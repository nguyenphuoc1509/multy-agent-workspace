package com.workspace.document.web;

import com.workspace.document.application.CommentView;
import com.workspace.document.domain.AuthorType;
import java.time.Instant;
import java.util.UUID;

public record CommentResponse(UUID id, UUID documentVersionId, AuthorType authorType, String authorRef, String body,
                              Instant createdAt) {
    static CommentResponse from(CommentView c) {
        return new CommentResponse(c.id(), c.documentVersionId(), c.authorType(), c.authorRef(), c.body(),
                c.createdAt());
    }
}
