package com.workspace.document.web;

import com.workspace.document.application.DecisionResult;
import java.util.UUID;

public record DecisionResponse(VersionResponse version, UUID supersededVersionId, ApprovalResponse approval) {
    static DecisionResponse from(DecisionResult r) {
        return new DecisionResponse(VersionResponse.from(r.version()), r.supersededVersionId(),
                ApprovalResponse.from(r.approval()));
    }
}
