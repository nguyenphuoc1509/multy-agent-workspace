package com.workspace.workflow.web;

import com.workspace.document.published.DocumentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** target: exactly one of documentId or newDocument. */
public record PromoteRequest(@NotNull @Valid Target target, @Size(max = 2_000) String changeSummary) {

    public record Target(UUID documentId, @Valid NewDocument newDocument) {}

    public record NewDocument(@NotNull DocumentType type, @NotBlank @Size(max = 200) String title) {}
}
