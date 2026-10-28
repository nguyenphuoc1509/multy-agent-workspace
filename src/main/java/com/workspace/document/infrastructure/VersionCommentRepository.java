package com.workspace.document.infrastructure;

import com.workspace.document.domain.VersionComment;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VersionCommentRepository extends JpaRepository<VersionComment, UUID> {

    /** Oldest first. */
    List<VersionComment> findByDocumentVersionIdOrderByCreatedAtAscIdAsc(UUID documentVersionId, Pageable pageable);

    @Query(value = """
            select c.* from version_comment c
            where c.document_version_id = :versionId
              and (c.created_at > :createdAt or (c.created_at = :createdAt and c.id > :id))
            order by c.created_at asc, c.id asc
            limit :limit
            """, nativeQuery = true)
    List<VersionComment> pageAfter(@Param("versionId") UUID versionId, @Param("createdAt") Instant createdAt,
                                   @Param("id") UUID id, @Param("limit") int limit);
}