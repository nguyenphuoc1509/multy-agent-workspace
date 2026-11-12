package com.workspace.document.web;

import com.workspace.document.application.DocumentView;
import com.workspace.document.published.DocumentType;
import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(UUID id, UUID projectId, DocumentType type, String title,
                               UUID currentApprovedVersionId, UUID openVersionId, Instant createdAt) {
    static DocumentResponse from(DocumentView v) {
        return new DocumentResponse(v.id(), v.projectId(), v.type(), v.title(), v.currentApprovedVersionId(),
                v.openVersionId(), v.createdAt());
    }
}
