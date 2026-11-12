package com.workspace.credential.application;

import com.workspace.credential.domain.LlmCredential;
import java.time.Instant;
import java.util.UUID;

/** Everything an API response may say about a credential. There is no key field, on purpose. */
public record CredentialView(UUID id, String label, String provider, String keyLast4, Instant createdAt,
                             Instant revokedAt) {
    static CredentialView of(LlmCredential c) {
        return new CredentialView(c.getId(), c.getLabel(), c.getProvider(), c.getKeyLast4(), c.getCreatedAt(),
                c.getRevokedAt());
    }
}
