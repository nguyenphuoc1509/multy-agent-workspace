package com.workspace.context.published;

import com.workspace.document.published.DocumentType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Deterministic, provider-neutral prompt assembly (brief section 9). */
public interface ContextAssembler {

    enum Mode { PREVIEW, COMMIT }

    record Item(UUID documentVersionId, DocumentType documentType, int versionNo, String contentHash,
                String inclusionMode) {}

    /** role is "user" or "assistant". */
    record Turn(String role, String content) {}

    record Blocker(String reason, String documentType) {}

    /** snapshotId is null in PREVIEW mode. */
    record Assembled(UUID snapshotId, String assemblerVersion, String system, List<Turn> turns, int tokenEstimate,
                     Integer lastMessageSeq, List<Item> items, List<String> missingDocumentTypes,
                     List<Blocker> blockers) {}

    record SnapshotView(UUID id, String assemblerVersion, int tokenEstimate, Integer lastMessageSeq,
                        List<String> missingDocumentTypes, List<UUID> dependencyRunIds, List<Item> items) {}

    /** upToSeq = seq of the triggering message (history cut-off); null = everything so far. */
    Assembled assemble(UUID agentId, UUID conversationId, Integer upToSeq, Mode mode);

    /** Rebuilds the identical prompt of a committed snapshot; a content hash mismatch is a hard failure. */
    Assembled renderFromSnapshot(UUID snapshotId, UUID conversationId, UUID roleConfigVersionId, Integer maxContextTokens);

    /** Types (of the role profile) whose approved documents differ from what the snapshot used. */
    List<String> staleDocumentTypes(UUID projectId, UUID snapshotId, UUID roleConfigVersionId);

    Optional<SnapshotView> snapshot(UUID snapshotId);
}
