-- V2.4 (ADR-002, step 1c): a draft created from a version other than the current approved one
-- (rollback to an older version, or re-edit of a rejected version) has a parent that is NOT the approved
-- pointer. The flag records that explicitly so the approve transaction can accept it (brief section 6, step 4).
ALTER TABLE document_version ADD COLUMN is_rollback boolean NOT NULL DEFAULT false;

CREATE OR REPLACE FUNCTION guard_document_version() RETURNS trigger AS $$
BEGIN
  IF NEW.document_id IS DISTINCT FROM OLD.document_id
     OR NEW.version_no IS DISTINCT FROM OLD.version_no
     OR NEW.author_type IS DISTINCT FROM OLD.author_type
     OR NEW.author_ref IS DISTINCT FROM OLD.author_ref
     OR NEW.parent_version_id IS DISTINCT FROM OLD.parent_version_id
     OR NEW.source_message_id IS DISTINCT FROM OLD.source_message_id
     OR NEW.is_rollback IS DISTINCT FROM OLD.is_rollback
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
