package com.workspace.credential.application;

import com.workspace.audit.published.AuditLog;
import com.workspace.credential.domain.LlmCredential;
import com.workspace.credential.infrastructure.LlmCredentialRepository;
import com.workspace.event.published.EventPublisher;
import com.workspace.llm.published.ApiKey;
import com.workspace.llm.published.KeyValidation;
import com.workspace.llm.published.LlmProvider;
import com.workspace.llm.published.LlmProviders;
import com.workspace.project.published.ProjectAccess;
import com.workspace.project.published.ProjectRole;
import com.workspace.shared.published.ApiException;
import com.workspace.shared.published.FieldIssue;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Credentials are OWNER-only (brief section 13). The raw key exists only inside add() and is never logged. */
@Service
public class CredentialService {

    private static final Set<ProjectRole> OWNER_ONLY = Set.of(ProjectRole.OWNER);

    private final LlmCredentialRepository credentials;
    private final ProjectAccess access;
    private final LlmProviders providers;
    private final SecretCipher cipher;
    private final AuditLog audit;
    private final EventPublisher events;
    private final Clock clock;
    private final TransactionTemplate tx;

    public CredentialService(LlmCredentialRepository credentials, ProjectAccess access, LlmProviders providers,
                             SecretCipher cipher, AuditLog audit, EventPublisher events, Clock clock,
                             PlatformTransactionManager txManager) {
        this.credentials = credentials;
        this.access = access;
        this.providers = providers;
        this.cipher = cipher;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
    }

    /**
     * Validates the key with the provider FIRST (no database transaction is held during the network call); on any
     * failure nothing is stored. Only then encrypts and stores, with the audit row, in one transaction.
     */
    public CredentialView add(UUID userId, UUID projectId, String label, String provider, String rawKey) {
        access.requireRole(projectId, userId, OWNER_ONLY);
        LlmProvider adapter = providers.find(provider).orElseThrow(() ->
                ApiException.validation("Unsupported provider", List.of(new FieldIssue("provider", "Unsupported provider"))));
        KeyValidation result = adapter.validate(new ApiKey(rawKey), null);
        if (result == KeyValidation.REJECTED) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PROVIDER_REJECTED_KEY", "The provider rejected this key");
        }
        if (result == KeyValidation.UNAVAILABLE) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PROVIDER_UNAVAILABLE",
                    "Could not reach the provider to check the key; nothing was stored. Try again later");
        }

        String fingerprint = fingerprint(rawKey);
        String last4 = rawKey.substring(rawKey.length() - 4);
        try {
            return tx.execute(status -> {
                if (credentials.existsByProjectIdAndKeyFingerprintAndRevokedAtIsNull(projectId, fingerprint)) {
                    throw duplicate();
                }
                SecretCipher.Sealed sealed = cipher.encrypt(rawKey, aad(projectId));
                LlmCredential c = credentials.saveAndFlush(LlmCredential.create(projectId, label.trim(), provider,
                        sealed.blob(), sealed.keyVersion(), last4, fingerprint, userId,
                        clock.instant().truncatedTo(ChronoUnit.MICROS)));
                audit.record(projectId, AuditLog.ACTOR_USER, userId.toString(), "CREDENTIAL_ADDED", "CREDENTIAL",
                        c.getId(), null, snapshot(c));
                return CredentialView.of(c);
            });
        } catch (DataIntegrityViolationException e) {
            throw duplicate(); // lost a race against the partial unique index
        }
    }

    @Transactional(readOnly = true)
    public List<CredentialView> list(UUID userId, UUID projectId) {
        access.requireRole(projectId, userId, OWNER_ONLY);
        return credentials.findByProjectIdOrderByCreatedAtDescIdDesc(projectId).stream().map(CredentialView::of).toList();
    }

    @Transactional
    public CredentialView revoke(UUID userId, UUID credentialId) {
        LlmCredential c = credentials.lockById(credentialId).orElseThrow(ApiException::notFound);
        access.requireRole(c.getProjectId(), userId, OWNER_ONLY);
        if (c.isRevoked()) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", "Credential is already revoked");
        }
        Map<String, Object> before = snapshot(c);
        c.revoke(clock.instant().truncatedTo(ChronoUnit.MICROS));
        credentials.flush();
        audit.record(c.getProjectId(), AuditLog.ACTOR_USER, userId.toString(), "CREDENTIAL_REVOKED", "CREDENTIAL",
                c.getId(), before, snapshot(c));
        events.publish(c.getProjectId(), "CREDENTIAL", c.getId(), "CredentialRevoked", AuditLog.ACTOR_USER,
                userId.toString(), Map.of("credentialId", c.getId().toString()));
        return CredentialView.of(c);
    }

    private static ApiException duplicate() {
        return new ApiException(HttpStatus.CONFLICT, "DUPLICATE_CREDENTIAL",
                "This key is already an active credential in the project");
    }

    private static byte[] aad(UUID projectId) {
        return projectId.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String fingerprint(String rawKey) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(rawKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Audit snapshot: never the key, only what the API already shows. */
    private static Map<String, Object> snapshot(LlmCredential c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", c.getLabel());
        m.put("provider", c.getProvider());
        m.put("keyLast4", c.getKeyLast4());
        m.put("revokedAt", c.getRevokedAt() == null ? null : c.getRevokedAt().toString());
        return m;
    }
}
