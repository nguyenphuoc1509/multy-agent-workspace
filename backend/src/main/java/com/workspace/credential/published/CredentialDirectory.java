package com.workspace.credential.published;

import java.util.Optional;
import java.util.UUID;

/** Read-only lookup for other modules. Never exposes the key. */
public interface CredentialDirectory {

    record Info(UUID id, UUID projectId, String provider, boolean revoked) {}

    Optional<Info> find(UUID credentialId);
}
