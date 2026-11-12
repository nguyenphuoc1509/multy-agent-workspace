package com.workspace.agent.application;

import com.workspace.agent.infrastructure.AgentRepository;
import com.workspace.agent.published.AgentDirectory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class AgentDirectoryService implements AgentDirectory {

    private final AgentRepository agents;

    AgentDirectoryService(AgentRepository agents) {
        this.agents = agents;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Info> find(UUID agentId) {
        return agents.findById(agentId).map(a -> new Info(a.getId(), a.getProjectId(), a.getRoleId(), a.getName(),
                a.getProvider(), a.getModel(), a.getCredentialId(), a.isEnabled()));
    }
}
