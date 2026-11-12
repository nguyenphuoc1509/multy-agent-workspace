-- V6: context snapshots (immutable) ----------------------------------
CREATE TABLE context_snapshot (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  assembler_version text NOT NULL,
  token_estimate int NOT NULL,
  last_message_seq int,                      -- history cut-off, so a retry rebuilds the same prompt
  missing_document_types text[] NOT NULL DEFAULT '{}',
  dependency_run_ids uuid[] NOT NULL DEFAULT '{}',
  assembled_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE snapshot_item (
  snapshot_id uuid NOT NULL REFERENCES context_snapshot(id),
  document_version_id uuid NOT NULL REFERENCES document_version(id),
  content_hash text NOT NULL,
  inclusion_mode text NOT NULL CHECK (inclusion_mode IN ('FULL','SUMMARY')),
  PRIMARY KEY (snapshot_id, document_version_id)
);
CREATE TRIGGER trg_snapshot_append_only BEFORE UPDATE OR DELETE ON context_snapshot
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER trg_snapshot_item_append_only BEFORE UPDATE OR DELETE ON snapshot_item
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
