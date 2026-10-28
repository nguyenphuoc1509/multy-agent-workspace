package com.workspace.project.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ProjectMemberKey implements Serializable {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    protected ProjectMemberKey() {}

    public ProjectMemberKey(UUID projectId, UUID userId) {
        this.projectId = projectId;
        this.userId = userId;
    }

    public UUID getProjectId() { return projectId; }
    public UUID getUserId() { return userId; }

    @Override
    public boolean equals(Object o) {
        return o instanceof ProjectMemberKey k && Objects.equals(projectId, k.projectId) && Objects.equals(userId, k.userId);
    }

    @Override
    public int hashCode() { return Objects.hash(projectId, userId); }
}
