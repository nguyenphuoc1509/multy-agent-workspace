package com.workspace.document.published;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Read-only access to approved document content for context assembly. Never returns drafts as "approved". */
public interface ApprovedDocuments {

    record Version(UUID versionId, UUID documentId, DocumentType type, String title, int versionNo, String contentHash,
                   String content, Instant documentCreatedAt) {}

    /** The current approved version of every document of the given types, ordered by type then document creation. */
    List<Version> currentApproved(UUID projectId, Collection<DocumentType> types);

    /** Versions by id, any status (a snapshot may reference one that has since been superseded). */
    List<Version> versions(Collection<UUID> versionIds);
}
