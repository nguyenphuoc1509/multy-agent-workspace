package com.workspace.document.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** expectedHash = content hash of the text the approver is looking at; a mismatch is 409 STALE_CONTENT. */
public record ApproveVersionRequest(
        @NotBlank @Size(max = 100) String expectedHash,
        @Size(max = 2_000) String comment) {}
