package com.workspace.document.domain;

public enum VersionStatus {
    DRAFT, IN_REVIEW, APPROVED, REJECTED, SUPERSEDED, DISCARDED;

    /** A document may have at most one open version (DB partial unique index). */
    public boolean isOpen() {
        return this == DRAFT || this == IN_REVIEW;
    }
}