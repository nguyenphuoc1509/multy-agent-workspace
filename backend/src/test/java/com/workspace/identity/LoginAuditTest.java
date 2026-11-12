package com.workspace.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.workspace.support.IntegrationTestBase;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Step 1e: LOGIN_SUCCEEDED / LOGIN_FAILED are audited, never with a password, and throttled attempts are not. */
class LoginAuditTest extends IntegrationTestBase {

    private static final String PW = "correct-horse-battery";

    private ResultActions attempt(Cookie csrf, String email, String password) throws Exception {
        return mvc.perform(withCsrf(post("/api/v1/auth/login"), csrf).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    @Test
    void successfulLoginIsAudited() throws Exception {
        UUID id = createUser("audit-ok@example.com", PW);
        login("audit-ok@example.com", PW);
        assertThat(count("select count(*) from audit_log where action = 'LOGIN_SUCCEEDED' and entity_id = ? and actor_id = ?",
                id, id.toString())).isEqualTo(1);
    }

    @Test
    void wrongPasswordIsAuditedAgainstTheUser_withoutTheSecret() throws Exception {
        UUID id = createUser("audit-bad@example.com", PW);
        attempt(freshCsrf(), "audit-bad@example.com", "wrong-secret-123").andExpect(status().isUnauthorized());

        assertThat(count("select count(*) from audit_log where action = 'LOGIN_FAILED' and entity_id = ? and actor_type = 'USER'",
                id)).isEqualTo(1);
        String reason = jdbc.queryForObject(
                "select after_json->>'reason' from audit_log where action = 'LOGIN_FAILED' and entity_id = ?",
                String.class, id);
        assertThat(reason).isEqualTo("INVALID_CREDENTIALS");
        assertThat(count("select count(*) from audit_log where entity_id = ? and (after_json::text like '%wrong-secret%' or actor_id like '%wrong-secret%')",
                id)).isZero();
    }

    @Test
    void unknownEmailIsAuditedAsAnonymous() throws Exception {
        String email = "ghost-" + UUID.randomUUID() + "@example.com";
        attempt(freshCsrf(), email, "whatever-123").andExpect(status().isUnauthorized());
        assertThat(count("select count(*) from audit_log where action = 'LOGIN_FAILED' and actor_type = 'ANONYMOUS' and actor_id = ? and entity_id is null",
                email)).isEqualTo(1);
    }

    @Test
    void throttledAttemptsAreNotAudited() throws Exception {
        UUID id = createUser("audit-throttle@example.com", PW);
        Cookie csrf = freshCsrf();
        for (int i = 0; i < 5; i++) {
            attempt(csrf, "audit-throttle@example.com", "wrong-wrong-wrong").andExpect(status().isUnauthorized());
        }
        attempt(csrf, "audit-throttle@example.com", PW).andExpect(status().isTooManyRequests());
        assertThat(count("select count(*) from audit_log where action = 'LOGIN_FAILED' and entity_id = ?", id))
                .isEqualTo(5);
        assertThat(count("select count(*) from audit_log where action = 'LOGIN_SUCCEEDED' and entity_id = ?", id))
                .isZero();
    }
}
