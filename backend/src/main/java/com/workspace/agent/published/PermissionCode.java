package com.workspace.agent.published;

/** There is no APPROVE, DELETE or DEPLOY permission and there must never be (brief section 7). */
public enum PermissionCode {
    READ_DOCUMENT, PROPOSE_DOCUMENT, COMMENT_ON_VERSION, READ_REPO, EXPORT_PATCH, PROPOSE_TASK
}
