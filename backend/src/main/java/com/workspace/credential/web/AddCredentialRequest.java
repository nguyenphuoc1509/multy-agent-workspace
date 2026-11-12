package com.workspace.credential.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The key is write-only. toString is redacted so a stray log line can never leak it. */
public record AddCredentialRequest(
        @NotBlank @Size(max = 100) String label,
        @NotBlank @Size(max = 40) String provider,
        @NotBlank @Size(min = 8, max = 512) String apiKey) {
    @Override
    public String toString() {
        return "AddCredentialRequest[label=" + label + ", provider=" + provider + ", apiKey=redacted]";
    }
}
