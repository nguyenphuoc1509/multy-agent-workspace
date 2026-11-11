package com.workspace.document.web;

import com.workspace.document.application.VersionView;
import com.workspace.document.domain.AuthorType;
import com.workspace.document.domain.VersionStatus;
import java.time.Instant;
import java.util.UUID;

/** List shape: same as VersionResponse without the body. */
public record VersionSummaryResponse(UUID id, UUID documentId, int versionNo, VersionStatus status,
                                     String contentFormat, String contentHash, AuthorType authorType,
                                     String authorRef, UUID parentVersionId, String changeSummary,
                                     UUID sourceMessageId, Instant createdAt, Instant submittedAt) {
    static VersionSummaryResponse from(VersionView v) {
        return new VersionSummaryResponse(v.id(), v.documentId(), v.versionNo(), v.status(), v.contentFormat(),
                v.contentHash(), v.authorType(), v.authorRef(), v.parentVersionId(), v.changeSummary(),
                v.sourceMessageId(), v.createdAt(), v.submittedAt());
    }
}
