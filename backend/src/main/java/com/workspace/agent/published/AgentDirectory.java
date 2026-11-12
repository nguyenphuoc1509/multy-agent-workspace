package com.workspace.agent.published;

import java.util.Optional;
import java.util.UUID;

/** Read-only lookup of agents for other modules. */
public interface AgentDirectory {

    record Info(UUID id, UUID projectId, UUID roleId, String name, String provider, String model, UUID credentialId,
                boolean enabled) {}

    Optional<Info> find(UUID agentId);
}
