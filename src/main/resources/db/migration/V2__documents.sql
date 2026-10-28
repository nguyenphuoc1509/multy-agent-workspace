-- V2: documents, versions, approvals (from BE brief section 5, unchanged)
CREATE TABLE document (
                          id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                          project_id uuid NOT NULL REFERENCES project(id),
                          type text NOT NULL CHECK (type IN ('REQUIREMENTS','ARCHITECTURE','API_CONTRACT','ADR')),
                          title text NOT NULL,
                          current_approved_version_id uuid,
                          created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE document_version (
                                  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                  document_id uuid NOT NULL REFERENCES document(id),
                                  version_no int NOT NULL,
                                  content text NOT NULL,
                                  content_hash text NOT NULL,
                                  status text NOT NULL CHECK (status IN
                                                              ('DRAFT','IN_REVIEW','APPROVED','REJECTED','SUPERSEDED','DISCARDED')),
                                  author_type text NOT NULL CHECK (author_type IN ('HUMAN','AGENT')),
                                  author_ref text NOT NULL,
                                  parent_version_id uuid REFERENCES document_version(id),
                                  change_summary text,
                                  source_message_id uuid,                    -- FK added in V7
                                  created_at timestamptz NOT NULL DEFAULT now(),
                                  submitted_at timestamptz,
                                  UNIQUE (document_id, version_no),
                                  UNIQUE (id, document_id)
);
CREATE UNIQUE INDEX one_open_version_per_document
    ON document_version (document_id) WHERE status IN ('DRAFT','IN_REVIEW');
ALTER TABLE document ADD CONSTRAINT fk_current_approved
    FOREIGN KEY (current_approved_version_id, id)
        REFERENCES document_version (id, document_id) DEFERRABLE INITIALLY DEFERRED;
CREATE TABLE approval (
                          id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                          document_version_id uuid NOT NULL REFERENCES document_version(id),
                          gate_code text NOT NULL,
                          approver_id uuid NOT NULL REFERENCES app_user(id),
                          decision text NOT NULL CHECK (decision IN ('APPROVED','REJECTED')),
                          content_hash text NOT NULL,
                          comment text,
                          decided_at timestamptz NOT NULL DEFAULT now()
);
CREATE FUNCTION guard_document_version() RETURNS trigger AS $$
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
       OR NEW.change_summary IS DISTINCT FROM OLD.change_summary THEN
      RAISE EXCEPTION 'content is immutable after submit'; END IF;
    IF NEW.status <> OLD.status AND (OLD.status, NEW.status) NOT IN
       (('IN_REVIEW','APPROVED'),('IN_REVIEW','REJECTED'),('APPROVED','SUPERSEDED')) THEN
      RAISE EXCEPTION 'illegal status transition % -> %', OLD.status, NEW.status; END IF;
END IF;
RETURN NEW;
END $$ LANGUAGE plpgsql;
CREATE TRIGGER trg_guard_document_version BEFORE UPDATE ON document_version
    FOR EACH ROW EXECUTE FUNCTION guard_document_version();
CREATE FUNCTION guard_document_pointer() RETURNS trigger AS $$
BEGIN
  IF NEW.current_approved_version_id IS NOT NULL AND NOT EXISTS (
     SELECT 1 FROM document_version v
     WHERE v.id = NEW.current_approved_version_id AND v.status = 'APPROVED') THEN
    RAISE EXCEPTION 'current_approved_version_id must reference an APPROVED version';
END IF;
RETURN NEW;
END $$ LANGUAGE plpgsql;
CREATE TRIGGER trg_guard_document_pointer
    BEFORE INSERT OR UPDATE OF current_approved_version_id ON document
    FOR EACH ROW EXECUTE FUNCTION guard_document_pointer();
CREATE TRIGGER trg_approval_append_only BEFORE UPDATE OR DELETE ON approval
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER trg_document_no_delete BEFORE DELETE ON document
    FOR EACH ROW EXECUTE FUNCTION forbid_delete();
CREATE TRIGGER trg_document_version_no_delete BEFORE DELETE ON document_version
    FOR EACH ROW EXECUTE FUNCTION forbid_delete();