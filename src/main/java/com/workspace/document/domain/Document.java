package com.workspace.document.domain;

import com.workspace.document.published.DocumentType;
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
@Table(name = "document")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false)
    private DocumentType type;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "current_approved_version_id")
    private UUID currentApprovedVersionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Document() {}

    public static Document create(UUID projectId, DocumentType type, String title, Instant now) {
        Document d = new Document();
        d.projectId = projectId;
        d.type = type;
        d.title = title;
        d.createdAt = now;
        return d;
    }

    /** Only call after the version is APPROVED and flushed (DB trigger checks it). Never point to an old version. */
    public void pointTo(UUID approvedVersionId) {
        this.currentApprovedVersionId = approvedVersionId;
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public DocumentType getType() { return type; }
    public String getTitle() { return title; }
    public UUID getCurrentApprovedVersionId() { return currentApprovedVersionId; }
    public Instant getCreatedAt() { return createdAt; }
}