package com.workspace.agent.application;

import com.workspace.agent.application.RoleViews.ActiveConfig;
import com.workspace.agent.application.RoleViews.ConfigInput;
import com.workspace.agent.application.RoleViews.ConfigVersionView;
import com.workspace.agent.application.RoleViews.RoleView;
import com.workspace.agent.domain.RoleConfigVersion;
import com.workspace.agent.domain.RoleDefinition;
import com.workspace.agent.domain.RolePermission;
import com.workspace.agent.domain.RolePermissionKey;
import com.workspace.agent.infrastructure.RoleConfigVersionRepository;
import com.workspace.agent.infrastructure.RoleDefinitionRepository;
import com.workspace.agent.infrastructure.RolePermissionRepository;
import com.workspace.agent.published.ContextProfile;
import com.workspace.agent.published.Permission;
import com.workspace.agent.published.PermissionCode;
import com.workspace.audit.published.AuditLog;
import com.workspace.project.published.ProjectAccess;
import com.workspace.project.published.ProjectRole;
import com.workspace.shared.published.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Roles and their immutable config versions. OWNER only (brief section 13). */
@Service
public class RoleService {

    private static final Set<ProjectRole> OWNER_ONLY = Set.of(ProjectRole.OWNER);

    private final RoleDefinitionRepository roles;
    private final RoleConfigVersionRepository versions;
    private final RolePermissionRepository permissions;
    private final ProjectAccess access;
    private final AuditLog audit;
    private final Clock clock;
    private final JsonMapper json;

    public RoleService(RoleDefinitionRepository roles, RoleConfigVersionRepository versions,
                       RolePermissionRepository permissions, ProjectAccess access, AuditLog audit, Clock clock,
                       JsonMapper json) {
        this.roles = roles;
        this.versions = versions;
        this.permissions = permissions;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
        this.json = json;
    }

    /** Creates the five default roles unless the project already has built-in roles. Runs in the caller's transaction. */
    @Transactional
    public void seedDefaults(UUID projectId, UUID userId) {
        if (roles.existsByProjectIdAndBuiltinTrue(projectId)) return;
        for (RoleTemplates.Template t : RoleTemplates.defaults()) {
            RoleDefinition role = roles.save(RoleDefinition.create(projectId, t.code(), t.name(), true, now()));
            RoleConfigVersion v = storeVersion(role.getId(), 1, new ConfigInput(t.systemPrompt(), t.profile(),
                    t.permissions()), userId);
            role.activate(v.getId());
            roles.flush();
            audit.record(projectId, AuditLog.ACTOR_USER, userId.toString(), "ROLE_CREATED", "ROLE", role.getId(),
                    null, roleSnapshot(role, 1));
        }
    }

    @Transactional
    public RoleView createRole(UUID userId, UUID projectId, String code, String name, ConfigInput config) {
        access.requireRole(projectId, userId, OWNER_ONLY);
        if (roles.existsByProjectIdAndCode(projectId, code)) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_ROLE", "A role with this code already exists");
        }
        ContextProfileValidator.validate(config.contextProfile(), config.permissions());
        RoleDefinition role = roles.saveAndFlush(RoleDefinition.create(projectId, code, name.trim(), false, now()));
        RoleConfigVersion v = storeVersion(role.getId(), 1, config, userId);
        role.activate(v.getId()); // a role without an active config is unusable; a NEW role starts with v1 active
        roles.flush();
        audit.record(projectId, AuditLog.ACTOR_USER, userId.toString(), "ROLE_CREATED", "ROLE", role.getId(), null,
                roleSnapshot(role, 1));
        return view(role, v);
    }

    @Transactional
    public List<RoleView> list(UUID userId, UUID projectId) {
        access.requireRole(projectId, userId, OWNER_ONLY);
        seedDefaults(projectId, userId); // projects created before M2b get their default roles on first use
        List<RoleDefinition> rs = roles.findByProjectIdOrderByCodeAsc(projectId);
        Map<UUID, RoleConfigVersion> active = versions.findByIdIn(rs.stream()
                        .map(RoleDefinition::getActiveConfigVersionId).filter(java.util.Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(RoleConfigVersion::getId, v -> v));
        return rs.stream().map(r -> view(r, r.getActiveConfigVersionId() == null ? null
                : active.get(r.getActiveConfigVersionId()))).toList();
    }

    @Transactional(readOnly = true)
    public RoleView get(UUID userId, UUID roleId) {
        RoleDefinition r = loadOwned(userId, roleId);
        return view(r, r.getActiveConfigVersionId() == null ? null
                : versions.findById(r.getActiveConfigVersionId()).orElse(null));
    }

    @Transactional
    public ConfigVersionView createConfigVersion(UUID userId, UUID roleId, ConfigInput config) {
        RoleDefinition r = roles.lockById(roleId).orElseThrow(ApiException::notFound);
        access.requireRole(r.getProjectId(), userId, OWNER_ONLY);
        ContextProfileValidator.validate(config.contextProfile(), config.permissions());
        int next = versions.maxVersionNo(roleId) + 1;
        RoleConfigVersion v = storeVersion(roleId, next, config, userId);
        audit.record(r.getProjectId(), AuditLog.ACTOR_USER, userId.toString(), "ROLE_CONFIG_CREATED", "ROLE", roleId,
                null, Map.of("versionNo", next, "roleCode", r.getCode()));
        return configView(v, config.contextProfile(), config.permissions(), false);
    }

    @Transactional
    public ConfigVersionView activate(UUID userId, UUID roleId, int versionNo) {
        RoleDefinition r = roles.lockById(roleId).orElseThrow(ApiException::notFound);
        access.requireRole(r.getProjectId(), userId, OWNER_ONLY);
        RoleConfigVersion v = versions.findByRoleIdAndVersionNo(roleId, versionNo).orElseThrow(ApiException::notFound);
        boolean already = v.getId().equals(r.getActiveConfigVersionId());
        if (!already) {
            Integer from = r.getActiveConfigVersionId() == null ? null
                    : versions.findById(r.getActiveConfigVersionId()).map(RoleConfigVersion::getVersionNo).orElse(null);
            r.activate(v.getId());
            roles.flush();
            Map<String, Object> before = new LinkedHashMap<>();
            before.put("activeVersionNo", from);
            audit.record(r.getProjectId(), AuditLog.ACTOR_USER, userId.toString(), "ROLE_CONFIG_ACTIVATED", "ROLE",
                    roleId, before, Map.of("activeVersionNo", versionNo, "roleCode", r.getCode()));
        }
        return toView(v, true);
    }

    @Transactional(readOnly = true)
    public List<ConfigVersionView> listConfigVersions(UUID userId, UUID roleId) {
        RoleDefinition r = loadOwned(userId, roleId);
        List<RoleConfigVersion> vs = versions.findByRoleIdOrderByVersionNoDesc(roleId);
        Map<UUID, List<Permission>> perms = permissionsOf(vs.stream().map(RoleConfigVersion::getId).toList());
        return vs.stream().map(v -> configView(v, read(v.getContextProfileJson()), perms.getOrDefault(v.getId(), List.of()),
                v.getId().equals(r.getActiveConfigVersionId()))).toList();
    }

    private RoleDefinition loadOwned(UUID userId, UUID roleId) {
        RoleDefinition r = roles.findById(roleId).orElseThrow(ApiException::notFound);
        access.requireRole(r.getProjectId(), userId, OWNER_ONLY);
        return r;
    }

    private RoleConfigVersion storeVersion(UUID roleId, int no, ConfigInput c, UUID userId) {
        RoleConfigVersion v = versions.saveAndFlush(RoleConfigVersion.create(roleId, no, c.systemPrompt().strip(),
                json.writeValueAsString(c.contextProfile()), userId, now()));
        permissions.saveAll(c.permissions().stream()
                .map(p -> new RolePermission(new RolePermissionKey(v.getId(), p.code().name(), p.resourceType()))).toList());
        permissions.flush();
        return v;
    }

    private ConfigVersionView toView(RoleConfigVersion v, boolean active) {
        return configView(v, read(v.getContextProfileJson()), permissionsOf(List.of(v.getId())).getOrDefault(v.getId(), List.of()), active);
    }

    private Map<UUID, List<Permission>> permissionsOf(List<UUID> versionIds) {
        if (versionIds.isEmpty()) return Map.of();
        return permissions.findByVersionIds(versionIds).stream().collect(Collectors.groupingBy(
                p -> p.getId().getRoleConfigVersionId(),
                Collectors.mapping(p -> new Permission(PermissionCode.valueOf(p.getId().getPermissionCode()),
                        p.getId().getResourceType()), Collectors.collectingAndThen(Collectors.toList(), l -> l.stream()
                        .sorted(Comparator.comparing((Permission p) -> p.code().name()).thenComparing(Permission::resourceType))
                        .toList()))));
    }

    private ContextProfile read(String text) {
        return json.readValue(text, ContextProfile.class);
    }

    private static ConfigVersionView configView(RoleConfigVersion v, ContextProfile profile, List<Permission> perms,
                                                boolean active) {
        return new ConfigVersionView(v.getId(), v.getRoleId(), v.getVersionNo(), v.getSystemPrompt(), profile, perms,
                active, v.getCreatedAt());
    }

    private static RoleView view(RoleDefinition r, RoleConfigVersion active) {
        return new RoleView(r.getId(), r.getProjectId(), r.getCode(), r.getName(), r.isBuiltin(),
                active == null ? null : new ActiveConfig(active.getId(), active.getVersionNo()), r.getCreatedAt());
    }

    private static Map<String, Object> roleSnapshot(RoleDefinition r, int activeVersionNo) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", r.getCode());
        m.put("name", r.getName());
        m.put("builtin", r.isBuiltin());
        m.put("activeVersionNo", activeVersionNo);
        return m;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
