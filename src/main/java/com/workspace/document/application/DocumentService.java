package com.workspace.document.application;

import com.workspace.audit.published.AuditLog;
import com.workspace.document.domain.AuthorType;
import com.workspace.document.domain.Document;
import com.workspace.document.domain.DocumentVersion;
import com.workspace.document.domain.VersionStatus;
import com.workspace.document.infrastructure.DocumentRepository;
import com.workspace.document.infrastructure.DocumentVersionRepository;
import com.workspace.document.published.DocumentType;
import com.workspace.project.published.ProjectAccess;
import com.workspace.project.published.ProjectRole;
import com.workspace.shared.published.ApiException;
import com.workspace.shared.published.PageResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Documents and versions (brief section 6), M1 step 1a: create, read, list, new draft, edit draft.
 * Submit / discard / approve / reject arrive in later steps. Only OWNER writes (agents have no write path).
 * Every write takes SELECT ... FOR UPDATE on the document row, after the authorisation check.
 */
@Service
public class DocumentService {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 200;

    private static final Set<ProjectRole> WRITERS = Set.of(ProjectRole.OWNER);
    private static final Set<VersionStatus> OPEN = EnumSet.of(VersionStatus.DRAFT, VersionStatus.IN_REVIEW);

    private final DocumentRepository documents;
    private final DocumentVersionRepository versions;
    private final ProjectAccess access;
    private final AuditLog audit;
    private final Clock clock;

    public DocumentService(DocumentRepository documents, DocumentVersionRepository versions, ProjectAccess access,
                           AuditLog audit, Clock clock) {
        this.documents = documents;
        this.versions = versions;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public DocumentView createDocument(UUID userId, UUID projectId, DocumentType type, String title,
                                       String content, String changeSummary) {
        access.requireRole(projectId, userId, WRITERS);
        Instant now = now();
        Document d = documents.save(Document.create(projectId, type, title.trim(), now));
        DocumentVersion v = versions.save(DocumentVersion.draft(d.getId(), 1, content, AuthorType.HUMAN,
                userId.toString(), null, changeSummary, null, now));
        audit.record(projectId, AuditLog.ACTOR_USER, userId.toString(), "DOCUMENT_CREATED", "DOCUMENT", d.getId(),
                null, map("type", type.name(), "title", d.getTitle()));
        audit.record(projectId, AuditLog.ACTOR_USER, userId.toString(), "VERSION_CREATED", "DOCUMENT_VERSION",
                v.getId(), null, versionAudit(v));
        return documentView(d, v.getId());
    }

    @Transactional(readOnly = true)
    public PageResponse<DocumentView> listDocuments(UUID userId, UUID projectId, DocumentType type, String cursor,
                                                    Integer limitParam) {
        access.requireMember(projectId, userId);
        int limit = limit(limitParam);
        String typeName = type == null ? null : type.name();
        List<Document> rows;
        if (cursor == null || cursor.isBlank()) {
            rows = documents.firstPage(projectId, typeName, limit + 1);
        } else {
            DocumentCursor c = DocumentCursor.decode(cursor);
            rows = documents.pageAfter(projectId, typeName, c.createdAt(), c.id(), limit + 1);
        }
        boolean hasMore = rows.size() > limit;
        List<Document> page = hasMore ? rows.subList(0, limit) : rows;

        Map<UUID, UUID> openByDocument = new HashMap<>();
        if (!page.isEmpty()) {
            versions.findByDocumentIdInAndStatusIn(page.stream().map(Document::getId).toList(), OPEN)
                    .forEach(v -> openByDocument.put(v.getDocumentId(), v.getId()));
        }
        List<DocumentView> items = page.stream().map(d -> documentView(d, openByDocument.get(d.getId()))).toList();
        String next = hasMore
                ? new DocumentCursor(page.get(page.size() - 1).getCreatedAt(), page.get(page.size() - 1).getId()).encode()
                : null;
        return new PageResponse<>(items, next);
    }

    @Transactional(readOnly = true)
    public DocumentView getDocument(UUID userId, UUID documentId) {
        Document d = documents.findById(documentId).orElseThrow(ApiException::notFound);
        access.requireMember(d.getProjectId(), userId);
        return documentView(d, openVersionId(documentId));
    }

    /** Newest first. Cursor = version number of the last item returned. Bodies are omitted. */
    @Transactional(readOnly = true)
    public PageResponse<VersionView> listVersions(UUID userId, UUID documentId, String cursor, Integer limitParam) {
        Document d = documents.findById(documentId).orElseThrow(ApiException::notFound);
        access.requireMember(d.getProjectId(), userId);
        int limit = limit(limitParam);
        PageRequest fetch = PageRequest.of(0, limit + 1);
        List<DocumentVersion> rows;
        if (cursor == null || cursor.isBlank()) {
            rows = versions.findByDocumentIdOrderByVersionNoDesc(documentId, fetch);
        } else {
            int before;
            try {
                before = Integer.parseInt(cursor);
            } catch (NumberFormatException e) {
                throw ApiException.validation("Invalid cursor");
            }
            rows = versions.findByDocumentIdAndVersionNoLessThanOrderByVersionNoDesc(documentId, before, fetch);
        }
        boolean hasMore = rows.size() > limit;
        List<DocumentVersion> page = hasMore ? rows.subList(0, limit) : rows;
        List<VersionView> items = page.stream().map(v -> VersionView.of(v, d.getType(), false)).toList();
        String next = hasMore ? String.valueOf(page.get(page.size() - 1).getVersionNo()) : null;
        return new PageResponse<>(items, next);
    }

    /**
     * New draft. Parent = fromVersionId (rollback / re-edit of a rejected version) or the current approved
     * version. Content defaults to a copy of the parent's content, or "" when there is no parent.
     */
    @Transactional
    public VersionView createVersion(UUID userId, UUID documentId, UUID fromVersionId, String content,
                                     String changeSummary) {
        UUID projectId = documents.findProjectId(documentId).orElseThrow(ApiException::notFound);
        access.requireRole(projectId, userId, WRITERS);
        Document d = documents.findByIdForUpdate(documentId).orElseThrow(ApiException::notFound);

        versions.findFirstByDocumentIdAndStatusIn(documentId, OPEN).ifPresent(open -> {
            throw new ApiException(HttpStatus.CONFLICT, "OPEN_VERSION_EXISTS",
                    "This document already has an open version (" + open.getStatus() + ")",
                    Map.of("openVersionId", open.getId()));
        });

        UUID parentId = d.getCurrentApprovedVersionId();
        String base = null;
        if (fromVersionId != null) {
            DocumentVersion from = versions.findByIdAndDocumentId(fromVersionId, documentId)
                    .orElseThrow(ApiException::notFound);
            parentId = from.getId();
            base = from.getContent();
        } else if (parentId != null) {
            base = versions.findById(parentId).orElseThrow(ApiException::notFound).getContent();
        }
        String body = content != null ? content : (base != null ? base : "");

        int versionNo = versions.maxVersionNo(documentId) + 1;
        DocumentVersion v = versions.save(DocumentVersion.draft(documentId, versionNo, body, AuthorType.HUMAN,
                userId.toString(), parentId, changeSummary, null, now()));
        audit.record(projectId, AuditLog.ACTOR_USER, userId.toString(), "VERSION_CREATED", "DOCUMENT_VERSION",
                v.getId(), null, versionAudit(v));
        return VersionView.of(v, d.getType(), true);
    }

    @Transactional(readOnly = true)
    public VersionView getVersion(UUID userId, UUID versionId) {
        DocumentVersion v = versions.findById(versionId).orElseThrow(ApiException::notFound);
        Document d = documents.findById(v.getDocumentId()).orElseThrow(ApiException::notFound);
        access.requireMember(d.getProjectId(), userId);
        return VersionView.of(v, d.getType(), true);
    }

    /**
     * Edit a DRAFT. If-Match must carry the content hash the caller last saw:
     * missing -> 428, stale -> 412 (with currentHash), not a DRAFT -> 409. Null arguments leave a field unchanged.
     * The audit row stores hashes only, never the text.
     */
    @Transactional
    public VersionView updateVersion(UUID userId, UUID versionId, String ifMatch, String content,
                                     String changeSummary) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new ApiException(HttpStatus.PRECONDITION_REQUIRED, "PRECONDITION_REQUIRED",
                    "If-Match header with the current content hash is required");
        }
        if (content == null && changeSummary == null) {
            throw ApiException.validation("Nothing to update");
        }
        UUID documentId = versions.findDocumentId(versionId).orElseThrow(ApiException::notFound);
        UUID projectId = documents.findProjectId(documentId).orElseThrow(ApiException::notFound);
        access.requireRole(projectId, userId, WRITERS);
        Document d = documents.findByIdForUpdate(documentId).orElseThrow(ApiException::notFound);
        DocumentVersion v = versions.findById(versionId).orElseThrow(ApiException::notFound);

        v.ensureDraft();
        if (!matches(ifMatch, v.getContentHash())) {
            throw new ApiException(HttpStatus.PRECONDITION_FAILED, "PRECONDITION_FAILED",
                    "The version changed since you loaded it", Map.of("currentHash", v.getContentHash()));
        }
        String hashBefore = v.getContentHash();
        v.editDraft(content, changeSummary);
        audit.record(projectId, AuditLog.ACTOR_USER, userId.toString(), "VERSION_UPDATED", "DOCUMENT_VERSION",
                v.getId(), map("contentHash", hashBefore), map("contentHash", v.getContentHash()));
        return VersionView.of(v, d.getType(), true);
    }

    /**
     * DRAFT -> IN_REVIEW. Not a DRAFT -> 409. Content must be non-empty (422 INVALID_CONTENT).
     * TODO(1d): API_CONTRACT must also parse as a valid OpenAPI document (422 INVALID_CONTRACT).
     */
    @Transactional
    public VersionView submitVersion(UUID userId, UUID versionId) {
        Loaded l = loadForWrite(userId, versionId);
        DocumentVersion v = l.version();
        v.ensureDraft();
        if (v.getContent().isBlank()) {
            throw new ApiException(HttpStatus.valueOf(422), "INVALID_CONTENT",
                    "A version with empty content cannot be submitted");
        }
        v.submit(now());
        audit.record(l.projectId(), AuditLog.ACTOR_USER, userId.toString(), "VERSION_SUBMITTED", "DOCUMENT_VERSION",
                v.getId(), map("status", "DRAFT"), map("status", "IN_REVIEW", "contentHash", v.getContentHash()));
        return VersionView.of(v, l.document().getType(), true);
    }

    /** DRAFT -> DISCARDED (frees the "one open version" slot). Not a DRAFT -> 409. */
    @Transactional
    public VersionView discardVersion(UUID userId, UUID versionId) {
        Loaded l = loadForWrite(userId, versionId);
        DocumentVersion v = l.version();
        v.discard();
        audit.record(l.projectId(), AuditLog.ACTOR_USER, userId.toString(), "VERSION_DISCARDED", "DOCUMENT_VERSION",
                v.getId(), map("status", "DRAFT"), map("status", "DISCARDED"));
        return VersionView.of(v, l.document().getType(), true);
    }

    private record Loaded(UUID projectId, Document document, DocumentVersion version) {}

    /** Authorise (OWNER), then lock the document row, then read the version fresh. */
    private Loaded loadForWrite(UUID userId, UUID versionId) {
        UUID documentId = versions.findDocumentId(versionId).orElseThrow(ApiException::notFound);
        UUID projectId = documents.findProjectId(documentId).orElseThrow(ApiException::notFound);
        access.requireRole(projectId, userId, WRITERS);
        Document d = documents.findByIdForUpdate(documentId).orElseThrow(ApiException::notFound);
        DocumentVersion v = versions.findById(versionId).orElseThrow(ApiException::notFound);
        return new Loaded(projectId, d, v);
    }

    /** If-Match may hold one or more entity tags, quoted or weak ("W/"); "*" is deliberately not accepted. */
    static boolean matches(String ifMatch, String currentHash) {
        for (String raw : ifMatch.split(",")) {
            String tag = raw.trim();
            if (tag.startsWith("W/")) tag = tag.substring(2);
            if (tag.length() >= 2 && tag.startsWith("\"") && tag.endsWith("\"")) {
                tag = tag.substring(1, tag.length() - 1);
            }
            if (tag.equals(currentHash)) return true;
        }
        return false;
    }

    private UUID openVersionId(UUID documentId) {
        return versions.findFirstByDocumentIdAndStatusIn(documentId, OPEN).map(DocumentVersion::getId).orElse(null);
    }

    private static DocumentView documentView(Document d, UUID openVersionId) {
        return new DocumentView(d.getId(), d.getProjectId(), d.getType(), d.getTitle(),
                d.getCurrentApprovedVersionId(), openVersionId, d.getCreatedAt());
    }

    private static Map<String, Object> versionAudit(DocumentVersion v) {
        return map("versionNo", v.getVersionNo(), "status", v.getStatus().name(), "contentHash", v.getContentHash(),
                "parentVersionId", v.getParentVersionId());
    }

    private static Map<String, Object> map(Object... keyValues) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) m.put((String) keyValues[i], keyValues[i + 1]);
        return m;
    }

    private static int limit(Integer limitParam) {
        int limit = limitParam == null ? DEFAULT_LIMIT : limitParam;
        if (limit < 1 || limit > MAX_LIMIT) {
            throw ApiException.validation("limit must be between 1 and " + MAX_LIMIT);
        }
        return limit;
    }

    private Instant now() {
        // truncate to micros: PostgreSQL timestamptz precision, keeps keyset cursors exact
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
