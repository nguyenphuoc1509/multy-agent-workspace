package com.workspace.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class RolePermissionKey implements Serializable {

    @Column(name = "role_config_version_id")
    private UUID roleConfigVersionId;

    @Column(name = "permission_code")
    private String permissionCode;

    @Column(name = "resource_type")
    private String resourceType;

    protected RolePermissionKey() {}

    public RolePermissionKey(UUID roleConfigVersionId, String permissionCode, String resourceType) {
        this.roleConfigVersionId = roleConfigVersionId;
        this.permissionCode = permissionCode;
        this.resourceType = resourceType;
    }

    public UUID getRoleConfigVersionId() { return roleConfigVersionId; }
    public String getPermissionCode() { return permissionCode; }
    public String getResourceType() { return resourceType; }

    @Override
    public boolean equals(Object o) {
        return o instanceof RolePermissionKey k && Objects.equals(roleConfigVersionId, k.roleConfigVersionId)
                && Objects.equals(permissionCode, k.permissionCode) && Objects.equals(resourceType, k.resourceType);
    }

    @Override
    public int hashCode() { return Objects.hash(roleConfigVersionId, permissionCode, resourceType); }
}
