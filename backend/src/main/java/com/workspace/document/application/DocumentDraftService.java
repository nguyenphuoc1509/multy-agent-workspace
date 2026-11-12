package com.workspace.document.application;

import com.workspace.audit.published.AuditLog;
import com.workspace.document.domain.AuthorType;
import com.workspace.document.domain.Document;
import com.workspace.document.domain.DocumentVersion;
import com.workspace.document.infrastructure.DocumentRepository;
import com.workspace.document.infrastructure.DocumentVersionRepository;
import com.workspace.document.domain.VersionStatus;
import com.workspace.document.published.DocumentDrafts;
import com.workspace.document.published.DocumentType;
import com.workspace.shared.published.ApiException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DocumentDraftService implements DocumentDrafts {

    private static final Set<VersionStatus> OPEN = EnumSet.of(VersionStatus.DRAFT, VersionStatus.IN_REVIEW);

    private final DocumentRepository documents;
    private final DocumentVersionRepository versions;
    private final AuditLog audit;
    private final Clock clock;

    DocumentDraftService(DocumentRepository documents, DocumentVersionRepository versions, AuditLog audit, Clock clock) {
        this.documents = documents;
        this.versions = versions;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DocumentRef> find(UUID documentId) {
        return documents.findById(documentId).map(d -> new DocumentRef(d.getId(), d.getProjectId(), d.getType()));
    }

    @Override
    @Transactional
    public Draft draftOnto(UUID documentId, String content, UUID agentId, UUID sourceMessageId, String changeSummary,
                           UUID actingUserId) {
        Document d = documents.findByIdForUpdate(documentId).orElseThrow(ApiException::notFound);
        versions.findFirstByDocumentIdAndStatusIn(documentId, OPEN).ifPresent(open -> {
            throw new ApiException(HttpStatus.CONFLICT, "OPEN_VERSION_EXISTS",
                    "This document already has an open version (" + open.getStatus() + ")",
                    Map.of("openVersionId", open.getId()));
        });
        UUID parent = d.getCurrentApprovedVersionId();
        DocumentVersion v = versions.save(DocumentVersion.draft(documentId, versions.maxVersionNo(documentId) + 1, content,
                AuthorType.AGENT, agentId.toString(), parent, changeSummary, sourceMessageId,
                clock.instant().truncatedTo(ChronoUnit.MICROS)));
        audit.record(d.getProjectId(), AuditLog.ACTOR_USER, actingUserId.toString(), "VERSION_CREATED", "DOCUMENT_VERSION",
                v.getId(), null, Map.of("versionNo", v.getVersionNo(), "status", v.getStatus().name(),
                        "contentHash", v.getContentHash(), "authorType", "AGENT"));
        return new Draft(documentId, v.getId(), v.getVersionNo(), d.getType(), v.getContentHash());
    }

    @Override
    @Transactional
    public Draft newDocument(UUID projectId, DocumentType type, String title, String content, UUID agentId,
                             UUID sourceMessageId, String changeSummary, UUID actingUserId) {
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Document d = documents.save(Document.create(projectId, type, title.trim(), now));
        DocumentVersion v = versions.save(DocumentVersion.draft(d.getId(), 1, content, AuthorType.AGENT, agentId.toString(),
                null, changeSummary, sourceMessageId, now));
        audit.record(projectId, AuditLog.ACTOR_USER, actingUserId.toString(), "DOCUMENT_CREATED", "DOCUMENT", d.getId(),
                null, Map.of("type", type.name(), "title", d.getTitle()));
        audit.record(projectId, AuditLog.ACTOR_USER, actingUserId.toString(), "VERSION_CREATED", "DOCUMENT_VERSION",
                v.getId(), null, Map.of("versionNo", 1, "status", v.getStatus().name(),
                        "contentHash", v.getContentHash(), "authorType", "AGENT"));
        return new Draft(d.getId(), v.getId(), 1, type, v.getContentHash());
    }
}
