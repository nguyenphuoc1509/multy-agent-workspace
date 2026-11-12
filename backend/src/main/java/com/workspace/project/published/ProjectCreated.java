package com.workspace.project.published;

import java.util.UUID;

/** In-process Spring event, published inside the transaction that creates the project (listeners join it). */
public record ProjectCreated(UUID projectId, UUID createdBy) {}
