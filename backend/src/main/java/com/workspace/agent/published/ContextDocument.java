package com.workspace.agent.published;

import com.workspace.document.published.DocumentType;
import jakarta.validation.constraints.NotNull;

public record ContextDocument(@NotNull DocumentType type, @NotNull DocumentMode mode, boolean required) {}
