package com.workspace.project.infrastructure;

import com.workspace.project.domain.Project;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    /** Keyset pagination on (created_at desc, id desc), restricted to projects the user is a member of. */
    @Query(value = """
            select p.* from project p
            join project_member m on m.project_id = p.id
            where m.user_id = :userId
            order by p.created_at desc, p.id desc
            limit :limit
            """, nativeQuery = true)
    List<Project> firstPage(@Param("userId") UUID userId, @Param("limit") int limit);

    @Query(value = """
            select p.* from project p
            join project_member m on m.project_id = p.id
            where m.user_id = :userId
              and (p.created_at < :createdAt or (p.created_at = :createdAt and p.id < :id))
            order by p.created_at desc, p.id desc
            limit :limit
            """, nativeQuery = true)
    List<Project> pageAfter(@Param("userId") UUID userId, @Param("createdAt") Instant createdAt,
                            @Param("id") UUID id, @Param("limit") int limit);
}
