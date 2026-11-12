package com.workspace.conversation.domain;

import com.workspace.conversation.published.MessageRole;
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

/** Append-only (also enforced by a trigger). */
@Entity
@Table(name = "message")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    @Column(name = "seq", nullable = false, updatable = false)
    private int seq;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, updatable = false)
    private MessageRole role;

    @Column(name = "content", nullable = false, updatable = false)
    private String content;

    @Column(name = "agent_run_id", updatable = false)
    private UUID agentRunId;

    @Column(name = "client_request_id", updatable = false)
    private UUID clientRequestId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Message() {}

    public static Message create(UUID conversationId, int seq, MessageRole role, String content, UUID agentRunId,
                                 UUID clientRequestId, Instant now) {
        Message m = new Message();
        m.conversationId = conversationId;
        m.seq = seq;
        m.role = role;
        m.content = content;
        m.agentRunId = agentRunId;
        m.clientRequestId = clientRequestId;
        m.createdAt = now;
        return m;
    }

    public UUID getId() { return id; }
    public UUID getConversationId() { return conversationId; }
    public int getSeq() { return seq; }
    public MessageRole getRole() { return role; }
    public String getContent() { return content; }
    public UUID getAgentRunId() { return agentRunId; }
    public Instant getCreatedAt() { return createdAt; }
}
