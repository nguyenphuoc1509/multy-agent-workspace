package com.workspace.document.web;

import com.workspace.document.application.VersionView;
import com.workspace.document.domain.AuthorType;
import com.workspace.document.domain.VersionStatus;
import java.time.Instant;
import java.util.UUID;

/** Single-version shape: includes the body. */
public record VersionResponse(UUID id, UUID documentId, int versionNo, VersionStatus status, String contentFormat,
                              String content, String contentHash, AuthorType authorType, String authorRef,
                              UUID parentVersionId, String changeSummary, UUID sourceMessageId,
                              Instant createdAt, Instant submittedAt) {
    static VersionResponse from(VersionView v) {
        return new VersionResponse(v.id(), v.documentId(), v.versionNo(), v.status(), v.contentFormat(), v.content(),
                v.contentHash(), v.authorType(), v.authorRef(), v.parentVersionId(), v.changeSummary(),
                v.sourceMessageId(), v.createdAt(), v.submittedAt());
    }
}
