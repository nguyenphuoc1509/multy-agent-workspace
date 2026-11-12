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

@Entity
@Table(name = "agent")
public class Agent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "role_id", nullable = false, updatable = false)
    private UUID roleId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "provider", nullable = false, updatable = false)
    private String provider;

    @Column(name = "executor_type", nullable = false, updatable = false)
    private String executorType;

    @Column(name = "model", nullable = false)
    private String model;

    @Column(name = "params", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String paramsJson;

    @Column(name = "credential_id")
    private UUID credentialId;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Agent() {}

    public static Agent create(UUID projectId, UUID roleId, String name, String provider, String model,
                               String paramsJson, UUID credentialId, Instant now) {
        Agent a = new Agent();
        a.projectId = projectId;
        a.roleId = roleId;
        a.name = name;
        a.provider = provider;
        a.executorType = "CHAT";
        a.model = model;
        a.paramsJson = paramsJson;
        a.credentialId = credentialId;
        a.enabled = true;
        a.createdAt = now;
        return a;
    }

    public void rename(String name) { this.name = name; }
    public void changeModel(String model) { this.model = model; }
    public void changeParams(String paramsJson) { this.paramsJson = paramsJson; }
    public void useCredential(UUID credentialId) { this.credentialId = credentialId; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public UUID getRoleId() { return roleId; }
    public String getName() { return name; }
    public String getProvider() { return provider; }
    public String getExecutorType() { return executorType; }
    public String getModel() { return model; }
    public String getParamsJson() { return paramsJson; }
    public UUID getCredentialId() { return credentialId; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
}
