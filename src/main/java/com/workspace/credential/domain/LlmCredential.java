package com.workspace.credential.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Holds only the ENCRYPTED key. There is deliberately no getter for the plaintext and no way to read it here. */
@Entity
@Table(name = "llm_credential")
public class LlmCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "provider", nullable = false, updatable = false)
    private String provider;

    @Column(name = "encrypted_key", nullable = false, updatable = false)
    private byte[] encryptedKey;

    @Column(name = "key_version", nullable = false, updatable = false)
    private int keyVersion;

    @Column(name = "key_last4", nullable = false, updatable = false)
    private String keyLast4;

    @Column(name = "key_fingerprint", nullable = false, updatable = false)
    private String keyFingerprint;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected LlmCredential() {}

    public static LlmCredential create(UUID projectId, String label, String provider, byte[] encryptedKey,
                                       int keyVersion, String keyLast4, String keyFingerprint, UUID createdBy,
                                       Instant now) {
        LlmCredential c = new LlmCredential();
        c.projectId = projectId;
        c.label = label;
        c.provider = provider;
        c.encryptedKey = encryptedKey;
        c.keyVersion = keyVersion;
        c.keyLast4 = keyLast4;
        c.keyFingerprint = keyFingerprint;
        c.createdBy = createdBy;
        c.createdAt = now;
        return c;
    }

    public boolean isRevoked() { return revokedAt != null; }

    public void revoke(Instant now) { this.revokedAt = now; }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public String getLabel() { return label; }
    public String getProvider() { return provider; }
    public String getKeyLast4() { return keyLast4; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getRevokedAt() { return revokedAt; }
}
