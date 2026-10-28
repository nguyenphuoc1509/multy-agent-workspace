package com.workspace.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.workspace.audit.published.AuditLog;
import com.workspace.event.published.EventPublisher;
import com.workspace.support.IntegrationTestBase;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

class AuditAndOutboxTest extends IntegrationTestBase {

    @Autowired AuditLog audit;
    @Autowired EventPublisher events;
    @Autowired TransactionTemplate tx;

    private int count(String table, UUID projectId) {
        return jdbc.queryForObject("select count(*) from " + table + " where project_id = ?", Integer.class, projectId);
    }

    private void writeBoth(UUID project, UUID entity) {
        audit.record(project, AuditLog.ACTOR_USER, "u1", "VERSION_APPROVED", "DOCUMENT_VERSION", entity,
                Map.of("status", "IN_REVIEW"), Map.of("status", "APPROVED"));
        events.publish(project, "DOCUMENT", entity, "DocumentVersionApproved", AuditLog.ACTOR_USER, "u1",
                Map.of("versionNo", 2));
    }

    @Test
    void bothRequireAnExistingTransaction() {
        UUID p = UUID.randomUUID();
        assertThatThrownBy(() -> writeBoth(p, UUID.randomUUID())).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(count("audit_log", p)).isZero();
        assertThat(count("outbox_event", p)).isZero();
    }

    @Test
    void auditAndEventAreWrittenInTheCallersTransaction() {
        UUID p = UUID.randomUUID();
        UUID entity = UUID.randomUUID();
        tx.executeWithoutResult(s -> writeBoth(p, entity));

        assertThat(count("audit_log", p)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select after_json->>'status' from audit_log where project_id = ?",
                String.class, p)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("select before_json->>'status' from audit_log where project_id = ?",
                String.class, p)).isEqualTo("IN_REVIEW");

        assertThat(count("outbox_event", p)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select payload->>'type' from outbox_event where project_id = ?",
                String.class, p)).isEqualTo("DocumentVersionApproved");
        assertThat(jdbc.queryForObject("select payload->'payload'->>'versionNo' from outbox_event where project_id = ?",
                String.class, p)).isEqualTo("2");
        assertThat(jdbc.queryForObject("select seq is not null and processed_at is null from outbox_event where project_id = ?",
                Boolean.class, p)).isTrue();
    }

    @Test
    void rollbackLeavesNeitherAuditNorEvent() {
        UUID p = UUID.randomUUID();
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            writeBoth(p, UUID.randomUUID());
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(count("audit_log", p)).isZero();
        assertThat(count("outbox_event", p)).isZero();
    }

    @Test
    void auditLogIsAppendOnly() {
        UUID p = UUID.randomUUID();
        tx.executeWithoutResult(s -> writeBoth(p, UUID.randomUUID()));
        assertThatThrownBy(() -> jdbc.update("update audit_log set action = 'x' where project_id = ?", p))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from audit_log where project_id = ?", p))
                .hasMessageContaining("append-only");
    }

    @Test
    void onlyProcessedAtMayChangeOnOutboxEvents() {
        UUID p = UUID.randomUUID();
        tx.executeWithoutResult(s -> writeBoth(p, UUID.randomUUID()));
        assertThatThrownBy(() -> jdbc.update("update outbox_event set payload = '{}'::jsonb where project_id = ?", p))
                .hasMessageContaining("only processed_at may change");
        assertThatThrownBy(() -> jdbc.update("delete from outbox_event where project_id = ?", p))
                .hasMessageContaining("delete not allowed");
        jdbc.update("update outbox_event set processed_at = now() where project_id = ?", p);
    }
}