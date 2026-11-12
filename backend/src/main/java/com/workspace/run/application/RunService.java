package com.workspace.run.application;

import com.workspace.agent.published.AgentParams;
import com.workspace.agent.published.AgentRuntime;
import com.workspace.audit.published.AuditLog;
import com.workspace.context.published.ContextAssembler;
import com.workspace.conversation.published.ConversationInfo;
import com.workspace.conversation.published.ConversationReader;
import com.workspace.conversation.published.ConversationWriter;
import com.workspace.conversation.published.MessageInfo;
import com.workspace.project.published.ProjectAccess;
import com.workspace.project.published.ProjectBudgets;
import com.workspace.project.published.ProjectRole;
import com.workspace.run.domain.AgentRun;
import com.workspace.run.domain.RunStatus;
import com.workspace.run.infrastructure.AgentRunRepository;
import com.workspace.shared.published.ApiException;
import com.workspace.shared.published.PageResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;

/** Send/cancel/retry/read runs. Members read; only the project OWNER sends, cancels and retries. */
@Service
public class RunService {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 200;
    private static final Set<ProjectRole> OWNER_ONLY = Set.of(ProjectRole.OWNER);
    private static final Set<RunStatus> ACTIVE = Set.of(RunStatus.QUEUED, RunStatus.RUNNING);
    private static final Set<RunStatus> RETRYABLE = Set.of(RunStatus.FAILED, RunStatus.BLOCKED, RunStatus.CANCELLED);

    public record RunView(RunData run, UUID messageId) {}

    public record SendResult(MessageInfo message, RunView run, boolean duplicate) {}

    public record Usage(LocalDate date, long inputTokens, long outputTokens, Long dailyTokenBudget) {}

    private final AgentRunRepository runs;
    private final RunStateService states;
    private final RunRegistry registry;
    private final RunEventBus bus;
    private final ConversationReader conversationReader;
    private final ConversationWriter conversationWriter;
    private final AgentRuntime agents;
    private final ContextAssembler assembler;
    private final ProjectAccess access;
    private final ProjectBudgets budgets;
    private final AuditLog audit;
    private final Clock clock;
    private final JsonMapper json;
    private final int maxMessageChars;

    public RunService(AgentRunRepository runs, RunStateService states, RunRegistry registry, RunEventBus bus,
                      ConversationReader conversationReader, ConversationWriter conversationWriter,
                      AgentRuntime agents, ContextAssembler assembler, ProjectAccess access, ProjectBudgets budgets,
                      AuditLog audit, Clock clock, JsonMapper json,
                      @Value("${workspace.run.max-message-chars:20000}") int maxMessageChars) {
        this.runs = runs;
        this.states = states;
        this.registry = registry;
        this.bus = bus;
        this.conversationReader = conversationReader;
        this.conversationWriter = conversationWriter;
        this.agents = agents;
        this.assembler = assembler;
        this.access = access;
        this.budgets = budgets;
        this.audit = audit;
        this.clock = clock;
        this.json = json;
        this.maxMessageChars = maxMessageChars;
    }

    @Transactional
    public SendResult send(UUID userId, UUID conversationId, String content, UUID clientRequestId) {
        ConversationInfo conv = conversationReader.find(conversationId).orElseThrow(ApiException::notFound);
        access.requireRole(conv.projectId(), userId, OWNER_ONLY);
        if (content == null || content.isBlank()) throw ApiException.validation("content must not be blank");
        if (content.length() > maxMessageChars) {
            throw ApiException.validation("content must be at most " + maxMessageChars + " characters");
        }
        if (clientRequestId == null) throw ApiException.validation("clientRequestId is required");

        ConversationWriter.Appended appended = conversationWriter.appendUserMessage(conversationId, content, clientRequestId);
        if (appended.duplicate()) {
            AgentRun existing = runs.findFirstByTriggerMessageIdOrderByCreatedAtDesc(appended.message().id()).orElse(null);
            return new SendResult(appended.message(), existing == null ? null : view(existing), true);
        }
        AgentRun run = enqueue(conv, userId, appended.message().id());
        audit.record(conv.projectId(), AuditLog.ACTOR_USER, userId.toString(), "MESSAGE_SENT", "CONVERSATION",
                conversationId, null, Map.of("messageId", appended.message().id().toString(), "runId", run.getId().toString()));
        return new SendResult(appended.message(), view(run), false);
    }

    @Transactional(readOnly = true)
    public RunView get(UUID userId, UUID runId) {
        return view(load(userId, runId));
    }

    @Transactional
    public RunView cancel(UUID userId, UUID runId) {
        AgentRun r = load(userId, runId);
        access.requireRole(r.getProjectId(), userId, OWNER_ONLY);
        if (r.getStatus() == RunStatus.QUEUED) {
            if (states.cancelled(runId)) bus.publishTerminal(runId, "run.failed", Map.of("code", "CANCELLED"));
        } else if (r.getStatus() == RunStatus.RUNNING) {
            registry.register(runId).set(true); // the worker finalises CANCELLED; the stream reports it
        }
        audit.record(r.getProjectId(), AuditLog.ACTOR_USER, userId.toString(), "RUN_CANCEL_REQUESTED", "AGENT_RUN",
                runId, null, null);
        return view(runs.findById(runId).orElseThrow(ApiException::notFound));
    }

    @Transactional
    public RunView retry(UUID userId, UUID runId) {
        AgentRun r = load(userId, runId);
        access.requireRole(r.getProjectId(), userId, OWNER_ONLY);
        if (!RETRYABLE.contains(r.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "RUN_NOT_RETRYABLE", "Only FAILED, BLOCKED or CANCELLED runs can be retried");
        }
        if (r.getConversationId() == null || r.getTriggerMessageId() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "RUN_NOT_RETRYABLE", "This run has no conversation to retry");
        }
        ConversationInfo conv = conversationReader.find(r.getConversationId()).orElseThrow(ApiException::notFound);
        AgentRun next = enqueue(conv, userId, r.getTriggerMessageId());
        audit.record(r.getProjectId(), AuditLog.ACTOR_USER, userId.toString(), "RUN_RETRIED", "AGENT_RUN", next.getId(),
                null, Map.of("retryOf", runId.toString()));
        return view(next);
    }

    @Transactional(readOnly = true)
    public PageResponse<RunView> list(UUID userId, UUID projectId, String status, String cursor, Integer limitParam) {
        access.requireMember(projectId, userId);
        int limit = limitParam == null ? DEFAULT_LIMIT : limitParam;
        if (limit < 1 || limit > MAX_LIMIT) throw ApiException.validation("limit must be between 1 and " + MAX_LIMIT);
        String st = null;
        if (status != null && !status.isBlank()) {
            try {
                st = RunStatus.valueOf(status).name();
            } catch (IllegalArgumentException e) {
                throw ApiException.validation("Unknown status");
            }
        }
        List<AgentRun> rows;
        if (cursor == null || cursor.isBlank()) {
            rows = runs.firstPage(projectId, st, limit + 1);
        } else {
            RunCursor c = RunCursor.decode(cursor);
            rows = runs.pageAfter(projectId, st, c.createdAt(), c.id(), limit + 1);
        }
        boolean more = rows.size() > limit;
        List<AgentRun> page = more ? rows.subList(0, limit) : rows;
        String next = more ? new RunCursor(page.get(page.size() - 1).getCreatedAt(), page.get(page.size() - 1).getId()).encode() : null;
        return new PageResponse<>(page.stream().map(this::view).toList(), next);
    }

    @Transactional(readOnly = true)
    public ContextAssembler.SnapshotView snapshot(UUID userId, UUID runId) {
        AgentRun r = load(userId, runId);
        if (r.getContextSnapshotId() == null) throw ApiException.notFound();
        return assembler.snapshot(r.getContextSnapshotId()).orElseThrow(ApiException::notFound);
    }

    @Transactional(readOnly = true)
    public Usage usage(UUID userId, UUID projectId, LocalDate date) {
        access.requireMember(projectId, userId);
        LocalDate day = date != null ? date : LocalDate.now(clock.withZone(ZoneOffset.UTC));
        Instant from = day.atStartOfDay(ZoneOffset.UTC).toInstant();
        RunStateService.Tokens t = states.usage(projectId, from, from.plus(1, ChronoUnit.DAYS), null);
        return new Usage(day, t.input(), t.output(), budgets.dailyTokenBudget(projectId).orElse(null));
    }

    /** Subscribe first, then re-check the stored state, so a run that finished in between is still reported. */
    public SseEmitter stream(UUID userId, UUID runId) {
        RunView v = get(userId, runId);
        SseEmitter emitter = bus.subscribe(runId);
        RunData now = states.data(runId).orElse(v.run());
        if (now.status().isTerminal()) {
            Terminal t = terminal(now);
            bus.sendTo(runId, emitter, t.event(), t.data());
            emitter.complete();
        }
        return emitter;
    }

    private record Terminal(String event, Map<String, Object> data) {}

    private Terminal terminal(RunData r) {
        return switch (r.status()) {
            case SUCCEEDED -> {
                MessageInfo m = conversationReader.findByAgentRun(r.id()).orElse(null);
                boolean stale = r.snapshotId() != null && !assembler
                        .staleDocumentTypes(r.projectId(), r.snapshotId(), r.roleConfigVersionId()).isEmpty();
                yield new Terminal("run.completed", RunExecutor.completedPayload(
                        new RunStateService.Completion(m == null ? null : m.id(), m == null ? 0 : m.seq(), stale),
                        r.inputTokens() == null ? 0 : r.inputTokens(), r.outputTokens() == null ? 0 : r.outputTokens()));
            }
            case BLOCKED -> new Terminal("run.blocked", Map.of("blockReason", r.blockReason().name()));
            case CANCELLED -> new Terminal("run.failed", Map.of("code", "CANCELLED"));
            default -> new Terminal("run.failed", Map.of("code", r.errorCode() == null ? "INTERNAL_ERROR" : r.errorCode()));
        };
    }

    private AgentRun load(UUID userId, UUID runId) {
        AgentRun r = runs.findById(runId).orElseThrow(ApiException::notFound);
        access.requireMember(r.getProjectId(), userId);
        return r;
    }

    private RunView view(AgentRun r) {
        UUID messageId = r.getStatus() == RunStatus.SUCCEEDED
                ? conversationReader.findByAgentRun(r.getId()).map(MessageInfo::id).orElse(null) : null;
        return new RunView(RunData.of(r), messageId);
    }

    /** Creates a QUEUED run with the values that must not change later (model, params, credential, role config). */
    private AgentRun enqueue(ConversationInfo conv, UUID userId, UUID triggerMessageId) {
        if (runs.findFirstByConversationIdAndStatusInOrderByCreatedAtDesc(conv.id(), ACTIVE).isPresent()) {
            throw activeRun();
        }
        AgentRuntime.Runtime rt = agents.load(conv.agentId()).orElseThrow(ApiException::notFound);
        AgentParams params = rt.params() == null ? new AgentParams(null, null, null) : rt.params();
        AgentRun run = AgentRun.queued(conv.projectId(), conv.agentId(), rt.config().roleConfigVersionId(),
                rt.agent().credentialId(), conv.id(), triggerMessageId, rt.agent().model(), json.writeValueAsString(params),
                clock.instant().truncatedTo(ChronoUnit.MICROS));
        try {
            return runs.saveAndFlush(run);
        } catch (DataIntegrityViolationException e) {
            throw activeRun(); // one_active_run_per_conversation lost a race
        }
    }

    private static ApiException activeRun() {
        return new ApiException(HttpStatus.CONFLICT, "ACTIVE_RUN_EXISTS", "This conversation already has an active run");
    }
}
