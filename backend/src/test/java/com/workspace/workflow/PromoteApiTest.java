package com.workspace.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.workspace.support.IntegrationTestBase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** M3d: promote an AGENT message to a DRAFT version, never to an approved one. */
class PromoteApiTest extends IntegrationTestBase {

    private static final String PW = "correct-horse-battery";

    private String read(ResultActions r, String path) throws Exception {
        return JsonPath.read(r.andReturn().getResponse().getContentAsString(), path);
    }

    private String agent(Auth a, String pid, String cred, String role) throws Exception {
        String roles = mvc.perform(get("/api/v1/projects/" + pid + "/roles").session(a.session())).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(roles, "$.items[?(@.code=='" + role + "')].id");
        return read(mvc.perform(as(post("/api/v1/projects/" + pid + "/agents"), a).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleId\":\"%s\",\"name\":\"%s\",\"provider\":\"ANTHROPIC\",\"model\":\"m\",\"credentialId\":\"%s\"}"
                        .formatted(ids.get(0), role.toLowerCase(), cred))).andExpect(status().isCreated()), "$.id");
    }

    /** Runs one chat turn and returns {userMessageId, agentMessageId}. */
    private String[] chat(Auth a, String agentId) throws Exception {
        String cid = read(mvc.perform(as(post("/api/v1/agents/" + agentId + "/conversations"), a)).andExpect(status().isCreated()), "$.id");
        String runId = read(mvc.perform(as(post("/api/v1/conversations/" + cid + "/messages"), a).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"# Plan\\nuse layers\",\"clientRequestId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isAccepted()), "$.run.id");
        for (int i = 0; i < 200; i++) {
            String st = read(mvc.perform(get("/api/v1/runs/" + runId).session(a.session())), "$.status");
            if (st.equals("SUCCEEDED")) break;
            assertThat(st).isIn("QUEUED", "RUNNING");
            Thread.sleep(50);
        }
        String msgs = mvc.perform(get("/api/v1/conversations/" + cid + "/messages").session(a.session())).andReturn().getResponse().getContentAsString();
        return new String[] {JsonPath.read(msgs, "$.items[0].id"), JsonPath.read(msgs, "$.items[1].id")};
    }

    private ResultActions promote(Auth a, String mid, String body) throws Exception {
        return mvc.perform(as(post("/api/v1/messages/" + mid + "/promote"), a).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void promoteCreatesAnAgentAuthoredDraft_andNeverTouchesTheApprovedPointer() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String pid = read(mvc.perform(as(post("/api/v1/projects"), o).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"P\"}")), "$.id");
        String cred = read(mvc.perform(as(post("/api/v1/projects/" + pid + "/credentials"), o).contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\":\"k\",\"provider\":\"ANTHROPIC\",\"apiKey\":\"sk-ant-test-PROMOTE-1\"}")), "$.id");
        String architect = agent(o, pid, cred, "ARCHITECT");
        String[] m = chat(o, architect);

        String body = promote(o, m[1], "{\"target\":{\"newDocument\":{\"type\":\"ADR\",\"title\":\"Layering\"}},\"changeSummary\":\"from chat\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        String did = JsonPath.read(body, "$.documentId");
        String vid = JsonPath.read(body, "$.versionId");
        mvc.perform(get("/api/v1/document-versions/" + vid).session(o.session())).andExpect(status().isOk())
                .andExpect(jsonPath("$.authorType").value("AGENT"))
                .andExpect(jsonPath("$.authorRef").value(architect))
                .andExpect(jsonPath("$.sourceMessageId").value(m[1]))
                .andExpect(jsonPath("$.content").value("[fake] You said: # Plan\nuse layers"));
        assertThat(jdbc.queryForObject("select current_approved_version_id from document where id = ?::uuid", UUID.class, did)).isNull();

        // same document again: one open version rule
        promote(o, m[1], "{\"target\":{\"documentId\":\"" + did + "\"}}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPEN_VERSION_EXISTS"));
        // the role may not propose REQUIREMENTS
        promote(o, m[1], "{\"target\":{\"newDocument\":{\"type\":\"REQUIREMENTS\",\"title\":\"x\"}}}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROLE_CANNOT_PROPOSE"));
        // USER messages cannot be promoted; target needs exactly one choice
        promote(o, m[0], "{\"target\":{\"newDocument\":{\"type\":\"ADR\",\"title\":\"x\"}}}").andExpect(status().isBadRequest());
        promote(o, m[1], "{\"target\":{}}").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'MESSAGE_PROMOTED' and entity_id = ?::uuid",
                Integer.class, m[1])).isEqualTo(1);

        String backend = agent(o, pid, cred, "BACKEND");
        String[] b = chat(o, backend);
        promote(o, b[1], "{\"target\":{\"newDocument\":{\"type\":\"ADR\",\"title\":\"x\"}}}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROLE_CANNOT_PROPOSE"));

        UUID viewer = createUser("v@example.com", PW);
        jdbc.update("insert into project_member(project_id, user_id, role) values (?, ?, 'VIEWER')", UUID.fromString(pid), viewer);
        createUser("x@example.com", PW);
        promote(login("v@example.com", PW), m[1], "{\"target\":{\"newDocument\":{\"type\":\"ADR\",\"title\":\"x\"}}}").andExpect(status().isForbidden());
        promote(login("x@example.com", PW), m[1], "{\"target\":{\"newDocument\":{\"type\":\"ADR\",\"title\":\"x\"}}}").andExpect(status().isNotFound());
    }
}
