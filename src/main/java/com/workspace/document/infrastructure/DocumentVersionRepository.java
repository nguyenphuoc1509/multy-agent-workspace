package com.workspace.document.infrastructure;

import com.workspace.document.domain.DocumentVersion;
import com.workspace.document.domain.VersionStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {

    @Query("select coalesce(max(v.versionNo), 0) from DocumentVersion v where v.documentId = :documentId")
    int maxVersionNo(@Param("documentId") UUID documentId);

    Optional<DocumentVersion> findFirstByDocumentIdAndStatusIn(UUID documentId, Collection<VersionStatus> statuses);

    List<DocumentVersion> findByDocumentIdInAndStatusIn(Collection<UUID> documentIds,
                                                        Collection<VersionStatus> statuses);

    Optional<DocumentVersion> findByIdAndDocumentId(UUID id, UUID documentId);

    /** Newest first, keyset on version_no. */
    List<DocumentVersion> findByDocumentIdOrderByVersionNoDesc(UUID documentId, Pageable pageable);

    List<DocumentVersion> findByDocumentIdAndVersionNoLessThanOrderByVersionNoDesc(UUID documentId, int before,
                                                                                   Pageable pageable);
}