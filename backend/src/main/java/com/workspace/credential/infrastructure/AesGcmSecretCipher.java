package com.workspace.credential.infrastructure;

import com.workspace.credential.application.SecretCipher;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM, random 12-byte nonce, blob = nonce || ciphertext+tag. The only class that touches javax.crypto for
 * API keys (ArchUnit rule 5). Fails fast at startup when WORKSPACE_MASTER_KEY is missing or not 32 base64 bytes.
 * Only key version 1 exists today; the version column is what makes rotation possible later.
 */
@Component
class AesGcmSecretCipher implements SecretCipher {

    static final int CURRENT_VERSION = 1;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    AesGcmSecretCipher(@Value("${workspace.security.master-key:}") String masterKeyBase64) {
        if (masterKeyBase64 == null || masterKeyBase64.isBlank()) {
            throw new IllegalStateException("WORKSPACE_MASTER_KEY is not set. Generate one with: openssl rand -base64 32");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(masterKeyBase64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("WORKSPACE_MASTER_KEY is not valid base64");
        }
        if (raw.length != 32) {
            throw new IllegalStateException("WORKSPACE_MASTER_KEY must decode to exactly 32 bytes (got " + raw.length + ")");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    @Override
    public Sealed encrypt(String plaintext, byte[] aad) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            c.updateAAD(aad);
            byte[] ct = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new Sealed(CURRENT_VERSION, ByteBuffer.allocate(nonce.length + ct.length).put(nonce).put(ct).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed");
        }
    }

    @Override
    public String decrypt(int keyVersion, byte[] blob, byte[] aad) {
        if (keyVersion != CURRENT_VERSION) throw new IllegalStateException("Unknown master key version " + keyVersion);
        if (blob == null || blob.length <= NONCE_BYTES) throw new IllegalStateException("Malformed ciphertext");
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, blob, 0, NONCE_BYTES));
            c.updateAAD(aad);
            return new String(c.doFinal(blob, NONCE_BYTES, blob.length - NONCE_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Decryption failed");
        }
    }
}
