package com.workspace.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.workspace.conversation.published.ConversationWriter;
import com.workspace.conversation.published.MessageInfo;
import com.workspace.support.IntegrationTestBase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** M3a: conversations, ordered/idempotent messages, access rules. Real PostgreSQL. */
class ConversationApiTest extends IntegrationTestBase {

    private static final String PW = "correct-horse-battery";

    @Autowired ConversationWriter writer;
    @Autowired PlatformTransactionManager txm;

    private String createProject(Auth a) throws Exception {
        return JsonPath.read(mvc.perform(as(post("/api/v1/projects"), a).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"P\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String createAgent(Auth a, String pid, String name) throws Exception {
        String roles = mvc.perform(get("/api/v1/projects/" + pid + "/roles").session(a.session()))
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(roles, "$.items[?(@.code=='QA')].id");
        return JsonPath.read(mvc.perform(as(post("/api/v1/projects/" + pid + "/agents"), a)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":\"%s\",\"name\":\"%s\",\"provider\":\"ANTHROPIC\",\"model\":\"m\"}".formatted(ids.get(0), name)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String createConversation(Auth a, String agentId, String title) throws Exception {
        return JsonPath.read(mvc.perform(as(post("/api/v1/agents/" + agentId + "/conversations"), a)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private <T> T inTx(java.util.function.Supplier<T> work) {
        return new TransactionTemplate(txm).execute(s -> work.get());
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    @Test
    void createListAndGet() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String pid = createProject(o);
        String agent = createAgent(o, pid, "qa-1");

        mvc.perform(as(post("/api/v1/agents/" + agent + "/conversations"), o).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  Plan  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Plan"))
                .andExpect(jsonPath("$.agentId").value(agent))
                .andExpect(jsonPath("$.lastSeq").value(0))
                .andExpect(jsonPath("$.stale").value(false))
                .andExpect(jsonPath("$.staleDocumentTypes.length()").value(0))
                .andExpect(jsonPath("$.activeRunId").value((Object) null));
        mvc.perform(as(post("/api/v1/agents/" + agent + "/conversations"), o)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("New conversation")); // no body at all is fine

        mvc.perform(get("/api/v1/projects/" + pid + "/conversations?limit=1").session(o.session()))
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.nextCursor").isNotEmpty());
        mvc.perform(get("/api/v1/projects/" + pid + "/conversations").session(o.session()))
                .andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.nextCursor").value((Object) null));
        mvc.perform(get("/api/v1/projects/" + pid + "/conversations?cursor=%25%25").session(o.session()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/projects/" + pid + "/conversations?agentId=" + UUID.randomUUID()).session(o.session()))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void membersReadButOnlyOwnersCreate_outsidersSeeNotFound() throws Exception {
        createUser("o@example.com", PW);
        UUID viewerId = createUser("v@example.com", PW);
        createUser("x@example.com", PW);
        Auth o = login("o@example.com", PW);
        Auth v = login("v@example.com", PW);
        Auth x = login("x@example.com", PW);
        String pid = createProject(o);
        jdbc.update("insert into project_member(project_id, user_id, role) values (?, ?, 'VIEWER')", UUID.fromString(pid), viewerId);
        String agent = createAgent(o, pid, "qa-1");
        String cid = createConversation(o, agent, "c");

        mvc.perform(as(post("/api/v1/agents/" + agent + "/conversations"), v)).andExpect(status().isForbidden());
        mvc.perform(as(post("/api/v1/agents/" + agent + "/conversations"), x)).andExpect(status().isNotFound());
        mvc.perform(as(post("/api/v1/agents/" + UUID.randomUUID() + "/conversations"), o)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/conversations/" + cid).session(v.session())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/conversations/" + cid + "/messages").session(v.session())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/projects/" + pid + "/conversations").session(v.session())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/conversations/" + cid).session(x.session())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/conversations/" + cid + "/messages").session(x.session())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/projects/" + pid + "/conversations").session(x.session())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/conversations/" + cid)).andExpect(status().isUnauthorized());
    }

    @Test
    void messagesGetIncreasingSeq_areIdempotentPerClientRequestId_andPaginate() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String pid = createProject(o);
        UUID cid = UUID.fromString(createConversation(o, createAgent(o, pid, "qa-1"), "c"));
        UUID req = UUID.randomUUID();

        ConversationWriter.Appended a = inTx(() -> writer.appendUserMessage(cid, "one", req));
        ConversationWriter.Appended again = inTx(() -> writer.appendUserMessage(cid, "one (retry)", req));
        MessageInfo agentMsg = inTx(() -> writer.appendAgentMessage(cid, "reply", null));
        ConversationWriter.Appended b = inTx(() -> writer.appendUserMessage(cid, "two", null));

        assertThat(a.message().seq()).isEqualTo(1);
        assertThat(a.duplicate()).isFalse();
        assertThat(again.duplicate()).isTrue();
        assertThat(again.message().id()).isEqualTo(a.message().id());
        assertThat(again.message().content()).isEqualTo("one");
        assertThat(agentMsg.seq()).isEqualTo(2);
        assertThat(b.message().seq()).isEqualTo(3);
        assertThat(count("select last_seq from conversation where id = ?", cid)).isEqualTo(3);

        mvc.perform(get("/api/v1/conversations/" + cid).session(o.session())).andExpect(jsonPath("$.lastSeq").value(3));
        mvc.perform(get("/api/v1/conversations/" + cid + "/messages?limit=2").session(o.session()))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].role").value("USER"))
                .andExpect(jsonPath("$.items[1].role").value("AGENT"))
                .andExpect(jsonPath("$.nextCursor").value("2"));
        mvc.perform(get("/api/v1/conversations/" + cid + "/messages?afterSeq=2").session(o.session()))
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].content").value("two"))
                .andExpect(jsonPath("$.nextCursor").value((Object) null));
        mvc.perform(get("/api/v1/conversations/" + cid + "/messages?afterSeq=-1").session(o.session()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void messagesAreAppendOnly_andAppendingNeedsATransaction() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String pid = createProject(o);
        UUID cid = UUID.fromString(createConversation(o, createAgent(o, pid, "qa-1"), "c"));
        inTx(() -> writer.appendUserMessage(cid, "hello", null));

        assertThatThrownBy(() -> jdbc.update("update message set content = 'x' where conversation_id = ?", cid))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("delete from message where conversation_id = ?", cid))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> writer.appendUserMessage(cid, "no tx", null))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
