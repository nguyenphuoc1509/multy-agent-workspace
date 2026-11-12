package com.workspace.audit.infrastructure;

import com.workspace.audit.published.AuditLog;
import com.workspace.shared.published.SimpleJson;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class JdbcAuditLog implements AuditLog {

    private final JdbcTemplate jdbc;

    JdbcAuditLog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID projectId, String actorType, String actorId, String action,
                       String entityType, UUID entityId, Map<String, Object> before, Map<String, Object> after) {
        jdbc.update("""
                insert into audit_log (project_id, actor_type, actor_id, action, entity_type, entity_id,
                                       before_json, after_json)
                values (?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb))
                """,
                projectId, actorType, actorId, action, entityType, entityId,
                before == null ? null : SimpleJson.write(before),
                after == null ? null : SimpleJson.write(after));
    }
}