package com.workspace.document.infrastructure;

import com.workspace.document.domain.Approval;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalRepository extends JpaRepository<Approval, UUID> {
    List<Approval> findByDocumentVersionIdOrderByDecidedAtDesc(UUID documentVersionId);
}