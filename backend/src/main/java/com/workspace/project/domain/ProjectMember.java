package com.workspace.project.domain;

import com.workspace.project.published.ProjectRole;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "project_member")
public class ProjectMember {

    @EmbeddedId
    private ProjectMemberKey id;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private ProjectRole role;

    protected ProjectMember() {}

    public ProjectMember(UUID projectId, UUID userId, ProjectRole role) {
        this.id = new ProjectMemberKey(projectId, userId);
        this.role = role;
    }

    public ProjectMemberKey getId() { return id; }
    public ProjectRole getRole() { return role; }
}
