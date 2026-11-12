package com.workspace.document.web;

import com.workspace.document.application.ApprovalView;
import com.workspace.document.domain.Decision;
import java.time.Instant;
import java.util.UUID;

public record ApprovalResponse(UUID id, UUID documentVersionId, String gateCode, UUID approverId, Decision decision,
                               String contentHash, String comment, Instant decidedAt) {
    static ApprovalResponse from(ApprovalView a) {
        return new ApprovalResponse(a.id(), a.documentVersionId(), a.gateCode(), a.approverId(), a.decision(),
                a.contentHash(), a.comment(), a.decidedAt());
    }
}
