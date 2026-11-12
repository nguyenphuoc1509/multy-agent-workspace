package com.workspace.credential.application;

import com.workspace.credential.domain.LlmCredential;
import com.workspace.credential.infrastructure.LlmCredentialRepository;
import com.workspace.credential.published.CredentialKeys;
import com.workspace.llm.published.ApiKey;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class CredentialKeyService implements CredentialKeys {

    private final LlmCredentialRepository credentials;
    private final SecretCipher cipher;

    CredentialKeyService(LlmCredentialRepository credentials, SecretCipher cipher) {
        this.credentials = credentials;
        this.cipher = cipher;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ApiKey> reveal(UUID credentialId) {
        return credentials.findById(credentialId).filter(c -> !c.isRevoked()).map(this::decrypt);
    }

    private ApiKey decrypt(LlmCredential c) {
        return new ApiKey(cipher.decrypt(c.getKeyVersion(), c.getEncryptedKey(),
                c.getProjectId().toString().getBytes(StandardCharsets.UTF_8)));
    }
}
