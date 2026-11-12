package com.workspace.event.infrastructure;

import com.workspace.event.published.EventPublisher;
import com.workspace.shared.published.SimpleJson;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class OutboxEventPublisher implements EventPublisher {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    OutboxEventPublisher(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(UUID projectId, String aggregateType, UUID aggregateId, String eventType,
                        String actorType, String actorId, Map<String, Object> payload) {
        UUID id = UUID.randomUUID();

        Map<String, Object> actor = new LinkedHashMap<>();
        actor.put("type", actorType);
        actor.put("id", actorId);

        // envelope from brief section 11; correlationId stays null until a correlation source exists
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", id);
        envelope.put("type", eventType);
        envelope.put("eventVersion", 1);
        envelope.put("projectId", projectId);
        envelope.put("occurredAt", clock.instant());
        envelope.put("actor", actor);
        envelope.put("correlationId", null);
        envelope.put("payload", payload);

        jdbc.update("""
                insert into outbox_event (id, project_id, aggregate_type, aggregate_id, event_type,
                                          event_version, payload, actor)
                values (?, ?, ?, ?, ?, 1, cast(? as jsonb), ?)
                """,
                id, projectId, aggregateType, aggregateId, eventType, SimpleJson.write(envelope), actorId);
    }
}