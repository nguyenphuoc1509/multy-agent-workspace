package com.workspace.conversation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversation")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "agent_id", nullable = false, updatable = false)
    private UUID agentId;

    @Column(name = "task_id", updatable = false)
    private UUID taskId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "last_seq", nullable = false)
    private int lastSeq;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Conversation() {}

    public static Conversation create(UUID projectId, UUID agentId, UUID taskId, String title, Instant now) {
        Conversation c = new Conversation();
        c.projectId = projectId;
        c.agentId = agentId;
        c.taskId = taskId;
        c.title = title;
        c.createdAt = now;
        return c;
    }

    /** Caller must hold the row lock (ConversationRepository.lockById). */
    public int nextSeq() {
        return ++lastSeq;
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public UUID getAgentId() { return agentId; }
    public UUID getTaskId() { return taskId; }
    public String getTitle() { return title; }
    public int getLastSeq() { return lastSeq; }
    public Instant getArchivedAt() { return archivedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
