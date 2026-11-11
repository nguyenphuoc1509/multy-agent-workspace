package com.workspace.document.application;

/** Result of approve / reject. supersededVersionId is null for a reject or for the first approval. */
public record DecisionResult(VersionView version, java.util.UUID supersededVersionId, ApprovalView approval) {}
