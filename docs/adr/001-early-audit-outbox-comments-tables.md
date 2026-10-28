# ADR-001: create audit/outbox/comment tables in M1

- Status: proposed (waiting for owner)
- Context: M1 requires "audit + outbox written in the same transaction" and comments on versions, but the
  brief defines audit_log/outbox_event in V9 and version_comment in V8 (which references agent_run).
- Options:
    1. Early migrations V2.1 / V2.2 (chosen). Flyway sorts 2 < 2.1 < 2.2 < 3, so V3..V8 keep their numbers.
       V8 later adds the agent_run FK to version_comment; V9 keeps only attention_item.
       Cost: the migration order differs slightly from the brief.
    2. Implement M1 without audit/outbox/comments. Violates rule 8 and the M1 acceptance criteria.
    3. Renumber every later migration. Churn for no benefit.
- Decision: option 1. Added one index on version_comment (document_version_id, created_at, id)
  for keyset paging; no constraints or triggers from the brief were dropped.