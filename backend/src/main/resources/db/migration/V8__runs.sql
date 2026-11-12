-- V8: runs and version comments --------------------------------------
CREATE TABLE agent_run (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id uuid NOT NULL REFERENCES project(id),
  agent_id uuid NOT NULL,
  role_config_version_id uuid NOT NULL REFERENCES role_config_version(id),
  credential_id uuid,
  conversation_id uuid REFERENCES conversation(id),
  task_id uuid,
  trigger_message_id uuid REFERENCES message(id),
  context_snapshot_id uuid UNIQUE REFERENCES context_snapshot(id),
  model text NOT NULL,                       -- values actually used (agent.model may change later)
  params jsonb NOT NULL,
  status text NOT NULL CHECK (status IN
    ('QUEUED','RUNNING','SUCCEEDED','FAILED','BLOCKED','CANCELLED')),
  block_reason text CHECK (block_reason IN ('MISSING_APPROVED_DOC','CONTEXT_TOO_LARGE',
    'CREDENTIAL_REVOKED','BUDGET_EXCEEDED','AGENT_DISABLED','RUN_LIMIT_EXCEEDED')),
  error_code text,
  attempt int NOT NULL DEFAULT 0,
  input_tokens int, output_tokens int,
  created_at timestamptz NOT NULL DEFAULT now(),
  started_at timestamptz, finished_at timestamptz,
  FOREIGN KEY (agent_id, project_id) REFERENCES agent (id, project_id),
  FOREIGN KEY (credential_id, project_id) REFERENCES llm_credential (id, project_id),
  FOREIGN KEY (task_id, project_id) REFERENCES task (id, project_id),
  CHECK (conversation_id IS NOT NULL OR task_id IS NOT NULL),
  CHECK ((status = 'BLOCKED') = (block_reason IS NOT NULL))
);
CREATE UNIQUE INDEX one_active_run_per_conversation ON agent_run (conversation_id)
  WHERE conversation_id IS NOT NULL AND status IN ('QUEUED','RUNNING');
CREATE INDEX agent_run_queue ON agent_run (status, created_at);
CREATE INDEX agent_run_usage ON agent_run (project_id, created_at);
ALTER TABLE message ADD CONSTRAINT fk_message_run FOREIGN KEY (agent_run_id) REFERENCES agent_run(id);
ALTER TABLE task ADD CONSTRAINT fk_task_accepted_run FOREIGN KEY (accepted_run_id) REFERENCES agent_run(id);
CREATE TRIGGER trg_agent_run_no_delete BEFORE DELETE ON agent_run
  FOR EACH ROW EXECUTE FUNCTION forbid_delete();
-- version_comment already exists (V2.2); complete it now that agent_run exists
ALTER TABLE version_comment ADD CONSTRAINT fk_comment_run FOREIGN KEY (agent_run_id) REFERENCES agent_run(id);
