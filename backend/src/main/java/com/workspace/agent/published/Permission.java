package com.workspace.agent.published;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** resourceType is "*" or a document type name. */
public record Permission(@NotNull PermissionCode code, @Size(max = 40) String resourceType) {
    public Permission {
        if (resourceType == null || resourceType.isBlank()) resourceType = "*";
    }
}
