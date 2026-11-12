package com.workspace.document.published;

import java.util.Optional;
import java.util.UUID;

/**
 * Creates DRAFT versions authored by an agent (promote). The approved pointer is never touched (rule 7).
 * Authorisation is the caller's job; this port only enforces document invariants (one open version).
 */
public interface DocumentDrafts {

    record DocumentRef(UUID documentId, UUID projectId, DocumentType type) {}

    record Draft(UUID documentId, UUID versionId, int versionNo, DocumentType type, String contentHash) {}

    Optional<DocumentRef> find(UUID documentId);

    /** 409 OPEN_VERSION_EXISTS when the document already has a DRAFT or IN_REVIEW version. */
    Draft draftOnto(UUID documentId, String content, UUID agentId, UUID sourceMessageId, String changeSummary, UUID actingUserId);

    Draft newDocument(UUID projectId, DocumentType type, String title, String content, UUID agentId,
                      UUID sourceMessageId, String changeSummary, UUID actingUserId);
}
