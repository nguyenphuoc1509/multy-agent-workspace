package com.workspace.conversation.infrastructure;

import com.workspace.conversation.domain.Conversation;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversation c where c.id = :id")
    Optional<Conversation> lockById(@Param("id") UUID id);

    @Query(value = """
            select c.* from conversation c
            where c.project_id = :projectId
              and (cast(:agentId as uuid) is null or c.agent_id = cast(:agentId as uuid))
            order by c.created_at desc, c.id desc
            limit :limit
            """, nativeQuery = true)
    List<Conversation> firstPage(@Param("projectId") UUID projectId, @Param("agentId") UUID agentId,
                                 @Param("limit") int limit);

    @Query(value = """
            select c.* from conversation c
            where c.project_id = :projectId
              and (cast(:agentId as uuid) is null or c.agent_id = cast(:agentId as uuid))
              and (c.created_at < :createdAt or (c.created_at = :createdAt and c.id < :id))
            order by c.created_at desc, c.id desc
            limit :limit
            """, nativeQuery = true)
    List<Conversation> pageAfter(@Param("projectId") UUID projectId, @Param("agentId") UUID agentId,
                                 @Param("createdAt") Instant createdAt, @Param("id") UUID id,
                                 @Param("limit") int limit);
}
