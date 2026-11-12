package com.workspace.agent.web;

import com.workspace.agent.published.AgentParams;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateAgentRequest(
        @NotNull UUID roleId,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 40) String provider,
        @NotBlank @Size(max = 100) String model,
        @Valid AgentParams params,
        UUID credentialId) {}
