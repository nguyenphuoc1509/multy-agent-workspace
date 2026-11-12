package com.workspace.document.web;

import jakarta.validation.constraints.Size;
import java.util.UUID;

/** All fields optional; send {} for "new draft from the current approved version". */
public record CreateVersionRequest(
        UUID fromVersionId,
        @Size(max = 1_000_000) String content,
        @Size(max = 2_000) String changeSummary) {}
