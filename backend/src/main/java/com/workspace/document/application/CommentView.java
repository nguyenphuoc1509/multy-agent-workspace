package com.workspace.document.application;

import com.workspace.document.domain.AuthorType;
import com.workspace.document.domain.VersionComment;
import java.time.Instant;
import java.util.UUID;

public record CommentView(UUID id, UUID documentVersionId, AuthorType authorType, String authorRef, String body,
                          Instant createdAt) {
    static CommentView of(VersionComment c) {
        return new CommentView(c.getId(), c.getDocumentVersionId(), c.getAuthorType(), c.getAuthorRef(), c.getBody(),
                c.getCreatedAt());
    }
}
