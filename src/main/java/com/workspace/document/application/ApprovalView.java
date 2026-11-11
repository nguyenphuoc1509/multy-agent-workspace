package com.workspace.document.application;

import com.workspace.document.domain.Approval;
import com.workspace.document.domain.Decision;
import java.time.Instant;
import java.util.UUID;

public record ApprovalView(UUID id, UUID documentVersionId, String gateCode, UUID approverId, Decision decision,
                           String contentHash, String comment, Instant decidedAt) {
    static ApprovalView of(Approval a) {
        return new ApprovalView(a.getId(), a.getDocumentVersionId(), a.getGateCode(), a.getApproverId(),
                a.getDecision(), a.getContentHash(), a.getComment(), a.getDecidedAt());
    }
}
