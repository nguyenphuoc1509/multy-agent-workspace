-- V2.2 (early, ADR-001): version_comment from brief V8, WITHOUT the agent_run FK.
-- V8 will add: ALTER TABLE version_comment ADD CONSTRAINT fk_comment_run
--   FOREIGN KEY (agent_run_id) REFERENCES agent_run(id);
CREATE TABLE version_comment (
                                 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                 document_version_id uuid NOT NULL REFERENCES document_version(id),
                                 author_type text NOT NULL CHECK (author_type IN ('HUMAN','AGENT')),
                                 author_ref text NOT NULL,
                                 agent_run_id uuid,
                                 body text NOT NULL,
                                 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX version_comment_by_version ON version_comment (document_version_id, created_at, id);
CREATE TRIGGER trg_version_comment_append_only BEFORE UPDATE OR DELETE ON version_comment
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();