package com.workspace.credential.application;

/**
 * Port for encrypting provider API keys at rest. The master key lives in the environment, never in the database.
 * {@code aad} binds a ciphertext to its project so a stored blob cannot be replayed under another project.
 */
public interface SecretCipher {

    record Sealed(int keyVersion, byte[] blob) {}

    Sealed encrypt(String plaintext, byte[] aad);

    /** @throws IllegalStateException when the key version is unknown or the blob was tampered with */
    String decrypt(int keyVersion, byte[] blob, byte[] aad);
}
