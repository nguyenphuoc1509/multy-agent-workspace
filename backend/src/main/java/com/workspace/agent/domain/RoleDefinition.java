package com.workspace.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "role_definition")
public class RoleDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "is_builtin", nullable = false, updatable = false)
    private boolean builtin;

    @Column(name = "active_config_version_id")
    private UUID activeConfigVersionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RoleDefinition() {}

    public static RoleDefinition create(UUID projectId, String code, String name, boolean builtin, Instant now) {
        RoleDefinition r = new RoleDefinition();
        r.projectId = projectId;
        r.code = code;
        r.name = name;
        r.builtin = builtin;
        r.createdAt = now;
        return r;
    }

    public void activate(UUID configVersionId) { this.activeConfigVersionId = configVersionId; }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public boolean isBuiltin() { return builtin; }
    public UUID getActiveConfigVersionId() { return activeConfigVersionId; }
    public Instant getCreatedAt() { return createdAt; }
}
