package com.workspace.event.published;

import java.util.Map;
import java.util.UUID;

/**
 * Transactional outbox: the event row is inserted in the SAME transaction as the change.
 * Calling it without a transaction fails.
 */
public interface EventPublisher {

    void publish(UUID projectId, String aggregateType, UUID aggregateId, String eventType,
                 String actorType, String actorId, Map<String, Object> payload);
}