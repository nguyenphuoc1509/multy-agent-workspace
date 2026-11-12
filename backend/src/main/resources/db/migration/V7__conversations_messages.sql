-- V7: conversations and messages -------------------------------------
CREATE TABLE conversation (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id uuid NOT NULL REFERENCES project(id),
  agent_id uuid NOT NULL,
  task_id uuid,                              -- set for the auto-created conversation of a task
  title text NOT NULL,
  last_seq int NOT NULL DEFAULT 0,
  archived_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  FOREIGN KEY (agent_id, project_id) REFERENCES agent (id, project_id),
  FOREIGN KEY (task_id, project_id) REFERENCES task (id, project_id)
);
CREATE UNIQUE INDEX one_conversation_per_task ON conversation (task_id) WHERE task_id IS NOT NULL;
CREATE TABLE message (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  conversation_id uuid NOT NULL REFERENCES conversation(id),
  seq int NOT NULL,
  role text NOT NULL CHECK (role IN ('USER','AGENT','SYSTEM')),
  content text NOT NULL,
  agent_run_id uuid,                         -- FK added in V8
  client_request_id uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (conversation_id, seq)
);
CREATE UNIQUE INDEX message_idempotency ON message (conversation_id, client_request_id)
  WHERE client_request_id IS NOT NULL;
CREATE TRIGGER trg_message_append_only BEFORE UPDATE OR DELETE ON message
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
ALTER TABLE document_version ADD CONSTRAINT fk_source_message
  FOREIGN KEY (source_message_id) REFERENCES message(id);
