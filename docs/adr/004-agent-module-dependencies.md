# ADR-004: agent may depend on document and identity; default roles are seeded by an in-process event

Status: Accepted (M2b)

## Context
Brief section 4 lists `agent -> project, credential, audit, event`. Role context profiles name document types and
must be validated against `DocumentType`; controllers need the current user (see ADR-002, ADR-003). Default roles must
exist for every new project, but `project` may not depend on `agent` (that would create a cycle).

## Decision
- `agent` may depend on `document.published` (only `DocumentType` is used) and `identity.published`.
- `project.published` exposes `ProjectCreated`, a plain Spring application event published inside the project's
  transaction. `agent` listens (synchronously, same transaction) and seeds the five default roles. If anything fails,
  the project is not created. Projects created before M2b get their roles on the first `GET /projects/{pid}/roles`.

## Alternatives
- Seed from `project`: rejected, forbidden dependency and a cycle.
- Seed through the outbox event: rejected, asynchronous, so a project could briefly exist without roles.
- Duplicate the document type list inside `agent`: rejected, two sources of truth.

## Consequences
`agent -> document` is one more edge; `document` does not depend on `agent`, so no cycle. The seed runs in the same
transaction, so "5 default roles exist after creating a project" holds immediately.
