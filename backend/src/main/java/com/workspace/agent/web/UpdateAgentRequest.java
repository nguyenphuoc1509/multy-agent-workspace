package com.workspace.agent.web;

import com.workspace.agent.published.AgentParams;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Absent (null) fields are left unchanged. The role cannot be changed (create a new agent), nor can a credential be unset. */
public record UpdateAgentRequest(
        @Size(max = 100) String name,
        @Size(max = 100) String model,
        @Valid AgentParams params,
        UUID credentialId,
        Boolean isEnabled) {}
