package com.workspace.run.infrastructure;

import com.workspace.run.domain.AgentRun;
import com.workspace.run.domain.RunStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentRunRepository extends JpaRepository<AgentRun, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AgentRun r where r.id = :id")
    Optional<AgentRun> lockById(@Param("id") UUID id);

    /** Worker queue: rows another worker holds are skipped (brief section 10). */
    @Query(value = "select * from agent_run where status = 'QUEUED' order by created_at, id limit :n for update skip locked",
            nativeQuery = true)
    List<AgentRun> claimQueued(@Param("n") int n);

    Optional<AgentRun> findFirstByConversationIdAndStatusInOrderByCreatedAtDesc(UUID conversationId, Collection<RunStatus> statuses);

    Optional<AgentRun> findFirstByTriggerMessageIdOrderByCreatedAtDesc(UUID triggerMessageId);

    Optional<AgentRun> findFirstByConversationIdAndStatusOrderByCreatedAtDesc(UUID conversationId, RunStatus status);

    List<AgentRun> findByConversationIdInAndStatusIn(Collection<UUID> conversationIds, Collection<RunStatus> statuses);

    @Query(value = """
            select r.* from agent_run r
            where r.project_id = :projectId and (cast(:status as text) is null or r.status = cast(:status as text))
            order by r.created_at desc, r.id desc limit :limit
            """, nativeQuery = true)
    List<AgentRun> firstPage(@Param("projectId") UUID projectId, @Param("status") String status, @Param("limit") int limit);

    @Query(value = """
            select r.* from agent_run r
            where r.project_id = :projectId and (cast(:status as text) is null or r.status = cast(:status as text))
              and (r.created_at < :createdAt or (r.created_at = :createdAt and r.id < :id))
            order by r.created_at desc, r.id desc limit :limit
            """, nativeQuery = true)
    List<AgentRun> pageAfter(@Param("projectId") UUID projectId, @Param("status") String status,
                             @Param("createdAt") Instant createdAt, @Param("id") UUID id, @Param("limit") int limit);

    @Query(value = """
            select coalesce(sum(coalesce(input_tokens, 0)), 0) as inp, coalesce(sum(coalesce(output_tokens, 0)), 0) as outp
            from agent_run where project_id = :projectId and created_at >= :from and created_at < :to
              and (cast(:exclude as uuid) is null or id <> cast(:exclude as uuid))
            """, nativeQuery = true)
    List<Object[]> usage(@Param("projectId") UUID projectId, @Param("from") Instant from, @Param("to") Instant to,
                         @Param("exclude") UUID excludeRunId);

    @org.springframework.data.jpa.repository.Modifying
    @Query(value = "update agent_run set status = 'FAILED', error_code = 'INTERRUPTED', finished_at = now() where status = 'RUNNING'",
            nativeQuery = true)
    int failInterrupted();
}
