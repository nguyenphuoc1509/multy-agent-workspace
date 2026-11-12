package com.workspace.credential.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.workspace.credential.application.SecretCipher;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** Pure unit tests (no Spring, no DB) for the AES-256-GCM cipher and its fail-fast startup check. */
class AesGcmSecretCipherTest {

    private static final String KEY_B64 = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    private static final byte[] AAD = "project-1".getBytes(StandardCharsets.UTF_8);

    private final AesGcmSecretCipher cipher = new AesGcmSecretCipher(KEY_B64);

    @Test
    void roundTrips() {
        SecretCipher.Sealed s = cipher.encrypt("sk-ant-hello", AAD);
        assertThat(s.keyVersion()).isEqualTo(1);
        assertThat(cipher.decrypt(s.keyVersion(), s.blob(), AAD)).isEqualTo("sk-ant-hello");
    }

    @Test
    void ciphertextHidesPlaintextAndNonceIsRandom() {
        SecretCipher.Sealed a = cipher.encrypt("sk-ant-hello", AAD);
        SecretCipher.Sealed b = cipher.encrypt("sk-ant-hello", AAD);
        assertThat(a.blob()).isNotEqualTo(b.blob());
        assertThat(new String(a.blob(), StandardCharsets.ISO_8859_1)).doesNotContain("sk-ant-hello");
        assertThat(a.blob().length).isEqualTo(12 + "sk-ant-hello".length() + 16);
    }

    @Test
    void tamperingWrongAadAndUnknownVersionAreRejected() {
        SecretCipher.Sealed s = cipher.encrypt("sk-ant-hello", AAD);
        byte[] tampered = Arrays.copyOf(s.blob(), s.blob().length);
        tampered[tampered.length - 1] ^= 1;
        assertThatThrownBy(() -> cipher.decrypt(1, tampered, AAD)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt(1, s.blob(), "project-2".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt(2, s.blob(), AAD)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt(1, new byte[5], AAD)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anotherMasterKeyCannotDecrypt() {
        SecretCipher.Sealed s = cipher.encrypt("sk-ant-hello", AAD);
        AesGcmSecretCipher other = new AesGcmSecretCipher(Base64.getEncoder()
                .encodeToString("ffffffffffffffffffffffffffffffff".getBytes(StandardCharsets.UTF_8)));
        assertThatThrownBy(() -> other.decrypt(1, s.blob(), AAD)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void startupFailsFastOnMissingOrBadMasterKey_withoutEchoingIt() {
        assertThatThrownBy(() -> new AesGcmSecretCipher("")).hasMessageContaining("WORKSPACE_MASTER_KEY");
        assertThatThrownBy(() -> new AesGcmSecretCipher(null)).hasMessageContaining("WORKSPACE_MASTER_KEY");
        assertThatThrownBy(() -> new AesGcmSecretCipher("not base64 !!")).hasMessageContaining("base64")
                .hasMessageNotContaining("not base64 !!");
        String short31 = Base64.getEncoder().encodeToString(new byte[31]);
        assertThatThrownBy(() -> new AesGcmSecretCipher(short31)).hasMessageContaining("32 bytes");
    }
}
