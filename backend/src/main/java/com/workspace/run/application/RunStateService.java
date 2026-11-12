package com.workspace.run.application;

import com.workspace.context.published.ContextAssembler;
import com.workspace.conversation.published.ConversationWriter;
import com.workspace.conversation.published.MessageInfo;
import com.workspace.audit.published.AuditLog;
import com.workspace.event.published.EventPublisher;
import com.workspace.run.domain.AgentRun;
import com.workspace.run.domain.BlockReason;
import com.workspace.run.domain.RunStatus;
import com.workspace.run.infrastructure.AgentRunRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Every run state transition, each in one short transaction. Terminal states are final. */
@Service
class RunStateService {

    record Completion(UUID messageId, int seq, boolean stale) {}

    record Tokens(long input, long output) {}

    private final AgentRunRepository runs;
    private final EventPublisher events;
    private final ConversationWriter conversations;
    private final ContextAssembler assembler;
    private final Clock clock;

    RunStateService(AgentRunRepository runs, EventPublisher events, ConversationWriter conversations,
                    ContextAssembler assembler, Clock clock) {
        this.runs = runs;
        this.events = events;
        this.conversations = conversations;
        this.assembler = assembler;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    Optional<RunData> data(UUID runId) {
        return runs.findById(runId).map(RunData::of);
    }

    /** QUEUED -> RUNNING for up to n runs (rows locked by another worker are skipped). */
    @Transactional
    List<UUID> claim(int n) {
        List<AgentRun> claimed = runs.claimQueued(n);
        for (AgentRun r : claimed) r.start(now());
        runs.flush();
        return claimed.stream().map(AgentRun::getId).toList();
    }

    @Transactional
    void attachSnapshot(UUID runId, UUID snapshotId, UUID roleConfigVersionId) {
        runs.lockById(runId).ifPresent(r -> r.attachSnapshot(snapshotId, roleConfigVersionId));
        runs.flush();
    }

    @Transactional
    void setAttempt(UUID runId, int attempt) {
        runs.lockById(runId).ifPresent(r -> r.setAttempt(attempt));
        runs.flush();
    }

    @Transactional
    boolean blocked(UUID runId, BlockReason reason) {
        AgentRun r = runs.lockById(runId).orElse(null);
        if (r == null || r.getStatus().isTerminal()) return false;
        r.block(reason, now());
        runs.flush();
        events.publish(r.getProjectId(), "AGENT_RUN", r.getId(), "AgentRunBlocked", AuditLog.ACTOR_SYSTEM, "system",
                Map.of("runId", r.getId().toString(), "blockReason", reason.name()));
        return true;
    }

    @Transactional
    boolean failed(UUID runId, String code) {
        AgentRun r = runs.lockById(runId).orElse(null);
        if (r == null || r.getStatus().isTerminal()) return false;
        r.fail(code, now());
        runs.flush();
        return true;
    }

    @Transactional
    boolean cancelled(UUID runId) {
        AgentRun r = runs.lockById(runId).orElse(null);
        if (r == null || r.getStatus().isTerminal()) return false;
        r.cancel(now());
        runs.flush();
        return true;
    }

    /** One transaction: the AGENT message, the run result, the stale check and the outbox event (brief section 10). */
    @Transactional
    Completion completed(UUID runId, String text, int inputTokens, int outputTokens) {
        AgentRun r = runs.lockById(runId).orElse(null);
        if (r == null || r.getStatus() != RunStatus.RUNNING) return null;
        MessageInfo m = conversations.appendAgentMessage(r.getConversationId(), text, r.getId());
        boolean stale = r.getContextSnapshotId() != null && !assembler
                .staleDocumentTypes(r.getProjectId(), r.getContextSnapshotId(), r.getRoleConfigVersionId()).isEmpty();
        r.succeed(inputTokens, outputTokens, now());
        runs.flush();
        events.publish(r.getProjectId(), "AGENT_RUN", r.getId(), "AgentRunSucceeded", AuditLog.ACTOR_SYSTEM, "system",
                Map.of("runId", r.getId().toString(), "messageId", m.id().toString(), "stale", stale));
        return new Completion(m.id(), m.seq(), stale);
    }

    @Transactional
    int failInterrupted() {
        return runs.failInterrupted();
    }

    @Transactional(readOnly = true)
    Tokens usage(UUID projectId, Instant from, Instant to, UUID excludeRunId) {
        List<Object[]> rows = runs.usage(projectId, from, to, excludeRunId);
        Object[] row = rows.get(0);
        return new Tokens(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
