package com.workspace.audit.published;

import java.util.Map;
import java.util.UUID;

/**
 * Append-only audit trail. Must be called inside the transaction that makes the change
 * (rule 8): calling it without a transaction fails. Never pass secrets or full document bodies.
 */
public interface AuditLog {

    String ACTOR_USER = "USER";
    String ACTOR_SYSTEM = "SYSTEM";   // actorId "system" for event consumers

    void record(UUID projectId, String actorType, String actorId, String action,
                String entityType, UUID entityId, Map<String, Object> before, Map<String, Object> after);
}