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

/** Append-only (DB trigger). approver_id is NOT NULL: an agent can never approve. */
@Entity
@Table(name = "approval")
public class Approval {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_version_id", nullable = false, updatable = false)
    private UUID documentVersionId;

    @Column(name = "gate_code", nullable = false, updatable = false)
    private String gateCode;

    @Column(name = "approver_id", nullable = false, updatable = false)
    private UUID approverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, updatable = false)
    private Decision decision;

    @Column(name = "content_hash", nullable = false, updatable = false)
    private String contentHash;

    @Column(name = "comment", updatable = false)
    private String comment;

    @Column(name = "decided_at", nullable = false, updatable = false)
    private Instant decidedAt;

    protected Approval() {}

    public static Approval of(UUID documentVersionId, String gateCode, UUID approverId, Decision decision,
                              String contentHash, String comment, Instant now) {
        Approval a = new Approval();
        a.documentVersionId = documentVersionId;
        a.gateCode = gateCode;
        a.approverId = approverId;
        a.decision = decision;
        a.contentHash = contentHash;
        a.comment = comment;
        a.decidedAt = now;
        return a;
    }

    public UUID getId() { return id; }
    public UUID getDocumentVersionId() { return documentVersionId; }
    public String getGateCode() { return gateCode; }
    public UUID getApproverId() { return approverId; }
    public Decision getDecision() { return decision; }
    public String getContentHash() { return contentHash; }
    public String getComment() { return comment; }
    public Instant getDecidedAt() { return decidedAt; }
}