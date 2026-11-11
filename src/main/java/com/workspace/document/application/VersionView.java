package com.workspace.document.application;

import com.workspace.document.domain.AuthorType;
import com.workspace.document.domain.DocumentVersion;
import com.workspace.document.domain.VersionStatus;
import com.workspace.document.published.DocumentType;
import java.time.Instant;
import java.util.UUID;

/** content is null in list results (the body is only returned when a single version is requested). */
public record VersionView(UUID id, UUID documentId, int versionNo, VersionStatus status, String contentFormat,
                          String content, String contentHash, AuthorType authorType, String authorRef,
                          UUID parentVersionId, String changeSummary, UUID sourceMessageId,
                          Instant createdAt, Instant submittedAt) {

    static VersionView of(DocumentVersion v, DocumentType type, boolean withContent) {
        return new VersionView(v.getId(), v.getDocumentId(), v.getVersionNo(), v.getStatus(), type.contentFormat(),
                withContent ? v.getContent() : null, v.getContentHash(), v.getAuthorType(), v.getAuthorRef(),
                v.getParentVersionId(), v.getChangeSummary(), v.getSourceMessageId(), v.getCreatedAt(),
                v.getSubmittedAt());
    }
}
