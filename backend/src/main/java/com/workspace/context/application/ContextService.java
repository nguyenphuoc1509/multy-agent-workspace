package com.workspace.context.application;

import com.workspace.agent.published.AgentRuntime;
import com.workspace.agent.published.ContextDocument;
import com.workspace.agent.published.ContextProfile;
import com.workspace.context.application.PromptRenderer.Rendered;
import com.workspace.context.infrastructure.SnapshotStore;
import com.workspace.context.infrastructure.SnapshotStore.ItemRow;
import com.workspace.context.infrastructure.SnapshotStore.SnapshotRow;
import com.workspace.context.published.ContextAssembler;
import com.workspace.context.published.ConversationHistorySource;
import com.workspace.context.published.ConversationHistorySource.HistoryMessage;
import com.workspace.document.published.ApprovedDocuments;
import com.workspace.document.published.ApprovedDocuments.Version;
import com.workspace.document.published.DocumentType;
import com.workspace.shared.published.ApiException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
class ContextService implements ContextAssembler {

    private final AgentRuntime agents;
    private final ApprovedDocuments approved;
    private final ConversationHistorySource history;
    private final SnapshotStore snapshots;
    private final int defaultMaxTokens;

    ContextService(AgentRuntime agents, ApprovedDocuments approved, ConversationHistorySource history,
                   SnapshotStore snapshots, @Value("${workspace.context.default-max-tokens:100000}") int defaultMaxTokens) {
        this.agents = agents;
        this.approved = approved;
        this.history = history;
        this.snapshots = snapshots;
        this.defaultMaxTokens = defaultMaxTokens;
    }

    /** One REPEATABLE READ transaction so every approved pointer is read consistently. */
    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Assembled assemble(UUID agentId, UUID conversationId, Integer upToSeq, Mode mode) {
        AgentRuntime.Runtime rt = agents.load(agentId).orElseThrow(ApiException::notFound);
        ContextProfile profile = rt.config().profile();
        List<DocumentType> types = profile.documents().stream().map(ContextDocument::type).toList();
        List<Version> docs = ordered(approved.currentApproved(rt.agent().projectId(), types), types);
        Set<DocumentType> present = docs.stream().map(Version::type).collect(Collectors.toCollection(() -> EnumSet.noneOf(DocumentType.class)));
        List<String> missing = types.stream().filter(t -> !present.contains(t)).map(Enum::name).toList();

        List<HistoryMessage> hist = conversationId == null ? List.of()
                : history.recent(conversationId, upToSeq == null ? Integer.MAX_VALUE : upToSeq, profile.recentMessages());
        int max = rt.params().maxContextTokens() != null ? rt.params().maxContextTokens() : defaultMaxTokens;
        Rendered r = PromptRenderer.render(rt.config().systemPrompt(), docs, missing, hist, max);

        List<Blocker> blockers = new ArrayList<>();
        if (!rt.agent().enabled()) blockers.add(new Blocker("AGENT_DISABLED", null));
        if (r.tooLarge()) blockers.add(new Blocker("CONTEXT_TOO_LARGE", null));
        Integer lastSeq = hist.isEmpty() ? null : hist.get(hist.size() - 1).seq();
        List<Item> items = docs.stream().map(ContextService::item).toList(); // SUMMARY is treated as FULL (brief 9.6)

        UUID snapshotId = null;
        if (mode == Mode.COMMIT && blockers.isEmpty()) {
            snapshotId = snapshots.insert(PromptRenderer.ASSEMBLER_VERSION, r.tokenEstimate(), lastSeq, missing,
                    items.stream().map(i -> new ItemRow(i.documentVersionId(), i.contentHash(), i.inclusionMode())).toList());
        }
        return new Assembled(snapshotId, PromptRenderer.ASSEMBLER_VERSION, r.system(), r.turns(), r.tokenEstimate(),
                lastSeq, items, missing, blockers);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Assembled renderFromSnapshot(UUID snapshotId, UUID conversationId, UUID roleConfigVersionId,
                                        Integer maxContextTokens) {
        SnapshotRow s = snapshots.find(snapshotId).orElseThrow(() -> new IllegalStateException("SNAPSHOT_NOT_FOUND"));
        AgentRuntime.RoleConfig cfg = agents.configVersion(roleConfigVersionId)
                .orElseThrow(() -> new IllegalStateException("ROLE_CONFIG_NOT_FOUND"));
        List<DocumentType> types = cfg.profile().documents().stream().map(ContextDocument::type).toList();
        List<Version> loaded = approved.versions(s.items().stream().map(ItemRow::documentVersionId).toList());
        Map<UUID, String> hashes = new HashMap<>();
        for (ItemRow i : s.items()) hashes.put(i.documentVersionId(), i.contentHash());
        for (Version v : loaded) {
            if (!v.contentHash().equals(hashes.get(v.versionId()))) {
                throw new IllegalStateException("SNAPSHOT_HASH_MISMATCH");
            }
        }
        if (loaded.size() != s.items().size()) throw new IllegalStateException("SNAPSHOT_HASH_MISMATCH");
        List<Version> docs = ordered(loaded, types);
        List<HistoryMessage> hist = conversationId == null ? List.of() : history.recent(conversationId,
                s.lastMessageSeq() == null ? Integer.MAX_VALUE : s.lastMessageSeq(), cfg.profile().recentMessages());
        int max = maxContextTokens != null ? maxContextTokens : defaultMaxTokens;
        Rendered r = PromptRenderer.render(cfg.systemPrompt(), docs, s.missingDocumentTypes(), hist, max);
        List<Blocker> blockers = r.tooLarge() ? List.of(new Blocker("CONTEXT_TOO_LARGE", null)) : List.of();
        return new Assembled(snapshotId, s.assemblerVersion(), r.system(), r.turns(), r.tokenEstimate(),
                s.lastMessageSeq(), docs.stream().map(ContextService::item).toList(), s.missingDocumentTypes(), blockers);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<String> staleDocumentTypes(UUID projectId, UUID snapshotId, UUID roleConfigVersionId) {
        SnapshotRow s = snapshots.find(snapshotId).orElse(null);
        AgentRuntime.RoleConfig cfg = agents.configVersion(roleConfigVersionId).orElse(null);
        if (s == null || cfg == null) return List.of();
        List<DocumentType> types = cfg.profile().documents().stream().map(ContextDocument::type).distinct().toList();
        Map<DocumentType, Set<UUID>> used = new HashMap<>();
        for (Version v : approved.versions(s.items().stream().map(ItemRow::documentVersionId).toList())) {
            used.computeIfAbsent(v.type(), k -> new HashSet<>()).add(v.versionId());
        }
        Map<DocumentType, Set<UUID>> now = new HashMap<>();
        for (Version v : approved.currentApproved(projectId, types)) {
            now.computeIfAbsent(v.type(), k -> new HashSet<>()).add(v.versionId());
        }
        return types.stream().filter(t -> !used.getOrDefault(t, Set.of()).equals(now.getOrDefault(t, Set.of())))
                .map(Enum::name).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SnapshotView> snapshot(UUID snapshotId) {
        return snapshots.find(snapshotId).map(s -> {
            Map<UUID, Version> byId = approved.versions(s.items().stream().map(ItemRow::documentVersionId).toList())
                    .stream().collect(Collectors.toMap(Version::versionId, v -> v));
            List<Item> items = s.items().stream().map(i -> {
                Version v = byId.get(i.documentVersionId());
                return new Item(i.documentVersionId(), v.type(), v.versionNo(), i.contentHash(), i.inclusionMode());
            }).sorted(Comparator.comparing((Item i) -> i.documentType().ordinal()).thenComparing(Item::versionNo)).toList();
            return new SnapshotView(s.id(), s.assemblerVersion(), s.tokenEstimate(), s.lastMessageSeq(),
                    s.missingDocumentTypes(), s.dependencyRunIds(), items);
        });
    }

    private static List<Version> ordered(List<Version> docs, List<DocumentType> profileOrder) {
        return docs.stream().sorted(Comparator.comparingInt((Version v) -> profileOrder.indexOf(v.type()))
                .thenComparing(Version::documentCreatedAt).thenComparing(Version::documentId)).toList();
    }

    private static Item item(Version v) {
        return new Item(v.versionId(), v.type(), v.versionNo(), v.contentHash(), "FULL");
    }
}
