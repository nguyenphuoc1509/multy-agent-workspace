package com.workspace.run.application;

import com.workspace.run.domain.AgentRun;
import com.workspace.run.domain.BlockReason;
import com.workspace.run.domain.RunStatus;
import java.time.Instant;
import java.util.UUID;

/** Detached copy of a run row, safe to hand to worker threads. */
public record RunData(UUID id, UUID projectId, UUID agentId, UUID roleConfigVersionId, UUID credentialId,
                      UUID conversationId, UUID taskId, UUID triggerMessageId, UUID snapshotId, String model,
                      String paramsJson, RunStatus status, BlockReason blockReason, String errorCode, int attempt,
                      Integer inputTokens, Integer outputTokens, Instant createdAt, Instant startedAt,
                      Instant finishedAt) {
    static RunData of(AgentRun r) {
        return new RunData(r.getId(), r.getProjectId(), r.getAgentId(), r.getRoleConfigVersionId(), r.getCredentialId(),
                r.getConversationId(), r.getTaskId(), r.getTriggerMessageId(), r.getContextSnapshotId(), r.getModel(),
                r.getParamsJson(), r.getStatus(), r.getBlockReason(), r.getErrorCode(), r.getAttempt(),
                r.getInputTokens(), r.getOutputTokens(), r.getCreatedAt(), r.getStartedAt(), r.getFinishedAt());
    }
}
