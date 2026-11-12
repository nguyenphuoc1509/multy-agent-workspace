package com.workspace.document.web;

import com.workspace.document.application.DocumentService;
import com.workspace.document.published.DocumentType;
import com.workspace.identity.published.CurrentUserProvider;
import com.workspace.shared.published.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class DocumentController {

    private final DocumentService documents;
    private final CurrentUserProvider currentUser;

    public DocumentController(DocumentService documents, CurrentUserProvider currentUser) {
        this.documents = documents;
        this.currentUser = currentUser;
    }

    @PostMapping("/projects/{projectId}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse create(@PathVariable UUID projectId, @Valid @RequestBody CreateDocumentRequest body) {
        UUID userId = currentUser.require().id();
        return DocumentResponse.from(documents.createDocument(userId, projectId, body.type(), body.title(),
                body.content(), body.changeSummary()));
    }

    @GetMapping("/projects/{projectId}/documents")
    public PageResponse<DocumentResponse> list(@PathVariable UUID projectId,
                                               @RequestParam(required = false) DocumentType type,
                                               @RequestParam(required = false) String cursor,
                                               @RequestParam(required = false) Integer limit) {
        PageResponse<com.workspace.document.application.DocumentView> page =
                documents.listDocuments(currentUser.require().id(), projectId, type, cursor, limit);
        return new PageResponse<>(page.items().stream().map(DocumentResponse::from).toList(), page.nextCursor());
    }

    @GetMapping("/documents/{documentId}")
    public DocumentResponse get(@PathVariable UUID documentId) {
        return DocumentResponse.from(documents.getDocument(currentUser.require().id(), documentId));
    }

    @GetMapping("/documents/{documentId}/versions")
    public PageResponse<VersionSummaryResponse> versions(@PathVariable UUID documentId,
                                                         @RequestParam(required = false) String cursor,
                                                         @RequestParam(required = false) Integer limit) {
        PageResponse<com.workspace.document.application.VersionView> page =
                documents.listVersions(currentUser.require().id(), documentId, cursor, limit);
        return new PageResponse<>(page.items().stream().map(VersionSummaryResponse::from).toList(),
                page.nextCursor());
    }

    @PostMapping("/documents/{documentId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<VersionResponse> createVersion(@PathVariable UUID documentId,
                                                         @Valid @RequestBody CreateVersionRequest body) {
        VersionResponse v = VersionResponse.from(documents.createVersion(currentUser.require().id(), documentId,
                body.fromVersionId(), body.content(), body.changeSummary()));
        return ResponseEntity.status(HttpStatus.CREATED).eTag(v.contentHash()).body(v);
    }

    @GetMapping("/document-versions/{versionId}")
    public ResponseEntity<VersionResponse> getVersion(@PathVariable UUID versionId) {
        VersionResponse v = VersionResponse.from(documents.getVersion(currentUser.require().id(), versionId));
        return ResponseEntity.ok().eTag(v.contentHash()).body(v);
    }

    @PatchMapping("/document-versions/{versionId}")
    public ResponseEntity<VersionResponse> updateVersion(@PathVariable UUID versionId,
                                                         @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                                         @Valid @RequestBody UpdateVersionRequest body) {
        VersionResponse v = VersionResponse.from(documents.updateVersion(currentUser.require().id(), versionId,
                ifMatch, body.content(), body.changeSummary()));
        return ResponseEntity.ok().eTag(v.contentHash()).body(v);
    }

    @PostMapping("/document-versions/{versionId}/submit")
    public ResponseEntity<VersionResponse> submit(@PathVariable UUID versionId) {
        VersionResponse v = VersionResponse.from(documents.submitVersion(currentUser.require().id(), versionId));
        return ResponseEntity.ok().eTag(v.contentHash()).body(v);
    }

    @PostMapping("/document-versions/{versionId}/discard")
    public ResponseEntity<VersionResponse> discard(@PathVariable UUID versionId) {
        VersionResponse v = VersionResponse.from(documents.discardVersion(currentUser.require().id(), versionId));
        return ResponseEntity.ok().eTag(v.contentHash()).body(v);
    }

    @PostMapping("/document-versions/{versionId}/approve")
    public DecisionResponse approve(@PathVariable UUID versionId, @Valid @RequestBody ApproveVersionRequest body) {
        return DecisionResponse.from(documents.approveVersion(currentUser.require().id(), versionId,
                body.expectedHash(), body.comment()));
    }

    @PostMapping("/document-versions/{versionId}/reject")
    public DecisionResponse reject(@PathVariable UUID versionId, @Valid @RequestBody RejectVersionRequest body) {
        return DecisionResponse.from(documents.rejectVersion(currentUser.require().id(), versionId, body.comment()));
    }

    @GetMapping("/document-versions/{versionId}/comments")
    public PageResponse<CommentResponse> comments(@PathVariable UUID versionId,
                                                  @RequestParam(required = false) String cursor,
                                                  @RequestParam(required = false) Integer limit) {
        PageResponse<com.workspace.document.application.CommentView> page =
                documents.listComments(currentUser.require().id(), versionId, cursor, limit);
        return new PageResponse<>(page.items().stream().map(CommentResponse::from).toList(), page.nextCursor());
    }

    @PostMapping("/document-versions/{versionId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse addComment(@PathVariable UUID versionId, @Valid @RequestBody CreateCommentRequest body) {
        return CommentResponse.from(documents.addComment(currentUser.require().id(), versionId, body.body()));
    }
}
