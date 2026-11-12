package com.workspace.agent.application;

import com.workspace.agent.published.AgentParams;
import java.time.Instant;
import java.util.UUID;

public record AgentView(UUID id, UUID projectId, UUID roleId, String name, String provider, String executorType,
                        String model, AgentParams params, UUID credentialId, boolean enabled, Instant createdAt) {}
