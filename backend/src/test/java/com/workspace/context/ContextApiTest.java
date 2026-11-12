package com.workspace.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.workspace.context.published.ContextAssembler;
import com.workspace.context.published.ContextAssembler.Assembled;
import com.workspace.conversation.published.ConversationWriter;
import com.workspace.document.domain.ContentHash;
import com.workspace.support.IntegrationTestBase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** M3b: context assembly from APPROVED documents only, immutable snapshots, faithful re-rendering, staleness. */
class ContextApiTest extends IntegrationTestBase {

    private static final String PW = "correct-horse-battery";

    @Autowired ContextAssembler assembler;
    @Autowired ConversationWriter writer;
    @Autowired PlatformTransactionManager txm;

    private String createProject(Auth a) throws Exception {
        return JsonPath.read(mvc.perform(as(post("/api/v1/projects"), a).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"P\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String agent(Auth a, String pid, String roleCode, String name, String params) throws Exception {
        String roles = mvc.perform(get("/api/v1/projects/" + pid + "/roles").session(a.session())).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(roles, "$.items[?(@.code=='" + roleCode + "')].id");
        return JsonPath.read(mvc.perform(as(post("/api/v1/projects/" + pid + "/agents"), a).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":\"%s\",\"name\":\"%s\",\"provider\":\"ANTHROPIC\",\"model\":\"m\",\"params\":%s}".formatted(ids.get(0), name, params)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private static String json(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    /** Creates a document, submits and approves its first version. Returns {documentId, versionId}. */
    private String[] approvedDoc(Auth a, String pid, String type, String content) throws Exception {
        MvcResult r = mvc.perform(as(post("/api/v1/projects/" + pid + "/documents"), a).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"%s\",\"title\":\"t\",\"content\":%s}".formatted(type, json(content))))
                .andExpect(status().isCreated()).andReturn();
        String did = JsonPath.read(r.getResponse().getContentAsString(), "$.id");
        String vid = JsonPath.read(r.getResponse().getContentAsString(), "$.openVersionId");
        approve(a, vid, content);
        return new String[] {did, vid};
    }

    private void approve(Auth a, String vid, String content) throws Exception {
        mvc.perform(as(post("/api/v1/document-versions/" + vid + "/submit"), a)).andExpect(status().isOk());
        mvc.perform(as(post("/api/v1/document-versions/" + vid + "/approve"), a).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedHash\":\"%s\"}".formatted(ContentHash.of(content)))).andExpect(status().isOk());
    }

    /** New draft from the approved pointer with new content, submitted and approved. Returns the new version id. */
    private String newApprovedVersion(Auth a, String did, String content) throws Exception {
        String vid = JsonPath.read(mvc.perform(as(post("/api/v1/documents/" + did + "/versions"), a).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":%s}".formatted(json(content)))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        approve(a, vid, content);
        return vid;
    }

    private UUID conversationWithMessage(Auth a, String agentId, String text) throws Exception {
        UUID cid = UUID.fromString(JsonPath.read(mvc.perform(as(post("/api/v1/agents/" + agentId + "/conversations"), a))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id"));
        new TransactionTemplate(txm).execute(s -> writer.appendUserMessage(cid, text, null));
        return cid;
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    @Test
    void previewShowsOnlyApprovedDocuments_andNeverBlocksAChatOnMissingOnes() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String pid = createProject(o);
        String ag = agent(o, pid, "BACKEND", "be", "{}");

        mvc.perform(get("/api/v1/agents/" + ag + "/context-preview").session(o.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.blockers.length()").value(0))
                .andExpect(jsonPath("$.missingDocumentTypes.length()").value(4))
                .andExpect(jsonPath("$.missingDocumentTypes[0]").value("ARCHITECTURE")) // profile order
                .andExpect(jsonPath("$.tokenEstimate").value(org.hamcrest.Matchers.greaterThan(0)));

        String[] req = approvedDoc(o, pid, "REQUIREMENTS", "must do X");
        // an open draft of the SAME document must never leak into the context
        mvc.perform(as(post("/api/v1/documents/" + req[0] + "/versions"), o).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"DRAFT TEXT\"}")).andExpect(status().isCreated());

        mvc.perform(get("/api/v1/agents/" + ag + "/context-preview").session(o.session()))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].documentType").value("REQUIREMENTS"))
                .andExpect(jsonPath("$.items[0].versionId").value(req[1]))
                .andExpect(jsonPath("$.items[0].contentHash").value(ContentHash.of("must do X")))
                .andExpect(jsonPath("$.items[0].mode").value("FULL"))
                .andExpect(jsonPath("$.missingDocumentTypes.length()").value(3));
        Assembled a = assembler.assemble(UUID.fromString(ag), null, null, ContextAssembler.Mode.PREVIEW);
        assertThat(a.system()).contains("must do X").doesNotContain("DRAFT TEXT");
        assertThat(a.snapshotId()).isNull();
        assertThat(count("select count(*) from context_snapshot")).isZero(); // PREVIEW writes nothing
    }

    @Test
    void commitStoresAnImmutableSnapshot_andRenderFromSnapshotRebuildsTheIdenticalPrompt() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String pid = createProject(o);
        String ag = agent(o, pid, "BACKEND", "be", "{}");
        String[] req = approvedDoc(o, pid, "REQUIREMENTS", "must do X");
        String[] arch = approvedDoc(o, pid, "ARCHITECTURE", "layered");
        UUID cid = conversationWithMessage(o, ag, "hello there");
        UUID roleConfig = jdbc.queryForObject("select active_config_version_id from role_definition r join agent a on a.role_id = r.id where a.id = ?", UUID.class, UUID.fromString(ag));

        Assembled committed = assembler.assemble(UUID.fromString(ag), cid, 1, ContextAssembler.Mode.COMMIT);
        assertThat(committed.snapshotId()).isNotNull();
        assertThat(committed.lastMessageSeq()).isEqualTo(1);
        assertThat(committed.turns()).hasSize(1);
        assertThat(committed.turns().get(0)).isEqualTo(new ContextAssembler.Turn("user", "hello there"));
        assertThat(committed.system()).contains("You are the Backend Developer")
                .contains("<document type=\"ARCHITECTURE\" version=\"1\" hash=\"" + ContentHash.of("layered") + "\">");
        assertThat(count("select count(*) from snapshot_item where snapshot_id = ?", committed.snapshotId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("select missing_document_types::text from context_snapshot where id = ?", String.class,
                committed.snapshotId())).contains("API_CONTRACT").contains("ADR");
        assertThatThrownBy(() -> jdbc.update("update context_snapshot set token_estimate = 1 where id = ?", committed.snapshotId()))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("delete from snapshot_item where snapshot_id = ?", committed.snapshotId()))
                .isInstanceOf(DataAccessException.class);

        // the approved world moves on AND the conversation grows; the snapshot still renders the same prompt
        assertThat(assembler.staleDocumentTypes(UUID.fromString(pid), committed.snapshotId(), roleConfig)).isEmpty();
        newApprovedVersion(o, arch[0], "layered v2");
        new TransactionTemplate(txm).execute(s -> writer.appendAgentMessage(cid, "later reply", null));
        Assembled again = assembler.renderFromSnapshot(committed.snapshotId(), cid, roleConfig, null);
        assertThat(again.system()).isEqualTo(committed.system());
        assertThat(again.turns()).isEqualTo(committed.turns());
        assertThat(again.items()).isEqualTo(committed.items());
        assertThat(assembler.staleDocumentTypes(UUID.fromString(pid), committed.snapshotId(), roleConfig))
                .containsExactly("ARCHITECTURE");

        // a fresh assembly now sees v2
        Assembled fresh = assembler.assemble(UUID.fromString(ag), cid, 1, ContextAssembler.Mode.PREVIEW);
        assertThat(fresh.system()).contains("layered v2").doesNotContain("\nlayered\n");

        ContextAssembler.SnapshotView view = assembler.snapshot(committed.snapshotId()).orElseThrow();
        assertThat(view.items()).extracting(ContextAssembler.Item::versionNo).containsExactly(1, 1);
        assertThat(view.missingDocumentTypes()).containsExactlyInAnyOrder("API_CONTRACT", "ADR");
    }

    @Test
    void aTamperedSnapshotItemIsAHardFailure() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String pid = createProject(o);
        String ag = agent(o, pid, "BACKEND", "be", "{}");
        approvedDoc(o, pid, "REQUIREMENTS", "must do X");
        UUID cid = conversationWithMessage(o, ag, "hi");
        UUID roleConfig = jdbc.queryForObject("select active_config_version_id from role_definition r join agent a on a.role_id = r.id where a.id = ?", UUID.class, UUID.fromString(ag));
        Assembled c = assembler.assemble(UUID.fromString(ag), cid, 1, ContextAssembler.Mode.COMMIT);

        jdbc.execute("alter table snapshot_item disable trigger trg_snapshot_item_append_only");
        try {
            jdbc.update("update snapshot_item set content_hash = 'sha256:tampered' where snapshot_id = ?", c.snapshotId());
        } finally {
            jdbc.execute("alter table snapshot_item enable trigger trg_snapshot_item_append_only");
        }
        assertThatThrownBy(() -> assembler.renderFromSnapshot(c.snapshotId(), cid, roleConfig, null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("SNAPSHOT_HASH_MISMATCH");
    }

    @Test
    void oversizedApprovedContextBlocks_andCommitStoresNothing() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String pid = createProject(o);
        String ag = agent(o, pid, "BACKEND", "be", "{\"maxContextTokens\":1000}");
        approvedDoc(o, pid, "REQUIREMENTS", "z".repeat(8_000));
        UUID cid = conversationWithMessage(o, ag, "hi");

        mvc.perform(get("/api/v1/agents/" + ag + "/context-preview?conversationId=" + cid).session(o.session()))
                .andExpect(jsonPath("$.blockers[0].reason").value("CONTEXT_TOO_LARGE"));
        Assembled c = assembler.assemble(UUID.fromString(ag), cid, 1, ContextAssembler.Mode.COMMIT);
        assertThat(c.blockers()).extracting(ContextAssembler.Blocker::reason).containsExactly("CONTEXT_TOO_LARGE");
        assertThat(c.snapshotId()).isNull();
        assertThat(count("select count(*) from context_snapshot")).isZero();
    }

    @Test
    void aDisabledAgentIsReportedAsABlocker() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String pid = createProject(o);
        String ag = agent(o, pid, "QA", "qa", "{}");
        mvc.perform(as(patch("/api/v1/agents/" + ag), o).contentType(MediaType.APPLICATION_JSON).content("{\"isEnabled\":false}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/agents/" + ag + "/context-preview").session(o.session()))
                .andExpect(jsonPath("$.blockers[0].reason").value("AGENT_DISABLED"));
    }

    @Test
    void previewIsOwnerOnly_andConversationMustBelongToTheAgent() throws Exception {
        createUser("o@example.com", PW);
        UUID viewerId = createUser("v@example.com", PW);
        createUser("x@example.com", PW);
        Auth o = login("o@example.com", PW);
        Auth v = login("v@example.com", PW);
        Auth x = login("x@example.com", PW);
        String pid = createProject(o);
        jdbc.update("insert into project_member(project_id, user_id, role) values (?, ?, 'VIEWER')", UUID.fromString(pid), viewerId);
        String a1 = agent(o, pid, "QA", "qa", "{}");
        String a2 = agent(o, pid, "REVIEWER", "rev", "{}");
        UUID c1 = conversationWithMessage(o, a1, "hi");

        mvc.perform(get("/api/v1/agents/" + a1 + "/context-preview").session(v.session())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/agents/" + a1 + "/context-preview").session(x.session())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/agents/" + UUID.randomUUID() + "/context-preview").session(o.session())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/agents/" + a2 + "/context-preview?conversationId=" + c1).session(o.session())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/agents/" + a1 + "/context-preview?conversationId=" + c1).session(o.session())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/agents/" + a1 + "/context-preview")).andExpect(status().isUnauthorized());
    }
}
