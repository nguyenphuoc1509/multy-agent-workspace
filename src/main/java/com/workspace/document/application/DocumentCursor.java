package com.workspace.document.application;

import com.workspace.shared.published.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/** Opaque keyset cursor for document lists: base64url("<createdAt ISO-8601>|<id>"). */
record DocumentCursor(Instant createdAt, UUID id) {

    String encode() {
        String raw = createdAt + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    static DocumentCursor decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int sep = raw.indexOf('|');
            if (sep < 0) throw new IllegalArgumentException("no separator");
            return new DocumentCursor(Instant.parse(raw.substring(0, sep)), UUID.fromString(raw.substring(sep + 1)));
        } catch (RuntimeException e) {
            throw ApiException.validation("Invalid cursor");
        }
    }
}
