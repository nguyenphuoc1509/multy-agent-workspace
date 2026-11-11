# ADR-003: credential may depend on llm, event and identity

Status: Accepted (M2a)

## Context
Brief section 4 lists `credential -> project, audit`. Brief section 8 requires the server to validate a new key through
`LlmProvider.validate()` before storing it, and to emit `CredentialRevoked` on revoke. Controllers need the current user
(same situation as ADR-002).

## Decision
`credential` may depend on `llm`, `event` and `identity` (through their `published` packages), in addition to
`project`, `audit` and `shared`. The ArchUnit matrix is updated accordingly. `llm` stays dependency-free (shared only),
so the cycle rules and rule 6 are unaffected.

## Alternatives
Validate the key in a higher module (for example `agent`) and pass the result in: it would let a credential module
store unvalidated keys if called from elsewhere, and spreads one rule over two modules.

## Consequences
Key validation and storage live in one place. The "invalid key stores nothing" guarantee is testable in one service.
