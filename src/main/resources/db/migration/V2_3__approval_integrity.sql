-- V2.3: approval must reference the exact content hash of its version; one decision per approver per gate;
-- submitted_at becomes immutable once the version leaves DRAFT; emails stored lowercase.
ALTER TABLE document_version
  ADD CONSTRAINT uq_version_id_hash UNIQUE (id, content_hash);
ALTER TABLE approval
  ADD CONSTRAINT fk_approval_hash
  FOREIGN KEY (document_version_id, content_hash)
  REFERENCES document_version (id, content_hash);
ALTER TABLE approval
  ADD CONSTRAINT uq_approval_once UNIQUE (document_version_id, gate_code, approver_id);
ALTER TABLE app_user
  ADD CONSTRAINT app_user_email_lower CHECK (email = lower(email));

CREATE OR REPLACE FUNCTION guard_document_version() RETURNS trigger AS $$
BEGIN
  IF NEW.document_id IS DISTINCT FROM OLD.document_id
     OR NEW.version_no IS DISTINCT FROM OLD.version_no
     OR NEW.author_type IS DISTINCT FROM OLD.author_type
     OR NEW.author_ref IS DISTINCT FROM OLD.author_ref
     OR NEW.parent_version_id IS DISTINCT FROM OLD.parent_version_id
     OR NEW.source_message_id IS DISTINCT FROM OLD.source_message_id
     OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
    RAISE EXCEPTION 'immutable column changed';
END IF;
  IF OLD.status = 'DRAFT' THEN
    IF NEW.status NOT IN ('DRAFT','IN_REVIEW','DISCARDED') THEN
      RAISE EXCEPTION 'illegal transition from DRAFT'; END IF;
ELSE
    IF NEW.content IS DISTINCT FROM OLD.content
       OR NEW.content_hash IS DISTINCT FROM OLD.content_hash
       OR NEW.change_summary IS DISTINCT FROM OLD.change_summary
       OR NEW.submitted_at IS DISTINCT FROM OLD.submitted_at THEN
      RAISE EXCEPTION 'content is immutable after submit'; END IF;
    IF NEW.status <> OLD.status AND (OLD.status, NEW.status) NOT IN
       (('IN_REVIEW','APPROVED'),('IN_REVIEW','REJECTED'),('APPROVED','SUPERSEDED')) THEN
      RAISE EXCEPTION 'illegal status transition % -> %', OLD.status, NEW.status; END IF;
END IF;
RETURN NEW;
END $$ LANGUAGE plpgsql;
