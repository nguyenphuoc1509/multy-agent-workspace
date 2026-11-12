package com.workspace.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.workspace.support.IntegrationTestBase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** M2c: agent CRUD, same-project safety for role and credential, audit. Real PostgreSQL. */
class AgentApiTest extends IntegrationTestBase {

    private static final String PW = "correct-horse-battery";

    private String createProject(Auth a) throws Exception {
        return JsonPath.read(mvc.perform(as(post("/api/v1/projects"), a).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"P\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String roleId(Auth a, String pid, String code) throws Exception {
        String body = mvc.perform(get("/api/v1/projects/" + pid + "/roles").session(a.session()))
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$.items[?(@.code=='" + code + "')].id");
        return ids.get(0);
    }

    private String credential(Auth a, String pid, String key) throws Exception {
        return JsonPath.read(mvc.perform(as(post("/api/v1/projects/" + pid + "/credentials"), a)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"k\",\"provider\":\"ANTHROPIC\",\"apiKey\":\"%s\"}".formatted(key)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private ResultActions create(Auth a, String pid, String roleId, String name, String credentialId) throws Exception {
        String cred = credentialId == null ? "" : ",\"credentialId\":\"" + credentialId + "\"";
        return mvc.perform(as(post("/api/v1/projects/" + pid + "/agents"), a).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleId\":\"%s\",\"name\":\"%s\",\"provider\":\"ANTHROPIC\",\"model\":\"claude-sonnet-5-5\",\"params\":{\"maxOutputTokens\":2000}%s}"
                        .formatted(roleId, name, cred)));
    }

    private ResultActions patchAgent(Auth a, String id, String json) throws Exception {
        return mvc.perform(as(patch("/api/v1/agents/" + id), a).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    @Test
    void createListGetAndAudit() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String pid = createProject(o);
        String cred = credential(o, pid, "sk-ant-agent-key-0001");

        String body = create(o, pid, roleId(o, pid, "BACKEND"), "backend-1", cred).andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("backend-1"))
                .andExpect(jsonPath("$.provider").value("ANTHROPIC"))
                .andExpect(jsonPath("$.executorType").value("CHAT"))
                .andExpect(jsonPath("$.model").value("claude-sonnet-5-5"))
                .andExpect(jsonPath("$.params.maxOutputTokens").value(2000))
                .andExpect(jsonPath("$.credentialId").value(cred))
                .andExpect(jsonPath("$.isEnabled").value(true))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        create(o, pid, roleId(o, pid, "BACKEND"), "backend-2", null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.credentialId").value((Object) null)); // several agents may share a role

        mvc.perform(get("/api/v1/projects/" + pid + "/agents").session(o.session()))
                .andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.items[0].name").value("backend-1"));
        mvc.perform(get("/api/v1/agents/" + id).session(o.session())).andExpect(status().isOk());
        assertThat(count("select count(*) from audit_log where action = 'AGENT_CREATED' and project_id = ?", UUID.fromString(pid))).isEqualTo(2);
    }

    @Test
    void roleAndCredentialMustBelongToTheSameProject() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String p1 = createProject(o);
        String p2 = createProject(o);

        create(o, p1, roleId(o, p2, "QA"), "x", null).andExpect(status().isBadRequest());
        create(o, p1, UUID.randomUUID().toString(), "x", null).andExpect(status().isBadRequest());
        String otherCred = credential(o, p2, "sk-ant-other-proj-0002");
        create(o, p1, roleId(o, p1, "QA"), "x", otherCred).andExpect(status().isBadRequest());
        create(o, p1, roleId(o, p1, "QA"), "x", UUID.randomUUID().toString()).andExpect(status().isBadRequest());

        String revoked = credential(o, p1, "sk-ant-revoked-0003");
        mvc.perform(as(post("/api/v1/credentials/" + revoked + "/revoke"), o)).andExpect(status().isOk());
        create(o, p1, roleId(o, p1, "QA"), "x", revoked).andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("CREDENTIAL_UNUSABLE"));
        assertThat(count("select count(*) from agent where project_id = ?", UUID.fromString(p1))).isZero();

        // the database enforces the same rule even if the application did not
        UUID foreignRole = UUID.fromString(roleId(o, p2, "QA"));
        assertThatThrownBy(() -> jdbc.update(
                "insert into agent(project_id, role_id, name, provider, model) values (?, ?, 'raw', 'ANTHROPIC', 'm')",
                UUID.fromString(p1), foreignRole)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void inputIsValidatedAndNamesAreUniquePerProject() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String pid = createProject(o);
        String role = roleId(o, pid, "QA");
        create(o, pid, role, "dup", null).andExpect(status().isCreated());
        create(o, pid, role, "dup", null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_AGENT"));
        create(o, pid, role, "  ", null).andExpect(status().isBadRequest());
        mvc.perform(as(post("/api/v1/projects/" + pid + "/agents"), o).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":\"%s\",\"name\":\"n\",\"provider\":\"OPENAI\",\"model\":\"m\"}".formatted(role)))
                .andExpect(status().isBadRequest());
        mvc.perform(as(post("/api/v1/projects/" + pid + "/agents"), o).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":\"%s\",\"name\":\"n\",\"provider\":\"ANTHROPIC\",\"model\":\"m\",\"params\":{\"maxOutputTokens\":0}}".formatted(role)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchChangesOnlyGivenFieldsAndIsAudited() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String pid = createProject(o);
        String role = roleId(o, pid, "REVIEWER");
        String id = JsonPath.read(create(o, pid, role, "rev", null).andReturn().getResponse().getContentAsString(), "$.id");
        create(o, pid, role, "other", null).andExpect(status().isCreated());
        String cred = credential(o, pid, "sk-ant-patch-key-0004");

        patchAgent(o, id, "{\"isEnabled\":false}").andExpect(status().isOk())
                .andExpect(jsonPath("$.isEnabled").value(false)).andExpect(jsonPath("$.name").value("rev"))
                .andExpect(jsonPath("$.model").value("claude-sonnet-5-5"));
        patchAgent(o, id, "{\"name\":\"renamed\",\"model\":\"claude-opus-5-5\",\"credentialId\":\"" + cred + "\",\"params\":{\"maxRunSeconds\":60}}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("renamed"))
                .andExpect(jsonPath("$.model").value("claude-opus-5-5")).andExpect(jsonPath("$.credentialId").value(cred))
                .andExpect(jsonPath("$.params.maxRunSeconds").value(60));
        patchAgent(o, id, "{\"name\":\"other\"}").andExpect(status().isConflict());
        patchAgent(o, id, "{\"name\":\"  \"}").andExpect(status().isBadRequest());
        patchAgent(o, UUID.randomUUID().toString(), "{}").andExpect(status().isNotFound());

        assertThat(count("select count(*) from audit_log where action = 'AGENT_UPDATED' and entity_id = ?", UUID.fromString(id))).isEqualTo(2);
        assertThat(jdbc.queryForObject("select before_json->>'name' from audit_log where action = 'AGENT_UPDATED' and entity_id = ? and after_json->>'name' = 'renamed'",
                String.class, UUID.fromString(id))).isEqualTo("rev");
    }

    @Test
    void onlyOwnersManageAgents_outsidersSeeNotFound() throws Exception {
        createUser("o@example.com", PW);
        UUID viewerId = createUser("v@example.com", PW);
        createUser("x@example.com", PW);
        Auth o = login("o@example.com", PW);
        Auth v = login("v@example.com", PW);
        Auth x = login("x@example.com", PW);
        String pid = createProject(o);
        jdbc.update("insert into project_member(project_id, user_id, role) values (?, ?, 'VIEWER')", UUID.fromString(pid), viewerId);
        String role = roleId(o, pid, "QA");
        String id = JsonPath.read(create(o, pid, role, "qa", null).andReturn().getResponse().getContentAsString(), "$.id");

        create(v, pid, role, "n", null).andExpect(status().isForbidden());
        create(x, pid, role, "n", null).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/projects/" + pid + "/agents").session(v.session())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/projects/" + pid + "/agents").session(x.session())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/agents/" + id).session(x.session())).andExpect(status().isNotFound());
        patchAgent(v, id, "{\"isEnabled\":false}").andExpect(status().isForbidden());
        patchAgent(x, id, "{\"isEnabled\":false}").andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/projects/" + pid + "/agents")).andExpect(status().isUnauthorized());
    }
}
