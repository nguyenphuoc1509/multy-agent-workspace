package com.workspace.run.domain;

/** CREDENTIAL_REVOKED also covers "the agent has no credential" (brief: credential present and not revoked). */
public enum BlockReason {
    MISSING_APPROVED_DOC, CONTEXT_TOO_LARGE, CREDENTIAL_REVOKED, BUDGET_EXCEEDED, AGENT_DISABLED, RUN_LIMIT_EXCEEDED
}
