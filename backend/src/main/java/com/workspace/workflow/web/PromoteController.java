package com.workspace.workflow.web;

import com.workspace.document.published.DocumentDrafts;
import com.workspace.identity.published.CurrentUserProvider;
import com.workspace.shared.published.ApiException;
import com.workspace.workflow.application.PromoteService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PromoteController {

    public record PromoteResponse(UUID documentId, UUID versionId, int versionNo, String documentType, String contentHash,
                                  String status) {}

    private final PromoteService promote;
    private final CurrentUserProvider currentUser;

    public PromoteController(PromoteService promote, CurrentUserProvider currentUser) {
        this.promote = promote;
        this.currentUser = currentUser;
    }

    @PostMapping("/messages/{messageId}/promote")
    @ResponseStatus(HttpStatus.CREATED)
    public PromoteResponse promote(@PathVariable UUID messageId, @Valid @RequestBody PromoteRequest body) {
        PromoteRequest.Target t = body.target();
        if ((t.documentId() == null) == (t.newDocument() == null)) {
            throw ApiException.validation("target needs exactly one of documentId or newDocument");
        }
        DocumentDrafts.Draft d = promote.promote(currentUser.require().id(), messageId, t.documentId(),
                t.newDocument() == null ? null : t.newDocument().type(),
                t.newDocument() == null ? null : t.newDocument().title(), body.changeSummary());
        return new PromoteResponse(d.documentId(), d.versionId(), d.versionNo(), d.type().name(), d.contentHash(), "DRAFT");
    }
}
