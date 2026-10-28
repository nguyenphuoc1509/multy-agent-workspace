package com.workspace.document.infrastructure;

import com.workspace.document.domain.Document;
import com.workspace.document.published.DocumentType;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    /** SELECT ... FOR UPDATE: serialises version creation, approval and rejection per document. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Document d where d.id = :id")
    Optional<Document> findByIdForUpdate(@Param("id") UUID id);

    /** Gate check: at least one document of that type in the project has an approved version. */
    boolean existsByProjectIdAndTypeAndCurrentApprovedVersionIdIsNotNull(UUID projectId, DocumentType type);

    /** typeName may be null (no filter). Keyset on (created_at desc, id desc). */
    @Query(value = """
            select d.* from document d
            where d.project_id = :projectId
              and (cast(:typeName as text) is null or d.type = cast(:typeName as text))
            order by d.created_at desc, d.id desc
            limit :limit
            """, nativeQuery = true)
    List<Document> firstPage(@Param("projectId") UUID projectId, @Param("typeName") String typeName,
                             @Param("limit") int limit);

    @Query(value = """
            select d.* from document d
            where d.project_id = :projectId
              and (cast(:typeName as text) is null or d.type = cast(:typeName as text))
              and (d.created_at < :createdAt or (d.created_at = :createdAt and d.id < :id))
            order by d.created_at desc, d.id desc
            limit :limit
            """, nativeQuery = true)
    List<Document> pageAfter(@Param("projectId") UUID projectId, @Param("typeName") String typeName,
                             @Param("createdAt") Instant createdAt, @Param("id") UUID id,
                             @Param("limit") int limit);
}