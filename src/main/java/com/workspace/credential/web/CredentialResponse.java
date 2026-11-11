package com.workspace.credential.web;

import com.workspace.credential.application.CredentialView;
import java.time.Instant;
import java.util.UUID;

public record CredentialResponse(UUID id, String label, String provider, String keyLast4, Instant createdAt,
                                 Instant revokedAt) {
    static CredentialResponse from(CredentialView v) {
        return new CredentialResponse(v.id(), v.label(), v.provider(), v.keyLast4(), v.createdAt(), v.revokedAt());
    }
}
