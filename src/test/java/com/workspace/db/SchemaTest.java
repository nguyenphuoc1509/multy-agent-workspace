package com.workspace.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.workspace.support.IntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** V1 constraints are part of the product (brief section 14: real PostgreSQL, no H2). */
class SchemaTest extends IntegrationTestBase {

    @Test
    void flywayAppliedV1() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where version = '1' and success", Integer.class);
        assertThat(applied).isEqualTo(1);
        Integer fns = jdbc.queryForObject(
                "select count(*) from pg_proc where proname in ('forbid_mutation','forbid_delete')", Integer.class);
        assertThat(fns).isEqualTo(2);
    }

    @Test
    void dailyTokenBudgetMustBePositiveWhenSet() {
        UUID user = createUser("schema1@example.com", "correct-horse-battery");
        assertThatThrownBy(() -> jdbc.update(
                "insert into project(name, daily_token_budget, created_by) values ('p', 0, ?)", user))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("insert into project(name, daily_token_budget, created_by) values ('p', null, ?)", user);
        jdbc.update("insert into project(name, daily_token_budget, created_by) values ('q', 1, ?)", user);
    }

    @Test
    void projectMemberRoleIsConstrained() {
        UUID user = createUser("schema2@example.com", "correct-horse-battery");
        UUID project = UUID.randomUUID();
        jdbc.update("insert into project(id, name, created_by) values (?, 'p', ?)", project, user);
        assertThatThrownBy(() -> jdbc.update(
                "insert into project_member(project_id, user_id, role) values (?, ?, 'ADMIN')", project, user))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("insert into project_member(project_id, user_id, role) values (?, ?, 'OWNER')", project, user);
    }

    @Test
    void emailIsUnique() {
        createUser("dup@example.com", "correct-horse-battery");
        assertThatThrownBy(() -> createUser("dup@example.com", "correct-horse-battery"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
