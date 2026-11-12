-- V3: LLM credentials (brief section 5, V3). The key is stored only encrypted (AES-256-GCM).
CREATE TABLE llm_credential (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id uuid NOT NULL REFERENCES project(id),
  label text NOT NULL,
  provider text NOT NULL CHECK (provider IN ('ANTHROPIC')),  -- extend only when an adapter exists
  encrypted_key bytea NOT NULL,                              -- nonce || ciphertext+tag
  key_version int NOT NULL,                                  -- master key version used
  key_last4 text NOT NULL,
  key_fingerprint text NOT NULL,                             -- one-way hash, duplicate detection only
  created_by uuid NOT NULL REFERENCES app_user(id),
  created_at timestamptz NOT NULL DEFAULT now(),
  revoked_at timestamptz,
  UNIQUE (id, project_id)
);
-- Addition to the brief: the same key cannot be active twice in one project.
CREATE UNIQUE INDEX uq_credential_active_fingerprint
  ON llm_credential (project_id, key_fingerprint) WHERE revoked_at IS NULL;
