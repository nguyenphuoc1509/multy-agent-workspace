package com.workspace.document.domain;

import com.workspace.shared.published.ApiException;
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
import org.springframework.http.HttpStatus;

/**
 * State machine: DRAFT -> IN_REVIEW -> APPROVED -> SUPERSEDED, DRAFT -> DISCARDED, IN_REVIEW -> REJECTED.
 * The same rules are enforced again by DB triggers (V2); this class gives clean 409 errors first.
 */
@Entity
@Table(name = "document_version")
public class DocumentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(name = "version_no", nullable = false, updatable = false)
    private int versionNo;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "content_hash", nullable = false)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private VersionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_type", nullable = false, updatable = false)
    private AuthorType authorType;

    @Column(name = "author_ref", nullable = false, updatable = false)
    private String authorRef;

    @Column(name = "parent_version_id", updatable = false)
    private UUID parentVersionId;

    @Column(name = "change_summary")
    private String changeSummary;

    @Column(name = "source_message_id", updatable = false)
    private UUID sourceMessageId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    protected DocumentVersion() {}

    public static DocumentVersion draft(UUID documentId, int versionNo, String content, AuthorType authorType,
                                        String authorRef, UUID parentVersionId, String changeSummary,
                                        UUID sourceMessageId, Instant now) {
        DocumentVersion v = new DocumentVersion();
        v.documentId = documentId;
        v.versionNo = versionNo;
        v.content = ContentHash.normalise(content);
        v.contentHash = ContentHash.of(v.content);
        v.status = VersionStatus.DRAFT;
        v.authorType = authorType;
        v.authorRef = authorRef;
        v.parentVersionId = parentVersionId;
        v.changeSummary = changeSummary;
        v.sourceMessageId = sourceMessageId;
        v.createdAt = now;
        return v;
    }

    /** Null arguments leave the field unchanged. The hash is recomputed on every content edit. */
    public void editDraft(String newContent, String newChangeSummary) {
        requireStatus(VersionStatus.DRAFT);
        if (newContent != null) {
            this.content = ContentHash.normalise(newContent);
            this.contentHash = ContentHash.of(this.content);
        }
        if (newChangeSummary != null) {
            this.changeSummary = newChangeSummary;
        }
    }

    public void submit(Instant now) {
        requireStatus(VersionStatus.DRAFT);
        this.status = VersionStatus.IN_REVIEW;
        this.submittedAt = now;
    }

    public void discard() {
        requireStatus(VersionStatus.DRAFT);
        this.status = VersionStatus.DISCARDED;
    }

    public void approve() {
        requireStatus(VersionStatus.IN_REVIEW);
        this.status = VersionStatus.APPROVED;
    }

    public void reject() {
        requireStatus(VersionStatus.IN_REVIEW);
        this.status = VersionStatus.REJECTED;
    }

    public void supersede() {
        requireStatus(VersionStatus.APPROVED);
        this.status = VersionStatus.SUPERSEDED;
    }

    private void requireStatus(VersionStatus expected) {
        if (status != expected) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE",
                    "Version is " + status + " but " + expected + " is required for this action");
        }
    }

    public UUID getId() { return id; }
    public UUID getDocumentId() { return documentId; }
    public int getVersionNo() { return versionNo; }
    public String getContent() { return content; }
    public String getContentHash() { return contentHash; }
    public VersionStatus getStatus() { return status; }
    public AuthorType getAuthorType() { return authorType; }
    public String getAuthorRef() { return authorRef; }
    public UUID getParentVersionId() { return parentVersionId; }
    public String getChangeSummary() { return changeSummary; }
    public UUID getSourceMessageId() { return sourceMessageId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSubmittedAt() { return submittedAt; }
}