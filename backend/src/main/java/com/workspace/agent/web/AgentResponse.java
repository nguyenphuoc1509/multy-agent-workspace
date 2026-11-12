package com.workspace.agent.web;

import com.workspace.agent.application.AgentView;
import com.workspace.agent.published.AgentParams;
import java.time.Instant;
import java.util.UUID;

public record AgentResponse(UUID id, UUID roleId, String name, String provider, String executorType, String model,
                            AgentParams params, UUID credentialId, boolean isEnabled, Instant createdAt) {
    static AgentResponse from(AgentView v) {
        return new AgentResponse(v.id(), v.roleId(), v.name(), v.provider(), v.executorType(), v.model(), v.params(),
                v.credentialId(), v.enabled(), v.createdAt());
    }
}
