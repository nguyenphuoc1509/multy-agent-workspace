package com.workspace.document.application;

import com.workspace.document.published.DocumentType;
import java.time.Instant;
import java.util.UUID;

public record DocumentView(UUID id, UUID projectId, DocumentType type, String title,
                           UUID currentApprovedVersionId, UUID openVersionId, Instant createdAt) {}
