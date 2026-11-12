package com.workspace.agent.application;

import com.workspace.agent.domain.RoleConfigVersion;
import com.workspace.agent.domain.RoleDefinition;
import com.workspace.agent.infrastructure.AgentRepository;
import com.workspace.agent.infrastructure.RoleConfigVersionRepository;
import com.workspace.agent.infrastructure.RoleDefinitionRepository;
import com.workspace.agent.infrastructure.RolePermissionRepository;
import com.workspace.agent.published.AgentDirectory;
import com.workspace.agent.published.AgentParams;
import com.workspace.agent.published.AgentRuntime;
import com.workspace.agent.published.ContextProfile;
import com.workspace.agent.published.ExecutorType;
import com.workspace.agent.published.Permission;
import com.workspace.agent.published.PermissionCode;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Component
class AgentRuntimeService implements AgentRuntime {

    private final AgentRepository agents;
    private final RoleDefinitionRepository roles;
    private final RoleConfigVersionRepository versions;
    private final RolePermissionRepository permissions;
    private final JsonMapper json;

    AgentRuntimeService(AgentRepository agents, RoleDefinitionRepository roles, RoleConfigVersionRepository versions,
                        RolePermissionRepository permissions, JsonMapper json) {
        this.agents = agents;
        this.roles = roles;
        this.versions = versions;
        this.permissions = permissions;
        this.json = json;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Runtime> load(UUID agentId) {
        return agents.findById(agentId).flatMap(a -> {
            RoleDefinition role = roles.findById(a.getRoleId()).orElse(null);
            if (role == null || role.getActiveConfigVersionId() == null) return Optional.empty();
            return versions.findById(role.getActiveConfigVersionId()).map(v -> new Runtime(
                    new AgentDirectory.Info(a.getId(), a.getProjectId(), a.getRoleId(), a.getName(), a.getProvider(),
                            a.getModel(), a.getCredentialId(), a.isEnabled()),
                    json.readValue(a.getParamsJson(), AgentParams.class), config(role.getCode(), v)));
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RoleConfig> configVersion(UUID roleConfigVersionId) {
        return versions.findById(roleConfigVersionId).flatMap(v -> roles.findById(v.getRoleId())
                .map(r -> config(r.getCode(), v)));
    }

    private RoleConfig config(String roleCode, RoleConfigVersion v) {
        Set<Permission> perms = permissions.findByVersionIds(List.of(v.getId())).stream()
                .map(p -> new Permission(PermissionCode.valueOf(p.getId().getPermissionCode()), p.getId().getResourceType()))
                .collect(Collectors.toUnmodifiableSet());
        return new RoleConfig(v.getId(), roleCode, v.getSystemPrompt(),
                json.readValue(v.getContextProfileJson(), ContextProfile.class), ExecutorType.CHAT.effective(perms));
    }
}
