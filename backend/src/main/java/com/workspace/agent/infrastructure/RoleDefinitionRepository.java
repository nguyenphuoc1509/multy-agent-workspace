package com.workspace.agent.infrastructure;

import com.workspace.agent.domain.RoleDefinition;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleDefinitionRepository extends JpaRepository<RoleDefinition, UUID> {

    List<RoleDefinition> findByProjectIdOrderByCodeAsc(UUID projectId);

    boolean existsByProjectIdAndCode(UUID projectId, String code);

    boolean existsByProjectIdAndBuiltinTrue(UUID projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RoleDefinition r where r.id = :id")
    Optional<RoleDefinition> lockById(@Param("id") UUID id);
}
