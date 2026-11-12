package com.workspace.agent.infrastructure;

import com.workspace.agent.domain.RolePermission;
import com.workspace.agent.domain.RolePermissionKey;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionKey> {

    @Query("select p from RolePermission p where p.id.roleConfigVersionId in :ids")
    List<RolePermission> findByVersionIds(@Param("ids") Collection<UUID> ids);
}
