package com.workspace.conversation.application;

import com.workspace.agent.published.AgentDirectory;
import com.workspace.audit.published.AuditLog;
import com.workspace.conversation.domain.Conversation;
import com.workspace.conversation.infrastructure.ConversationRepository;
import com.workspace.conversation.infrastructure.MessageRepository;
import com.workspace.conversation.published.ConversationDecorator;
import com.workspace.conversation.published.ConversationDecorator.Extras;
import com.workspace.conversation.published.ConversationInfo;
import com.workspace.conversation.published.MessageInfo;
import com.workspace.project.published.ProjectAccess;
import com.workspace.project.published.ProjectRole;
import com.workspace.shared.published.ApiException;
import com.workspace.shared.published.FieldIssue;
import com.workspace.shared.published.PageResponse;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Conversations: members read, OWNER creates (brief section 13). Messages are sent through the run module. */
@Service
public class ConversationService {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 200;

    public record ConversationView(ConversationInfo conversation, Extras extras) {}

    private static final Set<ProjectRole> OWNER_ONLY = Set.of(ProjectRole.OWNER);

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final AgentDirectory agents;
    private final ProjectAccess access;
    private final AuditLog audit;
    private final Clock clock;
    private final List<ConversationDecorator> decorators;

    public ConversationService(ConversationRepository conversations, MessageRepository messages,
                               AgentDirectory agents, ProjectAccess access, AuditLog audit, Clock clock,
                               List<ConversationDecorator> decorators) {
        this.conversations = conversations;
        this.messages = messages;
        this.agents = agents;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
        this.decorators = decorators;
    }

    @Transactional
    public ConversationView create(UUID userId, UUID agentId, String title) {
        AgentDirectory.Info agent = agents.find(agentId).orElseThrow(ApiException::notFound);
        access.requireRole(agent.projectId(), userId, OWNER_ONLY);
        String t = title == null || title.isBlank() ? "New conversation" : title.trim();
        Conversation c = conversations.saveAndFlush(Conversation.create(agent.projectId(), agentId, null, t,
                clock.instant().truncatedTo(ChronoUnit.MICROS)));
        audit.record(c.getProjectId(), AuditLog.ACTOR_USER, userId.toString(), "CONVERSATION_CREATED", "CONVERSATION",
                c.getId(), null, Map.of("agentId", agentId.toString(), "title", t));
        return new ConversationView(ConversationStore.info(c), Extras.none());
    }

    @Transactional(readOnly = true)
    public PageResponse<ConversationView> list(UUID userId, UUID projectId, UUID agentId, String cursor, Integer limitParam) {
        access.requireMember(projectId, userId);
        int limit = limitParam == null ? DEFAULT_LIMIT : limitParam;
        if (limit < 1 || limit > MAX_LIMIT) throw ApiException.validation("limit must be between 1 and " + MAX_LIMIT);
        List<Conversation> rows;
        if (cursor == null || cursor.isBlank()) {
            rows = conversations.firstPage(projectId, agentId, limit + 1);
        } else {
            ConversationCursor c = ConversationCursor.decode(cursor);
            rows = conversations.pageAfter(projectId, agentId, c.createdAt(), c.id(), limit + 1);
        }
        boolean more = rows.size() > limit;
        List<Conversation> page = more ? rows.subList(0, limit) : rows;
        Map<UUID, Extras> extras = extras(page.stream().map(Conversation::getId).toList());
        List<ConversationView> items = page.stream().map(c -> new ConversationView(ConversationStore.info(c),
                extras.getOrDefault(c.getId(), Extras.none()))).toList();
        String next = more ? new ConversationCursor(page.get(page.size() - 1).getCreatedAt(),
                page.get(page.size() - 1).getId()).encode() : null;
        return new PageResponse<>(items, next);
    }

    @Transactional(readOnly = true)
    public ConversationView get(UUID userId, UUID conversationId) {
        Conversation c = load(userId, conversationId);
        return new ConversationView(ConversationStore.info(c),
                extras(List.of(c.getId())).getOrDefault(c.getId(), Extras.none()));
    }

    @Transactional(readOnly = true)
    public PageResponse<MessageInfo> messages(UUID userId, UUID conversationId, Integer afterSeq, Integer limitParam) {
        load(userId, conversationId);
        int limit = limitParam == null ? DEFAULT_LIMIT : limitParam;
        if (limit < 1 || limit > MAX_LIMIT) throw ApiException.validation("limit must be between 1 and " + MAX_LIMIT);
        int after = afterSeq == null ? 0 : afterSeq;
        if (after < 0) {
            throw ApiException.validation("afterSeq must not be negative", List.of(new FieldIssue("afterSeq", "must be >= 0")));
        }
        List<MessageInfo> rows = messages.after(conversationId, after, PageRequest.of(0, limit + 1)).stream()
                .map(ConversationStore::info).toList();
        boolean more = rows.size() > limit;
        List<MessageInfo> page = more ? rows.subList(0, limit) : rows;
        return new PageResponse<>(page, more ? String.valueOf(page.get(page.size() - 1).seq()) : null);
    }

    /** Authorization for conversation-scoped reads: any project member; others see 404. */
    private Conversation load(UUID userId, UUID conversationId) {
        Conversation c = conversations.findById(conversationId).orElseThrow(ApiException::notFound);
        access.requireMember(c.getProjectId(), userId);
        return c;
    }

    private Map<UUID, Extras> extras(List<UUID> ids) {
        Map<UUID, Extras> merged = new java.util.HashMap<>();
        for (ConversationDecorator d : decorators) merged.putAll(d.extras(ids));
        return merged;
    }
}
