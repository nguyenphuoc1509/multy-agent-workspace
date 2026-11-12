# ADR-005: conversation, run and context dependencies added in M3

Status: Accepted (M3a)

## Context
Brief section 4 lists the M3 modules and their dependencies. Implementing them needs a few more edges, all
pointing "down" the existing order (no cycles).

## Decision
- `conversation` may depend on `identity` (controllers) and `project` (membership checks).
- Sending a message creates a run in the SAME transaction (brief section 10), and `run` depends on `conversation`,
  so the send use case lives in `run` and calls the `ConversationWriter` port. Computed conversation fields
  (active run, staleness) are supplied by `run` through the `ConversationDecorator` port defined in `conversation`.
- Further edges are recorded in this ADR when added: `run -> project` (daily token budget), `run -> identity`.

## Consequences
Rule 6 stays intact (`run`, `llm`, `context`, `conversation` never reach `workflow`). Ports are used only where a
dependency would otherwise point the wrong way.
