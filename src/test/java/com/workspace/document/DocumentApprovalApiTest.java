package com.workspace.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.workspace.document.domain.ContentHash;
import com.workspace.support.IntegrationTestBase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** M1 step 1c: approve / reject, gates, supersede, rollback flag, concurrency. Real PostgreSQL. */
class DocumentApprovalApiTest extends IntegrationTestBase {

    private static final String PW = "correct-horse-battery";

    private String createProject(Auth auth) throws Exception {
        String body = mvc.perform(as(post("/api/v1/projects"), auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"P\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private MvcResult createDoc(Auth auth, String pid, String type, String text) throws Exception {
        return mvc.perform(as(post("/api/v1/projects/" + pid + "/documents"), auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"%s\",\"title\":\"t\",\"content\":\"%s\"}".formatted(type, text)))
                .andExpect(status().isCreated()).andReturn();
    }

    private static String id(MvcResult r, String path) throws Exception {
        return JsonPath.read(r.getResponse().getContentAsString(), path);
    }

    private void submit(Auth a, String vid) throws Exception {
        mvc.perform(as(post("/api/v1/document-versions/" + vid + "/submit"), a)).andExpect(status().isOk());
    }

    private ResultActions approve(Auth a, String vid, String hash) throws Exception {
        return mvc.perform(as(post("/api/v1/document-versions/" + vid + "/approve"), a)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedHash\":\"%s\",\"comment\":\"ok\"}".formatted(hash)));
    }

    private ResultActions reject(Auth a, String vid, String json) throws Exception {
        return mvc.perform(as(post("/api/v1/document-versions/" + vid + "/reject"), a)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** create + submit + approve in one go; returns the version id. */
    private String approved(Auth a, String pid, String type, String text) throws Exception {
        String vid = id(createDoc(a, pid, type, text), "$.openVersionId");
        submit(a, vid);
        approve(a, vid, ContentHash.of(text)).andExpect(status().isOk());
        return vid;
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private String statusOf(Auth a, String vid) throws Exception {
        return JsonPath.read(mvc.perform(get("/api/v1/document-versions/" + vid).session(a.session()))
                .andReturn().getResponse().getContentAsString(), "$.status");
    }

    private boolean isRollback(String vid) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select is_rollback from document_version where id = ?",
                Boolean.class, UUID.fromString(vid)));
    }

    @Test
    void fullFlowApproveThenNewDraftSupersedesTheOldOne() throws Exception {
        UUID ownerId = createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String pid = createProject(o);
        MvcResult created = createDoc(o, pid, "REQUIREMENTS", "text one");
        String did = id(created, "$.id");
        String v1 = id(created, "$.openVersionId");
        submit(o, v1);

        approve(o, v1, ContentHash.of("text one"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version.status").value("APPROVED"))
                .andExpect(jsonPath("$.supersededVersionId").value((Object) null))
                .andExpect(jsonPath("$.approval.gateCode").value("REQUIREMENTS_APPROVED"))
                .andExpect(jsonPath("$.approval.decision").value("APPROVED"))
                .andExpect(jsonPath("$.approval.approverId").value(ownerId.toString()))
                .andExpect(jsonPath("$.approval.contentHash").value(ContentHash.of("text one")));
        mvc.perform(get("/api/v1/documents/" + did).session(o.session()))
                .andExpect(jsonPath("$.currentApprovedVersionId").value(v1))
                .andExpect(jsonPath("$.openVersionId").value((Object) null));
        assertThat(count("select count(*) from outbox_event where aggregate_id = ? and event_type = 'DocumentVersionApproved'",
                UUID.fromString(v1))).isEqualTo(1);
        assertThat(count("select count(*) from audit_log where entity_id = ? and action = 'VERSION_APPROVED'",
                UUID.fromString(v1))).isEqualTo(1);
        assertThat(count("select count(*) from approval where document_version_id = ? and decision = 'APPROVED'",
                UUID.fromString(v1))).isEqualTo(1);

        // second round: a normal draft is based on the approved version and is not a rollback
        MvcResult next = mvc.perform(as(post("/api/v1/documents/" + did + "/versions"), o)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentVersionId").value(v1))
                .andExpect(jsonPath("$.content").value("text one"))
                .andReturn();
        String v2 = id(next, "$.id");
        assertThat(isRollback(v2)).isFalse();
        mvc.perform(as(patch("/api/v1/document-versions/" + v2), o).contentType(MediaType.APPLICATION_JSON)
                .header("If-Match", "\"" + ContentHash.of("text one") + "\"").content("{\"content\":\"text two\"}"))
                .andExpect(status().isOk());
        submit(o, v2);
        approve(o, v2, ContentHash.of("text two"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supersededVersionId").value(v1));

        assertThat(statusOf(o, v1)).isEqualTo("SUPERSEDED");
        assertThat(statusOf(o, v2)).isEqualTo("APPROVED");
        mvc.perform(get("/api/v1/documents/" + did).session(o.session()))
                .andExpect(jsonPath("$.currentApprovedVersionId").value(v2));
    }

    @Test
    void staleHashIs409WithCurrentHash_andNothingChanges() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String vid = id(createDoc(o, createProject(o), "ADR", "text"), "$.openVersionId");
        submit(o, vid);

        approve(o, vid, "sha256:stale").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_CONTENT"))
                .andExpect(jsonPath("$.currentHash").value(ContentHash.of("text")));
        assertThat(statusOf(o, vid)).isEqualTo("IN_REVIEW");
        assertThat(count("select count(*) from approval where document_version_id = ?", UUID.fromString(vid))).isZero();
        assertThat(count("select count(*) from outbox_event where aggregate_id = ? and event_type = 'DocumentVersionApproved'",
                UUID.fromString(vid))).isZero();
    }

    @Test
    void onlyInReviewCanBeApproved_andNotTwice() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String pid = createProject(o);
        String draft = id(createDoc(o, pid, "ADR", "text"), "$.openVersionId");
        approve(o, draft, ContentHash.of("text")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        submit(o, draft);
        approve(o, draft, ContentHash.of("text")).andExpect(status().isOk());
        approve(o, draft, ContentHash.of("text")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        assertThat(count("select count(*) from approval where document_version_id = ?", UUID.fromString(draft))).isEqualTo(1);
    }

    @Test
    void gatesAreComputedFromApprovedUpstreamDocuments() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String pid = createProject(o);

        approved(o, pid, "ADR", "an adr needs nothing");

        String arch = id(createDoc(o, pid, "ARCHITECTURE", "arch"), "$.openVersionId");
        submit(o, arch);
        approve(o, arch, ContentHash.of("arch")).andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("GATE_NOT_SATISFIED"))
                .andExpect(jsonPath("$.missing[0].documentType").value("REQUIREMENTS"))
                .andExpect(jsonPath("$.missing[0].reason").value("NO_APPROVED_VERSION"));
        assertThat(statusOf(o, arch)).isEqualTo("IN_REVIEW");

        String api = id(createDoc(o, pid, "API_CONTRACT", "openapi: 3.1.0"), "$.openVersionId");
        submit(o, api);
        approve(o, api, ContentHash.of("openapi: 3.1.0")).andExpect(status().is(422))
                .andExpect(jsonPath("$.missing[0].documentType").value("ARCHITECTURE"));

        approved(o, pid, "REQUIREMENTS", "reqs");
        approve(o, arch, ContentHash.of("arch")).andExpect(status().isOk());
        approve(o, api, ContentHash.of("openapi: 3.1.0")).andExpect(status().isOk())
                .andExpect(jsonPath("$.approval.gateCode").value("API_CONTRACT_APPROVED"));
    }

    @Test
    void approverCanDecideButNotWrite_viewerAndOutsiderCannotDecide() throws Exception {
        createUser("o@example.com", PW);
        UUID approverId = createUser("a@example.com", PW);
        UUID viewerId = createUser("v@example.com", PW);
        createUser("x@example.com", PW);
        Auth o = login("o@example.com", PW);
        Auth a = login("a@example.com", PW);
        Auth v = login("v@example.com", PW);
        Auth x = login("x@example.com", PW);
        String pid = createProject(o);
        jdbc.update("insert into project_member(project_id, user_id, role) values (?, ?, 'APPROVER')",
                UUID.fromString(pid), approverId);
        jdbc.update("insert into project_member(project_id, user_id, role) values (?, ?, 'VIEWER')",
                UUID.fromString(pid), viewerId);
        String vid = id(createDoc(o, pid, "ADR", "text"), "$.openVersionId");
        submit(o, vid);

        approve(v, vid, ContentHash.of("text")).andExpect(status().isForbidden());
        reject(v, vid, "{\"comment\":\"no\"}").andExpect(status().isForbidden());
        approve(x, vid, ContentHash.of("text")).andExpect(status().isNotFound());
        reject(x, vid, "{\"comment\":\"no\"}").andExpect(status().isNotFound());
        assertThat(statusOf(o, vid)).isEqualTo("IN_REVIEW");

        approve(a, vid, ContentHash.of("text")).andExpect(status().isOk())
                .andExpect(jsonPath("$.approval.approverId").value(approverId.toString()));

        // an approver is not a writer
        mvc.perform(as(post("/api/v1/projects/" + pid + "/documents"), a).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"ADR\",\"title\":\"t\",\"content\":\"c\"}")).andExpect(status().isForbidden());
    }

    @Test
    void rejectNeedsCommentRecordsDecisionAndFreesTheSlot() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        MvcResult created = createDoc(o, createProject(o), "ADR", "text");
        String did = id(created, "$.id");
        String vid = id(created, "$.openVersionId");
        submit(o, vid);

        reject(o, vid, "{}").andExpect(status().isBadRequest());
        reject(o, vid, "{\"comment\":\"  \"}").andExpect(status().isBadRequest());
        assertThat(statusOf(o, vid)).isEqualTo("IN_REVIEW");

        reject(o, vid, "{\"comment\":\"needs work\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.version.status").value("REJECTED"))
                .andExpect(jsonPath("$.approval.decision").value("REJECTED"))
                .andExpect(jsonPath("$.approval.comment").value("needs work"));
        assertThat(count("select count(*) from outbox_event where aggregate_id = ? and event_type = 'DocumentVersionRejected'",
                UUID.fromString(vid))).isEqualTo(1);
        assertThat(count("select count(*) from audit_log where entity_id = ? and action = 'VERSION_REJECTED'",
                UUID.fromString(vid))).isEqualTo(1);
        mvc.perform(get("/api/v1/documents/" + did).session(o.session()))
                .andExpect(jsonPath("$.openVersionId").value((Object) null));
        reject(o, vid, "{\"comment\":\"again\"}").andExpect(status().isConflict());
    }

    @Test
    void reEditOfRejectedVersionIsFlaggedAndCanBeApproved() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        MvcResult created = createDoc(o, createProject(o), "ADR", "first try");
        String did = id(created, "$.id");
        String v1 = id(created, "$.openVersionId");
        submit(o, v1);
        reject(o, v1, "{\"comment\":\"redo\"}").andExpect(status().isOk());

        // nothing is approved yet, so the rejected parent differs from the (null) approved pointer
        MvcResult redo = mvc.perform(as(post("/api/v1/documents/" + did + "/versions"), o)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fromVersionId\":\"" + v1 + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentVersionId").value(v1))
                .andReturn();
        String v2 = id(redo, "$.id");
        assertThat(isRollback(v2)).isTrue();
        submit(o, v2);
        approve(o, v2, ContentHash.of("first try")).andExpect(status().isOk())
                .andExpect(jsonPath("$.supersededVersionId").value((Object) null));
        mvc.perform(get("/api/v1/documents/" + did).session(o.session()))
                .andExpect(jsonPath("$.currentApprovedVersionId").value(v2));
    }

    @Test
    void rollbackCreatesNewDraftAndNeverRepointsToTheOldVersion() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String pid = createProject(o);
        MvcResult created = createDoc(o, pid, "ADR", "old text");
        String did = id(created, "$.id");
        String v1 = id(created, "$.openVersionId");
        submit(o, v1);
        approve(o, v1, ContentHash.of("old text")).andExpect(status().isOk());

        String v2 = id(mvc.perform(as(post("/api/v1/documents/" + did + "/versions"), o)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"new text\"}"))
                .andExpect(status().isCreated()).andReturn(), "$.id");
        submit(o, v2);
        approve(o, v2, ContentHash.of("new text")).andExpect(status().isOk());

        MvcResult back = mvc.perform(as(post("/api/v1/documents/" + did + "/versions"), o)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fromVersionId\":\"" + v1 + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentVersionId").value(v1))
                .andExpect(jsonPath("$.content").value("old text"))
                .andReturn();
        String v3 = id(back, "$.id");
        assertThat(isRollback(v3)).isTrue();
        // until the new draft is approved, the pointer stays on v2
        mvc.perform(get("/api/v1/documents/" + did).session(o.session()))
                .andExpect(jsonPath("$.currentApprovedVersionId").value(v2));
        submit(o, v3);
        approve(o, v3, ContentHash.of("old text")).andExpect(status().isOk())
                .andExpect(jsonPath("$.supersededVersionId").value(v2));
        assertThat(statusOf(o, v1)).isEqualTo("SUPERSEDED");
        assertThat(statusOf(o, v2)).isEqualTo("SUPERSEDED");
        assertThat(statusOf(o, v3)).isEqualTo("APPROVED");
    }

    @Test
    void fromVersionEqualToTheApprovedOneIsNotARollback() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        MvcResult created = createDoc(o, createProject(o), "ADR", "text");
        String did = id(created, "$.id");
        String v1 = id(created, "$.openVersionId");
        submit(o, v1);
        approve(o, v1, ContentHash.of("text")).andExpect(status().isOk());

        String v2 = id(mvc.perform(as(post("/api/v1/documents/" + did + "/versions"), o)
                .contentType(MediaType.APPLICATION_JSON).content("{\"fromVersionId\":\"" + v1 + "\"}"))
                .andExpect(status().isCreated()).andReturn(), "$.id");
        assertThat(isRollback(v2)).isFalse();
    }

    @Test
    void databaseRejectsApprovalWithWrongHashAndDuplicateDecisions() throws Exception {
        UUID ownerId = createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String vid = id(createDoc(o, createProject(o), "ADR", "text"), "$.openVersionId");
        submit(o, vid);
        UUID v = UUID.fromString(vid);
        String sql = "insert into approval(id, document_version_id, gate_code, approver_id, decision, content_hash)"
                + " values (?, ?, 'ADR_APPROVED', ?, 'APPROVED', ?)";

        assertThatThrownBy(() -> jdbc.update(sql, UUID.randomUUID(), v, ownerId, "sha256:wrong"))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(sql, UUID.randomUUID(), v, ownerId, ContentHash.of("text"));
        assertThatThrownBy(() -> jdbc.update(sql, UUID.randomUUID(), v, ownerId, ContentHash.of("text")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void twoConcurrentApprovalsOfTheSameVersionYieldExactlyOneSuccess() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String vid = id(createDoc(o, createProject(o), "ADR", "text"), "$.openVersionId");
        submit(o, vid);
        String hash = ContentHash.of("text");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch go = new CountDownLatch(1);
            Callable<Integer> call = () -> {
                go.await();
                return approve(o, vid, hash).andReturn().getResponse().getStatus();
            };
            Future<Integer> f1 = pool.submit(call);
            Future<Integer> f2 = pool.submit(call);
            go.countDown();
            List<Integer> codes = new ArrayList<>(List.of(f1.get(30, TimeUnit.SECONDS), f2.get(30, TimeUnit.SECONDS)));
            Collections.sort(codes);
            assertThat(codes).containsExactly(200, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(count("select count(*) from approval where document_version_id = ? and decision = 'APPROVED'",
                UUID.fromString(vid))).isEqualTo(1);
        assertThat(count("select count(*) from outbox_event where aggregate_id = ? and event_type = 'DocumentVersionApproved'",
                UUID.fromString(vid))).isEqualTo(1);
    }
}
