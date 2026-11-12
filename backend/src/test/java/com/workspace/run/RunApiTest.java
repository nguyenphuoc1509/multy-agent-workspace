package com.workspace.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** M3c: send -> queued run -> worker -> result, with idempotency, cancel/retry, blocks, failures and budget. */
class RunApiTest extends IntegrationTestBase {

    private static final String PW = "correct-horse-battery";
    private static final String KEY = "sk-ant-test-RUNKEY-1234";

    private record Ctx(Auth owner, String pid, String agent, String conv, String cred) {}

    private String createProject(Auth a, String body) throws Exception {
        return JsonPath.read(mvc.perform(as(post("/api/v1/projects"), a).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String credential(Auth a, String pid) throws Exception {
        return JsonPath.read(mvc.perform(as(post("/api/v1/projects/" + pid + "/credentials"), a)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"k\",\"provider\":\"ANTHROPIC\",\"apiKey\":\"" + KEY + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String agent(Auth a, String pid, String credId) throws Exception {
        String roles = mvc.perform(get("/api/v1/projects/" + pid + "/roles").session(a.session()))
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(roles, "$.items[?(@.code=='QA')].id");
        String cred = credId == null ? "" : ",\"credentialId\":\"" + credId + "\"";
        return JsonPath.read(mvc.perform(as(post("/api/v1/projects/" + pid + "/agents"), a)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":\"%s\",\"name\":\"qa\",\"provider\":\"ANTHROPIC\",\"model\":\"m\"%s}".formatted(ids.get(0), cred)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String conversation(Auth a, String agentId) throws Exception {
        return JsonPath.read(mvc.perform(as(post("/api/v1/agents/" + agentId + "/conversations"), a))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private Ctx setup(boolean withCredential) throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String pid = createProject(o, "{\"name\":\"P\"}");
        String cred = withCredential ? credential(o, pid) : null;
        String ag = agent(o, pid, cred);
        return new Ctx(o, pid, ag, conversation(o, ag), cred);
    }

    private MvcResult send(Auth a, String cid, String text, UUID requestId) throws Exception {
        return mvc.perform(as(post("/api/v1/conversations/" + cid + "/messages"), a).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"%s\",\"clientRequestId\":\"%s\"}".formatted(text, requestId))).andReturn();
    }

    private String sendOk(Ctx c, String text) throws Exception {
        MvcResult r = send(c.owner(), c.conv(), text, UUID.randomUUID());
        assertThat(r.getResponse().getStatus()).isEqualTo(202);
        return JsonPath.read(r.getResponse().getContentAsString(), "$.run.id");
    }

    private String awaitTerminal(Auth a, String runId) throws Exception {
        for (int i = 0; i < 200; i++) {
            String body = mvc.perform(get("/api/v1/runs/" + runId).session(a.session())).andReturn().getResponse().getContentAsString();
            String st = JsonPath.read(body, "$.status");
            if (!st.equals("QUEUED") && !st.equals("RUNNING")) return body;
            Thread.sleep(50);
        }
        throw new AssertionError("run did not finish: " + runId);
    }

    private void awaitStatus(Auth a, String runId, String status) throws Exception {
        for (int i = 0; i < 200; i++) {
            String body = mvc.perform(get("/api/v1/runs/" + runId).session(a.session())).andReturn().getResponse().getContentAsString();
            if (status.equals(JsonPath.read(body, "$.status"))) return;
            Thread.sleep(50);
        }
        throw new AssertionError("run never reached " + status);
    }

    @Test
    void sendRunsToCompletion_appendsTheAgentMessage_andCountsUsage() throws Exception {
        Ctx c = setup(true);
        String runId = sendOk(c, "hello");
        String done = awaitTerminal(c.owner(), runId);
        assertThat((String) JsonPath.read(done, "$.status")).isEqualTo("SUCCEEDED");
        assertThat((Integer) JsonPath.read(done, "$.outputTokens")).isPositive();
        assertThat((String) JsonPath.read(done, "$.snapshotId")).isNotNull();
        assertThat((String) JsonPath.read(done, "$.messageId")).isNotNull();

        mvc.perform(get("/api/v1/conversations/" + c.conv() + "/messages").session(c.owner().session()))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].role").value("USER"))
                .andExpect(jsonPath("$.items[1].role").value("AGENT"))
                .andExpect(jsonPath("$.items[1].content").value("[fake] You said: hello"))
                .andExpect(jsonPath("$.items[1].agentRunId").value(runId));
        mvc.perform(get("/api/v1/conversations/" + c.conv()).session(c.owner().session()))
                .andExpect(jsonPath("$.activeRunId").value((Object) null))
                .andExpect(jsonPath("$.lastSeq").value(2));
        mvc.perform(get("/api/v1/projects/" + c.pid() + "/usage").session(c.owner().session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outputTokens").value(org.hamcrest.Matchers.greaterThan(0)));
        mvc.perform(get("/api/v1/projects/" + c.pid() + "/runs").session(c.owner().session()))
                .andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get("/api/v1/runs/" + runId + "/snapshot").session(c.owner().session()))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from outbox_event where event_type = 'AgentRunSucceeded'", Integer.class)).isEqualTo(1);
    }

    @Test
    void sameClientRequestIdIsIdempotent() throws Exception {
        Ctx c = setup(true);
        UUID rid = UUID.randomUUID();
        MvcResult first = send(c.owner(), c.conv(), "hi", rid);
        assertThat(first.getResponse().getStatus()).isEqualTo(202);
        MvcResult again = send(c.owner(), c.conv(), "hi", rid);
        assertThat(again.getResponse().getStatus()).isEqualTo(200);
        assertThat((String) JsonPath.read(again.getResponse().getContentAsString(), "$.run.id"))
                .isEqualTo(JsonPath.read(first.getResponse().getContentAsString(), "$.run.id"));
        assertThat((String) JsonPath.read(again.getResponse().getContentAsString(), "$.message.id"))
                .isEqualTo(JsonPath.read(first.getResponse().getContentAsString(), "$.message.id"));
        awaitTerminal(c.owner(), JsonPath.read(first.getResponse().getContentAsString(), "$.run.id"));
        assertThat(jdbc.queryForObject("select count(*) from agent_run where conversation_id = ?::uuid", Integer.class, c.conv())).isEqualTo(1);
    }

    @Test
    void oneActiveRunPerConversation_cancelFreesIt() throws Exception {
        Ctx c = setup(true);
        String runId = sendOk(c, "[slow] go");
        awaitStatus(c.owner(), runId, "RUNNING");
        MvcResult second = send(c.owner(), c.conv(), "again", UUID.randomUUID());
        assertThat(second.getResponse().getStatus()).isEqualTo(409);
        assertThat((String) JsonPath.read(second.getResponse().getContentAsString(), "$.code")).isEqualTo("ACTIVE_RUN_EXISTS");
        assertThat(jdbc.queryForObject("select count(*) from message where conversation_id = ?::uuid", Integer.class, c.conv()))
                .isEqualTo(1); // the rejected message was rolled back

        mvc.perform(as(post("/api/v1/runs/" + runId + "/cancel"), c.owner())).andExpect(status().isAccepted());
        String done = awaitTerminal(c.owner(), runId);
        assertThat((String) JsonPath.read(done, "$.status")).isEqualTo("CANCELLED");
        assertThat(send(c.owner(), c.conv(), "after", UUID.randomUUID()).getResponse().getStatus()).isEqualTo(202);
    }

    @Test
    void agentWithoutCredentialIsBlocked_thenRetryAfterFixingIt() throws Exception {
        Ctx c = setup(false);
        String runId = sendOk(c, "hi");
        String done = awaitTerminal(c.owner(), runId);
        assertThat((String) JsonPath.read(done, "$.status")).isEqualTo("BLOCKED");
        assertThat((String) JsonPath.read(done, "$.blockReason")).isEqualTo("CREDENTIAL_REVOKED");

        String cred = credential(c.owner(), c.pid());
        mvc.perform(as(patch("/api/v1/agents/" + c.agent()), c.owner()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"credentialId\":\"" + cred + "\"}")).andExpect(status().isOk());
        MvcResult retry = mvc.perform(as(post("/api/v1/runs/" + runId + "/retry"), c.owner())).andExpect(status().isAccepted()).andReturn();
        String newRun = JsonPath.read(retry.getResponse().getContentAsString(), "$.id");
        assertThat(newRun).isNotEqualTo(runId);
        assertThat((String) JsonPath.read(awaitTerminal(c.owner(), newRun), "$.status")).isEqualTo("SUCCEEDED");
        mvc.perform(as(post("/api/v1/runs/" + newRun + "/retry"), c.owner())).andExpect(status().isConflict());
    }

    @Test
    void revokedCredentialBlocksAndIsNeverReplaced() throws Exception {
        Ctx c = setup(true);
        mvc.perform(as(post("/api/v1/credentials/" + c.cred() + "/revoke"), c.owner())).andExpect(status().isOk());
        String done = awaitTerminal(c.owner(), sendOk(c, "hi"));
        assertThat((String) JsonPath.read(done, "$.blockReason")).isEqualTo("CREDENTIAL_REVOKED");
    }

    @Test
    void disabledAgentIsBlocked() throws Exception {
        Ctx c = setup(true);
        mvc.perform(as(patch("/api/v1/agents/" + c.agent()), c.owner()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"isEnabled\":false}")).andExpect(status().isOk());
        String done = awaitTerminal(c.owner(), sendOk(c, "hi"));
        assertThat((String) JsonPath.read(done, "$.blockReason")).isEqualTo("AGENT_DISABLED");
    }

    @Test
    void providerFailures() throws Exception {
        Ctx c = setup(true);
        String permanent = awaitTerminal(c.owner(), sendOk(c, "[fail-permanent]"));
        assertThat((String) JsonPath.read(permanent, "$.status")).isEqualTo("FAILED");
        assertThat((String) JsonPath.read(permanent, "$.errorCode")).isEqualTo("PROVIDER_BAD_REQUEST");

        String transientFail = awaitTerminal(c.owner(), sendOk(c, "[fail-transient]"));
        assertThat((String) JsonPath.read(transientFail, "$.status")).isEqualTo("FAILED");
        assertThat((String) JsonPath.read(transientFail, "$.errorCode")).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat((Integer) JsonPath.read(transientFail, "$.attempt")).isEqualTo(4);

        String once = awaitTerminal(c.owner(), sendOk(c, "[fail-once]"));
        assertThat((String) JsonPath.read(once, "$.status")).isEqualTo("SUCCEEDED");
        assertThat((Integer) JsonPath.read(once, "$.attempt")).isEqualTo(2);
    }

    @Test
    void dailyBudgetBlocksTheRun() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String pid = createProject(o, "{\"name\":\"P\",\"dailyTokenBudget\":1}");
        String ag = agent(o, pid, credential(o, pid));
        Ctx c = new Ctx(o, pid, ag, conversation(o, ag), null);
        String done = awaitTerminal(o, sendOk(c, "hi"));
        assertThat((String) JsonPath.read(done, "$.blockReason")).isEqualTo("BUDGET_EXCEEDED");
    }

    @Test
    void viewersReadButCannotSend_outsidersSeeNotFound() throws Exception {
        Ctx c = setup(true);
        UUID viewer = createUser("v@example.com", PW);
        jdbc.update("insert into project_member(project_id, user_id, role) values (?, ?, 'VIEWER')", UUID.fromString(c.pid()), viewer);
        createUser("x@example.com", PW);
        Auth v = login("v@example.com", PW);
        Auth x = login("x@example.com", PW);
        String runId = sendOk(c, "hi");
        awaitTerminal(c.owner(), runId);

        assertThat(send(v, c.conv(), "no", UUID.randomUUID()).getResponse().getStatus()).isEqualTo(403);
        mvc.perform(get("/api/v1/runs/" + runId).session(v.session())).andExpect(status().isOk());
        mvc.perform(as(post("/api/v1/runs/" + runId + "/retry"), v)).andExpect(status().isForbidden());
        assertThat(send(x, c.conv(), "no", UUID.randomUUID()).getResponse().getStatus()).isEqualTo(404);
        mvc.perform(get("/api/v1/runs/" + runId).session(x.session())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/projects/" + c.pid() + "/usage").session(x.session())).andExpect(status().isNotFound());
    }

    @Test
    void streamReplaysTheTerminalEventToALateSubscriber() throws Exception {
        Ctx c = setup(true);
        String runId = sendOk(c, "hi");
        awaitTerminal(c.owner(), runId);
        MvcResult r = mvc.perform(get("/api/v1/runs/" + runId + "/stream").session(c.owner().session())
                .accept(MediaType.TEXT_EVENT_STREAM)).andReturn();
        String body = r.getRequest().isAsyncStarted()
                ? mvc.perform(asyncDispatch(r)).andReturn().getResponse().getContentAsString()
                : r.getResponse().getContentAsString();
        assertThat(body).contains("event:run.completed").contains("\"messageId\"");
    }

    @Test
    void validation() throws Exception {
        Ctx c = setup(true);
        assertThat(mvc.perform(as(post("/api/v1/conversations/" + c.conv() + "/messages"), c.owner())
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"  \",\"clientRequestId\":\"" + UUID.randomUUID() + "\"}"))
                .andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(mvc.perform(as(post("/api/v1/conversations/" + c.conv() + "/messages"), c.owner())
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"x\"}"))
                .andReturn().getResponse().getStatus()).isEqualTo(400);
    }
}
