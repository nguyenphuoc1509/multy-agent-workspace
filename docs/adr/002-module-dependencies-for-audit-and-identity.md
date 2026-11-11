# ADR-002: allow project -> audit, document -> identity, identity -> audit

Status: accepted (owner, 2026-11-11). Supersedes the dependency matrix of brief section 4 for these two edges only.

Context: brief M1 requires audit rows in the same transaction as every change, including PROJECT_CREATED (brief
section 13). The matrix lets `project` depend only on `identity` and `shared`. Document endpoints need the caller's id
(`CurrentUserProvider`, published by `identity`), but the matrix does not let `document` depend on `identity`.

Decision: add `audit` to the allowed set of `project`, and `identity` to the allowed set of `document`.
Only the `published` packages of those modules are used (ArchUnit rule 2 still applies). No cycle is created:
`audit` and `identity` depend only on `shared`.

Consequences: later modules that serve HTTP (`credential`, `agent`, `conversation`, `task`, ...) will need the same
`identity` edge; add each one with a one-line change to `ArchitectureTest.ALLOWED` when the module is created.

Open point for step 1c (found while reading the brief): section 6 says rollback creates a draft whose parent is an
OLDER version, but the approve transaction asserts `version.parent_version_id == document.current_approved_version_id`.
A rollback draft would therefore always fail approval. Proposed default for 1c: relax the assertion to
"the parent is the current approved version OR the version was created by rollback", stored explicitly; to be decided
before 1c starts.

Amendment (step 1e): `identity` may also depend on `audit` (published API only), so that LOGIN_SUCCEEDED and
LOGIN_FAILED are written by the identity module (brief section 13). `audit` still depends only on `shared`, so there
is no cycle. Login audit rows are written in their own transaction (`LoginAuditor`) because a failed login throws.

