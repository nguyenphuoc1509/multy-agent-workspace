package com.workspace.credential.infrastructure;

import com.workspace.credential.domain.LlmCredential;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LlmCredentialRepository extends JpaRepository<LlmCredential, UUID> {

    List<LlmCredential> findByProjectIdOrderByCreatedAtDescIdDesc(UUID projectId);

    boolean existsByProjectIdAndKeyFingerprintAndRevokedAtIsNull(UUID projectId, String keyFingerprint);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from LlmCredential c where c.id = :id")
    Optional<LlmCredential> lockById(@Param("id") UUID id);
}
