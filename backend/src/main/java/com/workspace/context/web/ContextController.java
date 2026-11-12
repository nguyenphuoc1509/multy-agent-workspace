package com.workspace.context.web;

import com.workspace.agent.published.AgentDirectory;
import com.workspace.context.published.ContextAssembler;
import com.workspace.context.published.ConversationHistorySource;
import com.workspace.identity.published.CurrentUserProvider;
import com.workspace.project.published.ProjectAccess;
import com.workspace.project.published.ProjectRole;
import com.workspace.shared.published.ApiException;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ContextController {

    private final ContextAssembler assembler;
    private final AgentDirectory agents;
    private final ConversationHistorySource history;
    private final ProjectAccess access;
    private final CurrentUserProvider currentUser;

    public ContextController(ContextAssembler assembler, AgentDirectory agents, ConversationHistorySource history,
                             ProjectAccess access, CurrentUserProvider currentUser) {
        this.assembler = assembler;
        this.agents = agents;
        this.history = history;
        this.access = access;
        this.currentUser = currentUser;
    }

    /** What the agent would see right now. Writes nothing. OWNER only, like agents. */
    @GetMapping("/agents/{agentId}/context-preview")
    public ContextPreviewResponse preview(@PathVariable UUID agentId,
                                          @RequestParam(required = false) UUID conversationId) {
        AgentDirectory.Info agent = agents.find(agentId).orElseThrow(ApiException::notFound);
        access.requireRole(agent.projectId(), currentUser.require().id(), Set.of(ProjectRole.OWNER));
        if (conversationId != null && !history.belongsToAgent(conversationId, agentId)) throw ApiException.notFound();
        return ContextPreviewResponse.from(assembler.assemble(agentId, conversationId, null, ContextAssembler.Mode.PREVIEW));
    }
}
