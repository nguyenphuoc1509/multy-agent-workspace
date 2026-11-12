package com.workspace.document.domain;

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

@Entity
@Table(name = "version_comment")
public class VersionComment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_version_id", nullable = false, updatable = false)
    private UUID documentVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_type", nullable = false, updatable = false)
    private AuthorType authorType;

    @Column(name = "author_ref", nullable = false, updatable = false)
    private String authorRef;

    @Column(name = "agent_run_id", updatable = false)
    private UUID agentRunId;

    @Column(name = "body", nullable = false, updatable = false)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected VersionComment() {}

    public static VersionComment byHuman(UUID documentVersionId, UUID userId, String body, Instant now) {
        VersionComment c = new VersionComment();
        c.documentVersionId = documentVersionId;
        c.authorType = AuthorType.HUMAN;
        c.authorRef = userId.toString();
        c.body = body;
        c.createdAt = now;
        return c;
    }

    public UUID getId() { return id; }
    public UUID getDocumentVersionId() { return documentVersionId; }
    public AuthorType getAuthorType() { return authorType; }
    public String getAuthorRef() { return authorRef; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
}