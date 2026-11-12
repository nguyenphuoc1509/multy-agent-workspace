package com.workspace.document.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectVersionRequest(@NotBlank @Size(max = 2_000) String comment) {}
