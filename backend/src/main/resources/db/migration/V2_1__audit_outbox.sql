-- V2.1 (early, ADR-001): audit_log + outbox_event + event_delivery, copied from brief V9.
-- attention_item stays in V9.
CREATE TABLE outbox_event (
                              id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                              seq bigserial UNIQUE,
                              project_id uuid NOT NULL,
                              aggregate_type text NOT NULL, aggregate_id uuid NOT NULL,
                              event_type text NOT NULL, event_version int NOT NULL DEFAULT 1,
                              payload jsonb NOT NULL,
                              correlation_id uuid, actor text,
                              created_at timestamptz NOT NULL DEFAULT now(),
                              processed_at timestamptz
);
CREATE INDEX outbox_unprocessed ON outbox_event (seq) WHERE processed_at IS NULL;
CREATE TABLE event_delivery (
                                event_id uuid NOT NULL REFERENCES outbox_event(id),
                                consumer text NOT NULL,
                                status text NOT NULL CHECK (status IN ('DONE','FAILED')),
                                attempts int NOT NULL DEFAULT 1,
                                last_error text,
                                processed_at timestamptz NOT NULL DEFAULT now(),
                                PRIMARY KEY (event_id, consumer)
);
CREATE TABLE audit_log (
                           id bigserial PRIMARY KEY,
                           project_id uuid,
                           actor_type text NOT NULL, actor_id text NOT NULL,
                           action text NOT NULL,
                           entity_type text NOT NULL, entity_id uuid,
                           before_json jsonb, after_json jsonb,
                           at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX audit_entity ON audit_log (project_id, entity_type, entity_id, id);
CREATE TRIGGER trg_audit_append_only BEFORE UPDATE OR DELETE ON audit_log
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE FUNCTION outbox_guard() RETURNS trigger AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN RAISE EXCEPTION 'delete not allowed on outbox_event'; END IF;
  IF (NEW.id, NEW.seq, NEW.project_id, NEW.aggregate_type, NEW.aggregate_id, NEW.event_type,
      NEW.event_version, NEW.payload, NEW.created_at)
     IS DISTINCT FROM
     (OLD.id, OLD.seq, OLD.project_id, OLD.aggregate_type, OLD.aggregate_id, OLD.event_type,
      OLD.event_version, OLD.payload, OLD.created_at) THEN
    RAISE EXCEPTION 'only processed_at may change on outbox_event'; END IF;
RETURN NEW;
END $$ LANGUAGE plpgsql;
CREATE TRIGGER trg_outbox_guard BEFORE UPDATE OR DELETE ON outbox_event
  FOR EACH ROW EXECUTE FUNCTION outbox_guard();