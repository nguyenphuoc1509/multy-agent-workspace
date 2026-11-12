package com.workspace.agent.infrastructure;

import com.workspace.agent.domain.RoleConfigVersion;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleConfigVersionRepository extends JpaRepository<RoleConfigVersion, UUID> {

    List<RoleConfigVersion> findByRoleIdOrderByVersionNoDesc(UUID roleId);

    Optional<RoleConfigVersion> findByRoleIdAndVersionNo(UUID roleId, int versionNo);

    List<RoleConfigVersion> findByIdIn(Collection<UUID> ids);

    @Query("select coalesce(max(v.versionNo), 0) from RoleConfigVersion v where v.roleId = :roleId")
    int maxVersionNo(@Param("roleId") UUID roleId);
}
