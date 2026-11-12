package com.workspace.agent.web;

import com.workspace.agent.published.ContextProfile;
import com.workspace.agent.published.Permission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Creating a config version does NOT activate it. */
public record CreateConfigVersionRequest(
        @NotBlank @Size(max = 20_000) String systemPrompt,
        @NotNull @Valid ContextProfile contextProfile,
        @NotNull @Size(max = 60) List<@NotNull @Valid Permission> permissions) {}
