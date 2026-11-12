package com.workspace.agent.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "role_permission")
public class RolePermission {

    @EmbeddedId
    private RolePermissionKey id;

    protected RolePermission() {}

    public RolePermission(RolePermissionKey id) { this.id = id; }

    public RolePermissionKey getId() { return id; }
}
