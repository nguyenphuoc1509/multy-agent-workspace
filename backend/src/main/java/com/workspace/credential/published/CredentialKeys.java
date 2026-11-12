package com.workspace.credential.published;

import com.workspace.llm.published.ApiKey;
import java.util.Optional;
import java.util.UUID;

/**
 * Decrypts a stored key for ONE provider call. Returns empty when the credential is missing or revoked (a revoked
 * credential is never used and never silently replaced). The caller must not store the value or log it.
 */
public interface CredentialKeys {
    Optional<ApiKey> reveal(UUID credentialId);
}
