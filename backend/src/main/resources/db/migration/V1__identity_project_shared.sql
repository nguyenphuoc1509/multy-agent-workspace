-- V1: identity, project, shared trigger functions (from BE brief section 5)
CREATE TABLE app_user (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  email text NOT NULL UNIQUE,
  password_hash text NOT NULL,
  display_name text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE project (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL,
  description text,
  git_repo_url text,
  daily_token_budget bigint CHECK (daily_token_budget IS NULL OR daily_token_budget > 0),
  created_by uuid NOT NULL REFERENCES app_user(id),
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE project_member (
  project_id uuid NOT NULL REFERENCES project(id),
  user_id uuid NOT NULL REFERENCES app_user(id),
  role text NOT NULL CHECK (role IN ('OWNER','APPROVER','VIEWER')),
  PRIMARY KEY (project_id, user_id)
);
CREATE FUNCTION forbid_mutation() RETURNS trigger AS $$
BEGIN RAISE EXCEPTION 'table % is append-only', TG_TABLE_NAME; END $$ LANGUAGE plpgsql;
CREATE FUNCTION forbid_delete() RETURNS trigger AS $$
BEGIN RAISE EXCEPTION 'delete not allowed on %', TG_TABLE_NAME; END $$ LANGUAGE plpgsql;
