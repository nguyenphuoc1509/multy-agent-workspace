package com.workspace.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.workspace.document.domain.ContentHash;
import org.junit.jupiter.api.Test;

class ContentHashTest {

    @Test
    void knownSha256Vector() {
        assertThat(ContentHash.of("abc"))
                .isEqualTo("sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void lineEndingsAreNormalisedToLf() {
        assertThat(ContentHash.normalise("a\r\nb\rc")).isEqualTo("a\nb\nc");
        assertThat(ContentHash.of("a\r\nb")).isEqualTo(ContentHash.of("a\nb"));
        assertThat(ContentHash.of("a\rb")).isEqualTo(ContentHash.of("a\nb"));
    }

    @Test
    void differentContentGivesDifferentHash() {
        assertThat(ContentHash.of("é")).isNotEqualTo(ContentHash.of("e"));
    }
}