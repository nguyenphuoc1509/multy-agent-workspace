package com.workspace.project.published;

import java.util.UUID;

public record ProjectMembership(UUID projectId, UUID userId, ProjectRole role) {}
