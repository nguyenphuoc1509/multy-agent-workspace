package com.workspace.document.web;

import com.workspace.document.published.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateDocumentRequest(
        @NotNull DocumentType type,
        @NotBlank @Size(max = 200) String title,
        @NotNull @Size(max = 1_000_000) String content,
        @Size(max = 2_000) String changeSummary) {}
