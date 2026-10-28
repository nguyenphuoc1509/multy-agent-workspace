package com.workspace.project.application;

import com.workspace.project.infrastructure.ProjectMemberRepository;
import com.workspace.project.domain.ProjectMemberKey;
import com.workspace.project.published.ProjectAccess;
import com.workspace.project.published.ProjectMembership;
import com.workspace.project.published.ProjectRole;
import com.workspace.shared.published.ApiException;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ProjectAccessService implements ProjectAccess {

    private final ProjectMemberRepository members;

    ProjectAccessService(ProjectMemberRepository members) {
        this.members = members;
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectMembership requireMember(UUID projectId, UUID userId) {
        return members.findById(new ProjectMemberKey(projectId, userId))
                .map(m -> new ProjectMembership(projectId, userId, m.getRole()))
                .orElseThrow(ApiException::notFound); // non-member and non-existent look identical
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectMembership requireRole(UUID projectId, UUID userId, Set<ProjectRole> allowed) {
        ProjectMembership m = requireMember(projectId, userId);
        if (!allowed.contains(m.role())) {
            throw ApiException.forbidden("Your role in this project does not allow this action");
        }
        return m;
    }
}
