package com.workspace.conversation.application;

import com.workspace.conversation.domain.Conversation;
import com.workspace.conversation.domain.Message;
import com.workspace.conversation.infrastructure.ConversationRepository;
import com.workspace.conversation.infrastructure.MessageRepository;
import com.workspace.conversation.published.ConversationInfo;
import com.workspace.conversation.published.ConversationReader;
import com.workspace.conversation.published.ConversationWriter;
import com.workspace.conversation.published.MessageInfo;
import com.workspace.conversation.published.MessageRole;
import com.workspace.shared.published.ApiException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Implements the published reader/writer ports used by the run and workflow modules. */
@Component
class ConversationStore implements ConversationReader, ConversationWriter {

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final Clock clock;

    ConversationStore(ConversationRepository conversations, MessageRepository messages, Clock clock) {
        this.conversations = conversations;
        this.messages = messages;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ConversationInfo> find(UUID conversationId) {
        return conversations.findById(conversationId).map(ConversationStore::info);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MessageInfo> findMessage(UUID messageId) {
        return messages.findById(messageId).map(ConversationStore::info);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MessageInfo> findByAgentRun(UUID agentRunId) {
        return messages.findByAgentRunId(agentRunId).map(ConversationStore::info);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MessageInfo> recentMessages(UUID conversationId, int upToSeq, int count) {
        List<MessageInfo> latest = new java.util.ArrayList<>(messages
                .latestUpTo(conversationId, upToSeq, PageRequest.of(0, Math.max(1, count))).stream()
                .map(ConversationStore::info).toList());
        Collections.reverse(latest);
        return latest;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Appended appendUserMessage(UUID conversationId, String content, UUID clientRequestId) {
        Conversation c = conversations.lockById(conversationId).orElseThrow(ApiException::notFound);
        if (clientRequestId != null) {
            Optional<Message> existing = messages.findByConversationIdAndClientRequestId(conversationId, clientRequestId);
            if (existing.isPresent()) return new Appended(info(existing.get()), true);
        }
        if (c.getArchivedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", "The conversation is archived");
        }
        Message m = messages.saveAndFlush(Message.create(conversationId, c.nextSeq(), MessageRole.USER, content, null,
                clientRequestId, now()));
        conversations.flush();
        return new Appended(info(m), false);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public MessageInfo appendAgentMessage(UUID conversationId, String content, UUID agentRunId) {
        Conversation c = conversations.lockById(conversationId).orElseThrow(ApiException::notFound);
        Message m = messages.saveAndFlush(Message.create(conversationId, c.nextSeq(), MessageRole.AGENT, content,
                agentRunId, null, now()));
        conversations.flush();
        return info(m);
    }

    private java.time.Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    static ConversationInfo info(Conversation c) {
        return new ConversationInfo(c.getId(), c.getProjectId(), c.getAgentId(), c.getTaskId(), c.getTitle(),
                c.getLastSeq(), c.getArchivedAt(), c.getCreatedAt());
    }

    static MessageInfo info(Message m) {
        return new MessageInfo(m.getId(), m.getConversationId(), m.getSeq(), m.getRole(), m.getContent(),
                m.getAgentRunId(), m.getCreatedAt());
    }
}
