package com.workspace.project.published;

import java.util.Set;
import java.util.UUID;

/**
 * Membership checks for every project-scoped endpoint in later milestones.
 * Non-member -> 404 NOT_FOUND (never reveals that the project exists); member without the
 * required role -> 403 FORBIDDEN.
 */
public interface ProjectAccess {

    ProjectMembership requireMember(UUID projectId, UUID userId);

    ProjectMembership requireRole(UUID projectId, UUID userId, Set<ProjectRole> allowed);
}
