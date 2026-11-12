package com.workspace.run.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

/** One agent execution. Values that may change later (model, params, credential) are snapshotted at creation. */
@Entity
@Table(name = "agent_run")
public class AgentRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "agent_id", nullable = false, updatable = false)
    private UUID agentId;

    @Column(name = "role_config_version_id", nullable = false)
    private UUID roleConfigVersionId;

    @Column(name = "credential_id", updatable = false)
    private UUID credentialId;

    @Column(name = "conversation_id", updatable = false)
    private UUID conversationId;

    @Column(name = "task_id", updatable = false)
    private UUID taskId;

    @Column(name = "trigger_message_id", updatable = false)
    private UUID triggerMessageId;

    @Column(name = "context_snapshot_id")
    private UUID contextSnapshotId;

    @Column(name = "model", nullable = false, updatable = false)
    private String model;

    @Column(name = "params", nullable = false, updatable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String paramsJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RunStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "block_reason")
    private BlockReason blockReason;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "attempt", nullable = false)
    private int attempt;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected AgentRun() {}

    public static AgentRun queued(UUID projectId, UUID agentId, UUID roleConfigVersionId, UUID credentialId,
                                  UUID conversationId, UUID triggerMessageId, String model, String paramsJson,
                                  Instant now) {
        AgentRun r = new AgentRun();
        r.projectId = projectId;
        r.agentId = agentId;
        r.roleConfigVersionId = roleConfigVersionId;
        r.credentialId = credentialId;
        r.conversationId = conversationId;
        r.triggerMessageId = triggerMessageId;
        r.model = model;
        r.paramsJson = paramsJson;
        r.status = RunStatus.QUEUED;
        r.createdAt = now;
        return r;
    }

    public void start(Instant now) {
        status = RunStatus.RUNNING;
        startedAt = now;
    }

    public void attachSnapshot(UUID snapshotId, UUID roleConfigVersionId) {
        this.contextSnapshotId = snapshotId;
        this.roleConfigVersionId = roleConfigVersionId;
    }

    public void setAttempt(int attempt) { this.attempt = attempt; }

    public void block(BlockReason reason, Instant now) {
        status = RunStatus.BLOCKED;
        blockReason = reason;
        finishedAt = now;
    }

    public void fail(String code, Instant now) {
        status = RunStatus.FAILED;
        errorCode = code;
        finishedAt = now;
    }

    public void cancel(Instant now) {
        status = RunStatus.CANCELLED;
        finishedAt = now;
    }

    public void succeed(int in, int out, Instant now) {
        status = RunStatus.SUCCEEDED;
        inputTokens = in;
        outputTokens = out;
        finishedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public UUID getAgentId() { return agentId; }
    public UUID getRoleConfigVersionId() { return roleConfigVersionId; }
    public UUID getCredentialId() { return credentialId; }
    public UUID getConversationId() { return conversationId; }
    public UUID getTaskId() { return taskId; }
    public UUID getTriggerMessageId() { return triggerMessageId; }
    public UUID getContextSnapshotId() { return contextSnapshotId; }
    public String getModel() { return model; }
    public String getParamsJson() { return paramsJson; }
    public RunStatus getStatus() { return status; }
    public BlockReason getBlockReason() { return blockReason; }
    public String getErrorCode() { return errorCode; }
    public int getAttempt() { return attempt; }
    public Integer getInputTokens() { return inputTokens; }
    public Integer getOutputTokens() { return outputTokens; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
}
