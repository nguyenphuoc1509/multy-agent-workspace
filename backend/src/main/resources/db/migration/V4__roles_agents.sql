-- V4: roles, immutable role config versions, permissions, agents (brief section 5, V4)
CREATE TABLE role_definition (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id uuid NOT NULL REFERENCES project(id),
  code text NOT NULL CHECK (code ~ '^[A-Z][A-Z0-9_]{1,39}$'),
  name text NOT NULL,
  is_builtin boolean NOT NULL DEFAULT false,
  active_config_version_id uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (project_id, code),
  UNIQUE (id, project_id)
);
CREATE TABLE role_config_version (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  role_id uuid NOT NULL REFERENCES role_definition(id),
  version_no int NOT NULL,
  system_prompt text NOT NULL,
  context_profile jsonb NOT NULL,
  created_by uuid NOT NULL REFERENCES app_user(id),
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (role_id, version_no),
  UNIQUE (id, role_id)
);
ALTER TABLE role_definition ADD CONSTRAINT fk_active_config
  FOREIGN KEY (active_config_version_id, id)
  REFERENCES role_config_version (id, role_id) DEFERRABLE INITIALLY DEFERRED;
CREATE TABLE role_permission (
  role_config_version_id uuid NOT NULL REFERENCES role_config_version(id),
  permission_code text NOT NULL CHECK (permission_code IN
    ('READ_DOCUMENT','PROPOSE_DOCUMENT','COMMENT_ON_VERSION',
     'READ_REPO','EXPORT_PATCH','PROPOSE_TASK')),
  resource_type text NOT NULL DEFAULT '*',
  PRIMARY KEY (role_config_version_id, permission_code, resource_type)
);
CREATE TABLE agent (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id uuid NOT NULL REFERENCES project(id),
  role_id uuid NOT NULL,
  name text NOT NULL,
  provider text NOT NULL CHECK (provider IN ('ANTHROPIC')),
  executor_type text NOT NULL DEFAULT 'CHAT' CHECK (executor_type IN ('CHAT')),
  model text NOT NULL,
  params jsonb NOT NULL DEFAULT '{}',
  credential_id uuid,
  is_enabled boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (project_id, name),
  UNIQUE (id, project_id),
  FOREIGN KEY (role_id, project_id) REFERENCES role_definition (id, project_id),
  FOREIGN KEY (credential_id, project_id) REFERENCES llm_credential (id, project_id)
);
CREATE TRIGGER trg_role_config_append_only BEFORE UPDATE OR DELETE ON role_config_version
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
-- Addition to the brief: permissions belong to an immutable config version, so they are append-only too.
CREATE TRIGGER trg_role_permission_append_only BEFORE UPDATE OR DELETE ON role_permission
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
