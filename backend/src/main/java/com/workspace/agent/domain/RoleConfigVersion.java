package com.workspace.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

/** Immutable (also enforced by a trigger). contextProfile is stored as jsonb; Java sees it as JSON text. */
@Entity
@Table(name = "role_config_version")
public class RoleConfigVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "role_id", nullable = false, updatable = false)
    private UUID roleId;

    @Column(name = "version_no", nullable = false, updatable = false)
    private int versionNo;

    @Column(name = "system_prompt", nullable = false, updatable = false)
    private String systemPrompt;

    @Column(name = "context_profile", nullable = false, updatable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String contextProfileJson;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RoleConfigVersion() {}

    public static RoleConfigVersion create(UUID roleId, int versionNo, String systemPrompt, String contextProfileJson,
                                           UUID createdBy, Instant now) {
        RoleConfigVersion v = new RoleConfigVersion();
        v.roleId = roleId;
        v.versionNo = versionNo;
        v.systemPrompt = systemPrompt;
        v.contextProfileJson = contextProfileJson;
        v.createdBy = createdBy;
        v.createdAt = now;
        return v;
    }

    public UUID getId() { return id; }
    public UUID getRoleId() { return roleId; }
    public int getVersionNo() { return versionNo; }
    public String getSystemPrompt() { return systemPrompt; }
    public String getContextProfileJson() { return contextProfileJson; }
    public Instant getCreatedAt() { return createdAt; }
}
