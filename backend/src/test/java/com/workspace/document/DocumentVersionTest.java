package com.workspace.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.workspace.document.domain.AuthorType;
import com.workspace.document.domain.ContentHash;
import com.workspace.document.domain.DocumentVersion;
import com.workspace.document.domain.VersionStatus;
import com.workspace.shared.published.ApiException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Pure unit tests of the state machine (no Spring, no DB). */
class DocumentVersionTest {

    private static DocumentVersion draft() {
        return DocumentVersion.draft(UUID.randomUUID(), 1, "line1\r\nline2", AuthorType.HUMAN, "u1",
                null, "first", null, Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static void assertInvalidState(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.code()).isEqualTo("INVALID_STATE");
                });
    }

    @Test
    void draftStoresNormalisedContentAndMatchingHash() {
        DocumentVersion v = draft();
        assertThat(v.getStatus()).isEqualTo(VersionStatus.DRAFT);
        assertThat(v.getContent()).isEqualTo("line1\nline2");
        assertThat(v.getContentHash()).isEqualTo(ContentHash.of("line1\nline2"));
    }

    @Test
    void editRecomputesHashAndKeepsOtherFieldsWhenNull() {
        DocumentVersion v = draft();
        String before = v.getContentHash();
        v.editDraft("changed", null);
        assertThat(v.getContentHash()).isNotEqualTo(before).isEqualTo(ContentHash.of("changed"));
        assertThat(v.getChangeSummary()).isEqualTo("first");
    }

    @Test
    void happyPathDraftToSuperseded() {
        DocumentVersion v = draft();
        v.submit(Instant.parse("2026-01-02T00:00:00Z"));
        assertThat(v.getStatus()).isEqualTo(VersionStatus.IN_REVIEW);
        assertThat(v.getSubmittedAt()).isNotNull();
        v.approve();
        v.supersede();
        assertThat(v.getStatus()).isEqualTo(VersionStatus.SUPERSEDED);
    }

    @Test
    void contentIsImmutableAfterSubmit() {
        DocumentVersion v = draft();
        v.submit(Instant.now());
        assertInvalidState(() -> v.editDraft("x", null));
    }

    @Test
    void illegalTransitionsAreRejected() {
        DocumentVersion a = draft();
        assertInvalidState(a::approve);           // DRAFT -> APPROVED
        assertInvalidState(a::reject);            // DRAFT -> REJECTED
        assertInvalidState(a::supersede);         // DRAFT -> SUPERSEDED

        DocumentVersion b = draft();
        b.submit(Instant.now());
        assertInvalidState(() -> b.submit(Instant.now()));
        assertInvalidState(b::discard);           // IN_REVIEW -> DISCARDED

        DocumentVersion c = draft();
        c.discard();
        assertInvalidState(() -> c.submit(Instant.now()));   // DISCARDED is terminal
    }
}