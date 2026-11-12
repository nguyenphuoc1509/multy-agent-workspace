package com.workspace.document.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** content_hash = "sha256:" + lowercase hex of the UTF-8 bytes of the LF-normalised content. */
public final class ContentHash {

    private ContentHash() {}

    public static String normalise(String content) {
        return content.replace("\r\n", "\n").replace('\r', '\n');
    }

    public static String of(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(normalise(content).getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}