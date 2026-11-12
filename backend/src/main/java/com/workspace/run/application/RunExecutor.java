package com.workspace.run.application;

import com.workspace.agent.published.AgentParams;
import com.workspace.agent.published.AgentRuntime;
import com.workspace.context.published.ContextAssembler;
import com.workspace.context.published.ContextAssembler.Assembled;
import com.workspace.credential.published.CredentialKeys;
import com.workspace.conversation.published.ConversationReader;
import com.workspace.conversation.published.MessageInfo;
import com.workspace.llm.published.ApiKey;
import com.workspace.llm.published.LlmAbortedException;
import com.workspace.llm.published.LlmException;
import com.workspace.llm.published.LlmProvider;
import com.workspace.llm.published.LlmProviders;
import com.workspace.llm.published.LlmTurn;
import com.workspace.llm.published.StreamRequest;
import com.workspace.llm.published.StreamResult;
import com.workspace.project.published.ProjectBudgets;
import com.workspace.run.domain.BlockReason;
import com.workspace.run.domain.RunStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Executes one claimed run: pre-flight, assemble (committed BEFORE the provider call), stream, complete.
 * The decrypted key lives only in local variables of {@link #run}; it is never logged or stored.
 */
@Component
class RunExecutor {

    private static final Logger log = LoggerFactory.getLogger(RunExecutor.class);

    private final RunStateService states;
    private final RunRegistry registry;
    private final RunEventBus bus;
    private final AgentRuntime agents;
    private final ContextAssembler assembler;
    private final CredentialKeys keys;
    private final ConversationReader conversations;
    private final LlmProviders providers;
    private final ProjectBudgets budgets;
    private final Clock clock;
    private final JsonMapper json;
    private final int maxRetries;
    private final long backoffMs;
    private final int defaultMaxOutputTokens;
    private final int defaultMaxRunSeconds;

    RunExecutor(RunStateService states, RunRegistry registry, RunEventBus bus, AgentRuntime agents,
                ContextAssembler assembler, CredentialKeys keys, ConversationReader conversations,
                LlmProviders providers, ProjectBudgets budgets, Clock clock, JsonMapper json,
                @Value("${workspace.run.max-retries:3}") int maxRetries,
                @Value("${workspace.run.retry-backoff-ms:1000}") long backoffMs,
                @Value("${workspace.run.default-max-output-tokens:4096}") int defaultMaxOutputTokens,
                @Value("${workspace.run.default-max-run-seconds:300}") int defaultMaxRunSeconds) {
        this.states = states;
        this.registry = registry;
        this.bus = bus;
        this.agents = agents;
        this.assembler = assembler;
        this.keys = keys;
        this.conversations = conversations;
        this.providers = providers;
        this.budgets = budgets;
        this.clock = clock;
        this.json = json;
        this.maxRetries = maxRetries;
        this.backoffMs = backoffMs;
        this.defaultMaxOutputTokens = defaultMaxOutputTokens;
        this.defaultMaxRunSeconds = defaultMaxRunSeconds;
    }

    void execute(UUID runId) {
        AtomicBoolean cancel = registry.register(runId);
        try {
            run(runId, cancel);
        } catch (Throwable t) {
            log.error("Run {} crashed: {}", runId, t.getClass().getName()); // class only: never message/stack of key-handling code
            fail(runId, "INTERNAL_ERROR");
        } finally {
            registry.unregister(runId);
        }
    }

    private void run(UUID runId, AtomicBoolean cancel) {
        RunData r = states.data(runId).orElse(null);
        if (r == null || r.status() != RunStatus.RUNNING) return;

        AgentRuntime.Runtime rt = agents.load(r.agentId()).orElse(null);
        if (rt == null || !rt.agent().enabled()) {
            block(runId, BlockReason.AGENT_DISABLED);
            return;
        }
        if (r.credentialId() == null) {
            block(runId, BlockReason.CREDENTIAL_REVOKED);
            return;
        }
        Optional<ApiKey> revealed = keys.reveal(r.credentialId());
        if (revealed.isEmpty()) {
            block(runId, BlockReason.CREDENTIAL_REVOKED);
            return;
        }
        ApiKey key = revealed.get();

        MessageInfo trigger = conversations.findMessage(r.triggerMessageId())
                .orElseThrow(() -> new IllegalStateException("TRIGGER_MESSAGE_MISSING"));
        Assembled first = assembler.assemble(r.agentId(), r.conversationId(), trigger.seq(), ContextAssembler.Mode.COMMIT);
        if (!first.blockers().isEmpty()) {
            block(runId, BlockReason.valueOf(first.blockers().get(0).reason()));
            return;
        }
        UUID roleConfigId = rt.config().roleConfigVersionId();
        states.attachSnapshot(runId, first.snapshotId(), roleConfigId);

        Optional<Long> budget = budgets.dailyTokenBudget(r.projectId());
        if (budget.isPresent()) {
            Instant from = LocalDate.now(clock.withZone(ZoneOffset.UTC)).atStartOfDay(ZoneOffset.UTC).toInstant();
            RunStateService.Tokens used = states.usage(r.projectId(), from, from.plus(1, ChronoUnit.DAYS), runId);
            if (used.input() + used.output() + first.tokenEstimate() > budget.get()) {
                block(runId, BlockReason.BUDGET_EXCEEDED);
                return;
            }
        }

        AgentParams params = json.readValue(r.paramsJson(), AgentParams.class);
        int maxOutput = params.maxOutputTokens() != null ? params.maxOutputTokens() : defaultMaxOutputTokens;
        int seconds = params.maxRunSeconds() != null ? params.maxRunSeconds() : defaultMaxRunSeconds;
        long deadline = System.nanoTime() + seconds * 1_000_000_000L;
        BooleanSupplier abort = () -> cancel.get() || System.nanoTime() > deadline;

        LlmProvider provider = providers.find(rt.agent().provider()).orElse(null);
        if (provider == null) {
            fail(runId, "PROVIDER_NOT_CONFIGURED");
            return;
        }

        bus.publish(runId, "run.started", Map.of("runId", runId.toString(), "snapshotId", first.snapshotId().toString(),
                "model", r.model()));

        StreamResult result;
        for (int attempt = 1; ; attempt++) {
            states.setAttempt(runId, attempt);
            Assembled prompt = first;
            if (attempt > 1) { // retries rebuild the identical prompt from the snapshot (brief section 9.9)
                try {
                    prompt = assembler.renderFromSnapshot(first.snapshotId(), r.conversationId(), roleConfigId,
                            params.maxContextTokens());
                } catch (IllegalStateException e) {
                    fail(runId, e.getMessage());
                    return;
                }
            }
            try {
                result = provider.stream(new StreamRequest(r.model(), prompt.system(),
                                prompt.turns().stream().map(t -> new LlmTurn(t.role(), t.content())).toList(), maxOutput),
                        key, text -> bus.publish(runId, "delta", Map.of("text", text)), abort);
                break;
            } catch (LlmAbortedException e) {
                aborted(runId, cancel);
                return;
            } catch (LlmException e) {
                if (e.kind() == LlmException.Kind.TRANSIENT && attempt <= maxRetries) {
                    if (!sleepUnlessAborted(backoffMs << (attempt - 1), abort)) {
                        aborted(runId, cancel);
                        return;
                    }
                    continue;
                }
                fail(runId, e.kind() == LlmException.Kind.TRANSIENT ? "PROVIDER_UNAVAILABLE" : e.code());
                return;
            }
        }

        RunStateService.Completion c = states.completed(runId, result.text(), result.usage().inputTokens(),
                result.usage().outputTokens());
        if (c != null) {
            bus.publishTerminal(runId, "run.completed", completedPayload(c, result.usage().inputTokens(),
                    result.usage().outputTokens()));
        }
    }

    static Map<String, Object> completedPayload(RunStateService.Completion c, int in, int out) {
        return Map.of("messageId", c.messageId().toString(), "seq", c.seq(),
                "usage", Map.of("inputTokens", in, "outputTokens", out), "stale", c.stale());
    }

    private void aborted(UUID runId, AtomicBoolean cancel) {
        if (cancel.get()) {
            if (states.cancelled(runId)) bus.publishTerminal(runId, "run.failed", Map.of("code", "CANCELLED"));
        } else {
            block(runId, BlockReason.RUN_LIMIT_EXCEEDED);
        }
    }

    private void block(UUID runId, BlockReason reason) {
        if (states.blocked(runId, reason)) {
            bus.publishTerminal(runId, "run.blocked", Map.of("blockReason", reason.name()));
        }
    }

    private void fail(UUID runId, String code) {
        if (states.failed(runId, code)) bus.publishTerminal(runId, "run.failed", Map.of("code", code));
    }

    private static boolean sleepUnlessAborted(long ms, BooleanSupplier abort) {
        long end = System.nanoTime() + ms * 1_000_000L;
        while (System.nanoTime() < end) {
            if (abort.getAsBoolean()) return false;
            try {
                Thread.sleep(Math.min(50, Math.max(1, ms)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return !abort.getAsBoolean();
    }
}
