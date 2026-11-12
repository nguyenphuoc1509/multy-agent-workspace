package com.workspace.agent.published;

import java.util.Set;

/** Effective permissions = role permissions INTERSECT executor capabilities (kept so AUTONOMOUS_CODING can be added). */
public enum ExecutorType {
    CHAT(Set.of(PermissionCode.values()));

    private final Set<PermissionCode> capabilities;

    ExecutorType(Set<PermissionCode> capabilities) {
        this.capabilities = capabilities;
    }

    public Set<Permission> effective(Set<Permission> rolePermissions) {
        return rolePermissions.stream().filter(p -> capabilities.contains(p.code()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
