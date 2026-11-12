-- V5: tasks ----------------------------------------------------------
CREATE TABLE task (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id uuid NOT NULL REFERENCES project(id),
  title text NOT NULL,
  description text NOT NULL,
  acceptance_criteria text,
  role_id uuid NOT NULL,
  assigned_agent_id uuid,                    -- preferred agent, optional; actual agent is on agent_run
  status text NOT NULL DEFAULT 'TODO' CHECK (status IN
    ('TODO','BLOCKED','RUNNING','NEEDS_REVIEW','DONE','NEEDS_RERUN')),
  accepted_run_id uuid,                      -- FK added in V8
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (id, project_id),
  FOREIGN KEY (role_id, project_id) REFERENCES role_definition (id, project_id),
  FOREIGN KEY (assigned_agent_id, project_id) REFERENCES agent (id, project_id)
);
CREATE TABLE task_dependency (
  task_id uuid NOT NULL REFERENCES task(id),
  depends_on_task_id uuid NOT NULL REFERENCES task(id),
  PRIMARY KEY (task_id, depends_on_task_id),
  CHECK (task_id <> depends_on_task_id)
);   -- the service layer must reject cycles and cross-project dependencies
