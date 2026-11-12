package com.workspace.credential.application;

import com.workspace.credential.infrastructure.LlmCredentialRepository;
import com.workspace.credential.published.CredentialDirectory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class CredentialDirectoryService implements CredentialDirectory {

    private final LlmCredentialRepository credentials;

    CredentialDirectoryService(LlmCredentialRepository credentials) {
        this.credentials = credentials;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Info> find(UUID credentialId) {
        return credentials.findById(credentialId)
                .map(c -> new Info(c.getId(), c.getProjectId(), c.getProvider(), c.isRevoked()));
    }
}
