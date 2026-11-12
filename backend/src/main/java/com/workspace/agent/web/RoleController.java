package com.workspace.agent.web;

import com.workspace.agent.application.RoleService;
import com.workspace.agent.application.RoleViews.ConfigInput;
import com.workspace.identity.published.CurrentUserProvider;
import com.workspace.shared.published.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class RoleController {

    private final RoleService roles;
    private final CurrentUserProvider currentUser;

    public RoleController(RoleService roles, CurrentUserProvider currentUser) {
        this.roles = roles;
        this.currentUser = currentUser;
    }

    @PostMapping("/projects/{projectId}/roles")
    @ResponseStatus(HttpStatus.CREATED)
    public RoleResponse create(@PathVariable UUID projectId, @Valid @RequestBody CreateRoleRequest body) {
        return RoleResponse.from(roles.createRole(currentUser.require().id(), projectId, body.code(), body.name(),
                new ConfigInput(body.systemPrompt(), body.contextProfile(), body.permissions())));
    }

    @GetMapping("/projects/{projectId}/roles")
    public PageResponse<RoleResponse> list(@PathVariable UUID projectId) {
        return new PageResponse<>(roles.list(currentUser.require().id(), projectId).stream()
                .map(RoleResponse::from).toList(), null);
    }

    @GetMapping("/roles/{roleId}")
    public RoleResponse get(@PathVariable UUID roleId) {
        return RoleResponse.from(roles.get(currentUser.require().id(), roleId));
    }

    @PostMapping("/roles/{roleId}/config-versions")
    @ResponseStatus(HttpStatus.CREATED)
    public ConfigVersionResponse createConfigVersion(@PathVariable UUID roleId,
                                                     @Valid @RequestBody CreateConfigVersionRequest body) {
        return ConfigVersionResponse.from(roles.createConfigVersion(currentUser.require().id(), roleId,
                new ConfigInput(body.systemPrompt(), body.contextProfile(), body.permissions())));
    }

    @GetMapping("/roles/{roleId}/config-versions")
    public PageResponse<ConfigVersionResponse> listConfigVersions(@PathVariable UUID roleId) {
        return new PageResponse<>(roles.listConfigVersions(currentUser.require().id(), roleId).stream()
                .map(ConfigVersionResponse::from).toList(), null);
    }

    @PostMapping("/roles/{roleId}/config-versions/{versionNo}/activate")
    public ConfigVersionResponse activate(@PathVariable UUID roleId, @PathVariable int versionNo) {
        return ConfigVersionResponse.from(roles.activate(currentUser.require().id(), roleId, versionNo));
    }
}
