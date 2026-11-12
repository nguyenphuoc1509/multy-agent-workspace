package com.workspace.agent.web;

import com.workspace.agent.application.AgentService;
import com.workspace.identity.published.CurrentUserProvider;
import com.workspace.shared.published.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AgentController {

    private final AgentService agents;
    private final CurrentUserProvider currentUser;

    public AgentController(AgentService agents, CurrentUserProvider currentUser) {
        this.agents = agents;
        this.currentUser = currentUser;
    }

    @PostMapping("/projects/{projectId}/agents")
    @ResponseStatus(HttpStatus.CREATED)
    public AgentResponse create(@PathVariable UUID projectId, @Valid @RequestBody CreateAgentRequest b) {
        return AgentResponse.from(agents.create(currentUser.require().id(), projectId, b.roleId(), b.name(),
                b.provider(), b.model(), b.params(), b.credentialId()));
    }

    @GetMapping("/projects/{projectId}/agents")
    public PageResponse<AgentResponse> list(@PathVariable UUID projectId) {
        return new PageResponse<>(agents.list(currentUser.require().id(), projectId).stream()
                .map(AgentResponse::from).toList(), null);
    }

    @GetMapping("/agents/{agentId}")
    public AgentResponse get(@PathVariable UUID agentId) {
        return AgentResponse.from(agents.get(currentUser.require().id(), agentId));
    }

    @PatchMapping("/agents/{agentId}")
    public AgentResponse update(@PathVariable UUID agentId, @Valid @RequestBody UpdateAgentRequest b) {
        return AgentResponse.from(agents.update(currentUser.require().id(), agentId, b.name(), b.model(), b.params(),
                b.credentialId(), b.isEnabled()));
    }
}
