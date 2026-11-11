package com.workspace.credential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.workspace.support.IntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** M2a: credentials are write-only, validated before storing, encrypted at rest and OWNER-only. Real PostgreSQL. */
@ExtendWith(OutputCaptureExtension.class)
class CredentialApiTest extends IntegrationTestBase {

    private static final String PW = "correct-horse-battery";
    private static final String KEY = "sk-ant-test-SECRETVALUE-9876";

    private String createProject(Auth auth) throws Exception {
        String body = mvc.perform(as(post("/api/v1/projects"), auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"P\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions add(Auth a, String pid, String label, String provider, String key) throws Exception {
        return mvc.perform(as(post("/api/v1/projects/" + pid + "/credentials"), a)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\":\"%s\",\"provider\":\"%s\",\"apiKey\":\"%s\"}".formatted(label, provider, key)));
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    @Test
    void addedKeyIsEncryptedAndNeverReturnedOrAudited() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String pid = createProject(o);

        MvcResult r = add(o, pid, "main key", "ANTHROPIC", KEY).andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value("main key"))
                .andExpect(jsonPath("$.provider").value("ANTHROPIC"))
                .andExpect(jsonPath("$.keyLast4").value("9876"))
                .andExpect(jsonPath("$.revokedAt").value((Object) null))
                .andExpect(jsonPath("$.apiKey").doesNotExist())
                .andExpect(jsonPath("$.encryptedKey").doesNotExist())
                .andReturn();
        String body = r.getResponse().getContentAsString();
        assertThat(body).doesNotContain("SECRETVALUE");
        String id = JsonPath.read(body, "$.id");

        // stored ciphertext does not contain the plaintext, and the fingerprint is not the key
        assertThat(count("select position(convert_to(?, 'UTF8') in encrypted_key) from llm_credential where id = ?",
                "SECRETVALUE", UUID.fromString(id))).isZero();
        assertThat(count("select key_version from llm_credential where id = ?", UUID.fromString(id))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select key_fingerprint from llm_credential where id = ?", String.class,
                UUID.fromString(id))).hasSize(64).doesNotContain("SECRET");

        assertThat(count("select count(*) from audit_log where action = 'CREDENTIAL_ADDED' and entity_id = ?",
                UUID.fromString(id))).isEqualTo(1);
        assertThat(count("select count(*) from audit_log where entity_id = ? and (after_json::text like '%SECRETVALUE%' or before_json::text like '%SECRETVALUE%')",
                UUID.fromString(id))).isZero();

        String list = mvc.perform(get("/api/v1/projects/" + pid + "/credentials").session(o.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat(list).doesNotContain("SECRETVALUE").doesNotContain("encryptedKey").doesNotContain("apiKey");
    }

    @Test
    void rejectedKeyStoresNothing() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String pid = createProject(o);

        add(o, pid, "bad", "ANTHROPIC", "sk-bad-key-0000").andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PROVIDER_REJECTED_KEY"));
        add(o, pid, "down", "ANTHROPIC", "sk-down-key-0000").andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PROVIDER_UNAVAILABLE"));

        assertThat(count("select count(*) from llm_credential where project_id = ?", UUID.fromString(pid))).isZero();
        assertThat(count("select count(*) from audit_log where action = 'CREDENTIAL_ADDED' and project_id = ?",
                UUID.fromString(pid))).isZero();
    }

    @Test
    void inputIsValidated() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String pid = createProject(o);
        add(o, pid, "x", "OPENAI", KEY).andExpect(status().isBadRequest());
        add(o, pid, "x", "ANTHROPIC", "short").andExpect(status().isBadRequest());
        add(o, pid, "  ", "ANTHROPIC", KEY).andExpect(status().isBadRequest());
        assertThat(count("select count(*) from llm_credential where project_id = ?", UUID.fromString(pid))).isZero();
    }

    @Test
    void sameActiveKeyCannotBeAddedTwice_butAfterRevokeItCan() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String pid = createProject(o);
        String id = JsonPath.read(add(o, pid, "a", "ANTHROPIC", KEY).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");

        add(o, pid, "b", "ANTHROPIC", KEY).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_CREDENTIAL"));

        mvc.perform(as(post("/api/v1/credentials/" + id + "/revoke"), o)).andExpect(status().isOk());
        add(o, pid, "c", "ANTHROPIC", KEY).andExpect(status().isCreated());
    }

    @Test
    void revokeSetsTimestampOnceAuditsAndEmitsEvent() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String pid = createProject(o);
        String id = JsonPath.read(add(o, pid, "a", "ANTHROPIC", KEY).andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(as(post("/api/v1/credentials/" + id + "/revoke"), o)).andExpect(status().isOk())
                .andExpect(jsonPath("$.revokedAt").isNotEmpty());
        mvc.perform(as(post("/api/v1/credentials/" + id + "/revoke"), o)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        assertThat(count("select count(*) from audit_log where action = 'CREDENTIAL_REVOKED' and entity_id = ?",
                UUID.fromString(id))).isEqualTo(1);
        assertThat(count("select count(*) from outbox_event where aggregate_id = ? and payload->>'type' = 'CredentialRevoked'",
                UUID.fromString(id))).isEqualTo(1);
        mvc.perform(as(post("/api/v1/credentials/" + UUID.randomUUID() + "/revoke"), o)).andExpect(status().isNotFound());
    }

    @Test
    void onlyOwnersMayUseCredentials_outsidersSeeNotFound() throws Exception {
        createUser("o@example.com", PW);
        UUID approverId = createUser("a@example.com", PW);
        UUID viewerId = createUser("v@example.com", PW);
        createUser("x@example.com", PW);
        Auth o = login("o@example.com", PW);
        Auth a = login("a@example.com", PW);
        Auth v = login("v@example.com", PW);
        Auth x = login("x@example.com", PW);
        String pid = createProject(o);
        jdbc.update("insert into project_member(project_id, user_id, role) values (?, ?, 'APPROVER')", UUID.fromString(pid), approverId);
        jdbc.update("insert into project_member(project_id, user_id, role) values (?, ?, 'VIEWER')", UUID.fromString(pid), viewerId);
        String id = JsonPath.read(add(o, pid, "a", "ANTHROPIC", KEY).andReturn().getResponse().getContentAsString(), "$.id");

        add(a, pid, "n", "ANTHROPIC", "sk-ant-other-1111").andExpect(status().isForbidden());
        add(v, pid, "n", "ANTHROPIC", "sk-ant-other-1111").andExpect(status().isForbidden());
        add(x, pid, "n", "ANTHROPIC", "sk-ant-other-1111").andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/projects/" + pid + "/credentials").session(a.session())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/projects/" + pid + "/credentials").session(x.session())).andExpect(status().isNotFound());
        mvc.perform(as(post("/api/v1/credentials/" + id + "/revoke"), v)).andExpect(status().isForbidden());
        mvc.perform(as(post("/api/v1/credentials/" + id + "/revoke"), x)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/projects/" + pid + "/credentials")).andExpect(status().isUnauthorized());
    }

    @Test
    void theKeyNeverReachesTheLogs(CapturedOutput output) throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String pid = createProject(o);
        add(o, pid, "ok", "ANTHROPIC", KEY).andExpect(status().isCreated());
        add(o, pid, "dup", "ANTHROPIC", KEY).andExpect(status().isConflict());
        add(o, pid, "bad", "ANTHROPIC", "sk-bad-SECRETVALUE-0001").andExpect(status().isBadGateway());
        add(o, pid, "down", "ANTHROPIC", "sk-down-SECRETVALUE-0002").andExpect(status().isBadGateway());
        add(o, pid, "huge", "ANTHROPIC", "k".repeat(600) + "SECRETVALUE").andExpect(status().isBadRequest());
        // malformed JSON containing the key must not be echoed either
        mvc.perform(as(post("/api/v1/projects/" + pid + "/credentials"), o).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"x\",\"apiKey\":\"" + KEY + "\""))
                .andExpect(status().isBadRequest());

        assertThat(output.getAll()).doesNotContain("SECRETVALUE");
    }
}
