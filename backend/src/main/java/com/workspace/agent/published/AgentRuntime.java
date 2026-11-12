package com.workspace.agent.published;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** What a run needs to know about an agent: its model, limits, and the role's ACTIVE config version. */
public interface AgentRuntime {

    record RoleConfig(UUID roleConfigVersionId, String roleCode, String systemPrompt, ContextProfile profile,
                      Set<Permission> permissions) {}

    record Runtime(AgentDirectory.Info agent, AgentParams params, RoleConfig config) {}

    Optional<Runtime> load(UUID agentId);

    /** A specific (possibly no longer active) config version, e.g. to re-render a snapshot. */
    Optional<RoleConfig> configVersion(UUID roleConfigVersionId);
}
