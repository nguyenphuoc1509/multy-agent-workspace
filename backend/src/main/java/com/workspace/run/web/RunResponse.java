package com.workspace.run.web;

import com.workspace.run.application.RunData;
import com.workspace.run.application.RunService.RunView;
import com.workspace.run.domain.BlockReason;
import com.workspace.run.domain.RunStatus;
import java.time.Instant;
import java.util.UUID;

public record RunResponse(UUID id, UUID agentId, UUID conversationId, UUID triggerMessageId, UUID snapshotId,
                          RunStatus status, BlockReason blockReason, String errorCode, int attempt, String model,
                          Integer inputTokens, Integer outputTokens, UUID messageId, Instant createdAt,
                          Instant startedAt, Instant finishedAt) {
    static RunResponse from(RunView v) {
        RunData r = v.run();
        return new RunResponse(r.id(), r.agentId(), r.conversationId(), r.triggerMessageId(), r.snapshotId(), r.status(),
                r.blockReason(), r.errorCode(), r.attempt(), r.model(), r.inputTokens(), r.outputTokens(), v.messageId(),
                r.createdAt(), r.startedAt(), r.finishedAt());
    }
}
