package com.workspace.document.web;

import jakarta.validation.constraints.Size;

/** Absent field = unchanged; at least one field is required. */
public record UpdateVersionRequest(
        @Size(max = 1_000_000) String content,
        @Size(max = 2_000) String changeSummary) {}
