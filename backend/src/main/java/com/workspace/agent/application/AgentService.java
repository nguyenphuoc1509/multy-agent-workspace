package com.workspace.agent.application;

import com.workspace.agent.domain.Agent;
import com.workspace.agent.domain.RoleDefinition;
import com.workspace.agent.infrastructure.AgentRepository;
import com.workspace.agent.infrastructure.RoleDefinitionRepository;
import com.workspace.agent.published.AgentParams;
import com.workspace.audit.published.AuditLog;
import com.workspace.credential.published.CredentialDirectory;
import com.workspace.project.published.ProjectAccess;
import com.workspace.project.published.ProjectRole;
import com.workspace.shared.published.ApiException;
import com.workspace.shared.published.FieldIssue;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Agent = who plays a role (provider + model + credential). OWNER only (brief section 13). */
@Service
public class AgentService {

    private static final Set<ProjectRole> OWNER_ONLY = Set.of(ProjectRole.OWNER);
    private static final Set<String> PROVIDERS = Set.of("ANTHROPIC"); // extend only when an adapter exists

    private final AgentRepository agents;
    private final RoleDefinitionRepository roles;
    private final CredentialDirectory credentials;
    private final ProjectAccess access;
    private final AuditLog audit;
    private final Clock clock;
    private final JsonMapper json;

    public AgentService(AgentRepository agents, RoleDefinitionRepository roles, CredentialDirectory credentials,
                        ProjectAccess access, AuditLog audit, Clock clock, JsonMapper json) {
        this.agents = agents;
        this.roles = roles;
        this.credentials = credentials;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
        this.json = json;
    }

    @Transactional
    public AgentView create(UUID userId, UUID projectId, UUID roleId, String name, String provider, String model,
                            AgentParams params, UUID credentialId) {
        access.requireRole(projectId, userId, OWNER_ONLY);
        if (!PROVIDERS.contains(provider)) throw invalid("provider", "Unsupported provider");
        RoleDefinition role = roles.findById(roleId).filter(r -> r.getProjectId().equals(projectId))
                .orElseThrow(() -> invalid("roleId", "Role not found in this project"));
        if (role.getActiveConfigVersionId() == null) throw invalid("roleId", "Role has no active config version");
        if (agents.existsByProjectIdAndName(projectId, name.trim())) throw duplicate();
        checkCredential(projectId, provider, credentialId);

        Agent a = agents.saveAndFlush(Agent.create(projectId, roleId, name.trim(), provider, model.trim(),
                write(params == null ? new AgentParams(null, null, null) : params), credentialId,
                clock.instant().truncatedTo(ChronoUnit.MICROS)));
        audit.record(projectId, AuditLog.ACTOR_USER, userId.toString(), "AGENT_CREATED", "AGENT", a.getId(), null,
                snapshot(a));
        return view(a);
    }

    @Transactional(readOnly = true)
    public List<AgentView> list(UUID userId, UUID projectId) {
        access.requireRole(projectId, userId, OWNER_ONLY);
        return agents.findByProjectIdOrderByNameAsc(projectId).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public AgentView get(UUID userId, UUID agentId) {
        Agent a = agents.findById(agentId).orElseThrow(ApiException::notFound);
        access.requireRole(a.getProjectId(), userId, OWNER_ONLY);
        return view(a);
    }

    /** Only the fields that are non-null change. A credential cannot be unset (revoke it instead). */
    @Transactional
    public AgentView update(UUID userId, UUID agentId, String name, String model, AgentParams params,
                            UUID credentialId, Boolean enabled) {
        Agent a = agents.lockById(agentId).orElseThrow(ApiException::notFound);
        access.requireRole(a.getProjectId(), userId, OWNER_ONLY);
        Map<String, Object> before = snapshot(a);
        if (name != null) {
            String n = name.trim();
            if (n.isEmpty()) throw invalid("name", "must not be blank");
            if (agents.existsByProjectIdAndNameAndIdNot(a.getProjectId(), n, a.getId())) throw duplicate();
            a.rename(n);
        }
        if (model != null) {
            if (model.isBlank()) throw invalid("model", "must not be blank");
            a.changeModel(model.trim());
        }
        if (params != null) a.changeParams(write(params));
        if (credentialId != null) {
            checkCredential(a.getProjectId(), a.getProvider(), credentialId);
            a.useCredential(credentialId);
        }
        if (enabled != null) a.setEnabled(enabled);
        agents.flush();
        audit.record(a.getProjectId(), AuditLog.ACTOR_USER, userId.toString(), "AGENT_UPDATED", "AGENT", a.getId(),
                before, snapshot(a));
        return view(a);
    }

    private void checkCredential(UUID projectId, String provider, UUID credentialId) {
        if (credentialId == null) return;
        CredentialDirectory.Info c = credentials.find(credentialId).filter(i -> i.projectId().equals(projectId))
                .orElseThrow(() -> invalid("credentialId", "Credential not found in this project"));
        if (c.revoked()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CREDENTIAL_UNUSABLE", "The credential is revoked");
        }
        if (!c.provider().equals(provider)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CREDENTIAL_UNUSABLE",
                    "The credential belongs to a different provider");
        }
    }

    private static ApiException invalid(String field, String message) {
        return ApiException.validation(message, List.of(new FieldIssue(field, message)));
    }

    private static ApiException duplicate() {
        return new ApiException(HttpStatus.CONFLICT, "DUPLICATE_AGENT", "An agent with this name already exists in the project");
    }

    private String write(AgentParams p) {
        return json.writeValueAsString(p);
    }

    private AgentView view(Agent a) {
        return new AgentView(a.getId(), a.getProjectId(), a.getRoleId(), a.getName(), a.getProvider(),
                a.getExecutorType(), a.getModel(), json.readValue(a.getParamsJson(), AgentParams.class),
                a.getCredentialId(), a.isEnabled(), a.getCreatedAt());
    }

    private static Map<String, Object> snapshot(Agent a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", a.getName());
        m.put("roleId", a.getRoleId().toString());
        m.put("provider", a.getProvider());
        m.put("model", a.getModel());
        m.put("params", a.getParamsJson());
        m.put("credentialId", a.getCredentialId() == null ? null : a.getCredentialId().toString());
        m.put("enabled", a.isEnabled());
        return m;
    }
}
