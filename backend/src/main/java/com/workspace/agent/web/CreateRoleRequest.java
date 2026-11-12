package com.workspace.agent.web;

import com.workspace.agent.published.ContextProfile;
import com.workspace.agent.published.Permission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** A new custom role starts with config version 1, which is active. */
public record CreateRoleRequest(
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,39}$", message = "must match ^[A-Z][A-Z0-9_]{1,39}$") String code,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 20_000) String systemPrompt,
        @NotNull @Valid ContextProfile contextProfile,
        @NotNull @Size(max = 60) List<@NotNull @Valid Permission> permissions) {}
