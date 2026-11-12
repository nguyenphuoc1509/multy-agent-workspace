package com.workspace.run.application;

import com.workspace.context.published.ContextAssembler;
import com.workspace.conversation.published.ConversationDecorator;
import com.workspace.run.domain.AgentRun;
import com.workspace.run.domain.RunStatus;
import com.workspace.run.infrastructure.AgentRunRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Adds activeRunId and staleness (of the latest successful run) to conversation views. */
@Component
class RunConversationDecorator implements ConversationDecorator {

    private final AgentRunRepository runs;
    private final ContextAssembler assembler;

    RunConversationDecorator(AgentRunRepository runs, ContextAssembler assembler) {
        this.runs = runs;
        this.assembler = assembler;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Extras> extras(Collection<UUID> ids) {
        Map<UUID, Extras> out = new HashMap<>();
        if (ids.isEmpty()) return out;
        Map<UUID, UUID> active = new HashMap<>();
        for (AgentRun r : runs.findByConversationIdInAndStatusIn(ids, Set.of(RunStatus.QUEUED, RunStatus.RUNNING))) {
            active.put(r.getConversationId(), r.getId());
        }
        for (UUID id : ids) {
            List<String> stale = runs.findFirstByConversationIdAndStatusOrderByCreatedAtDesc(id, RunStatus.SUCCEEDED)
                    .filter(r -> r.getContextSnapshotId() != null)
                    .map(r -> assembler.staleDocumentTypes(r.getProjectId(), r.getContextSnapshotId(), r.getRoleConfigVersionId()))
                    .orElse(List.of());
            out.put(id, new Extras(active.get(id), !stale.isEmpty(), stale));
        }
        return out;
    }
}
