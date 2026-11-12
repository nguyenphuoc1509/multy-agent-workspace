package com.workspace.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.workspace.document.domain.ContentHash;
import com.workspace.support.IntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** M1 step 1a: create / read / list / new draft / edit draft over HTTP, against real PostgreSQL. */
class DocumentApiTest extends IntegrationTestBase {

    private static final String PW = "correct-horse-battery";

    private String createProject(Auth auth) throws Exception {
        String body = mvc.perform(as(post("/api/v1/projects"), auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"P\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private MvcResult createDoc(Auth auth, String projectId, String type, String title, String text) throws Exception {
        return mvc.perform(as(post("/api/v1/projects/" + projectId + "/documents"), auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"%s\",\"title\":\"%s\",\"content\":\"%s\"}".formatted(type, title, text)))
                .andExpect(status().isCreated()).andReturn();
    }

    private static String id(MvcResult r, String path) throws Exception {
        return JsonPath.read(r.getResponse().getContentAsString(), path);
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private void discard(String versionId) {
        jdbc.update("update document_version set status = 'DISCARDED' where id = ?", UUID.fromString(versionId));
    }

    @Test
    void createDocumentMakesDraftOneWithHashAndAuditRows() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String pid = createProject(o);

        MvcResult created = createDoc(o, pid, "REQUIREMENTS", "Reqs", "line1\\r\\nline2");
        String did = id(created, "$.id");
        String vid = id(created, "$.openVersionId");
        mvc.perform(get("/api/v1/documents/" + did).session(o.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("REQUIREMENTS"))
                .andExpect(jsonPath("$.currentApprovedVersionId").value((Object) null))
                .andExpect(jsonPath("$.openVersionId").value(vid));

        MvcResult v = mvc.perform(get("/api/v1/document-versions/" + vid).session(o.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNo").value(1))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.contentFormat").value("text/markdown"))
                .andExpect(jsonPath("$.content").value("line1\nline2"))
                .andExpect(jsonPath("$.authorType").value("HUMAN"))
                .andReturn();
        String hash = id(v, "$.contentHash");
        assertThat(hash).isEqualTo(ContentHash.of("line1\nline2"));
        assertThat(v.getResponse().getHeader("ETag")).isEqualTo("\"" + hash + "\"");

        assertThat(count("select count(*) from audit_log where entity_id = ? and action = 'DOCUMENT_CREATED'",
                UUID.fromString(did))).isEqualTo(1);
        assertThat(count("select count(*) from audit_log where entity_id = ? and action = 'VERSION_CREATED'",
                UUID.fromString(vid))).isEqualTo(1);
    }

    @Test
    void apiContractUsesYamlFormat() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        MvcResult created = createDoc(o, createProject(o), "API_CONTRACT", "API", "openapi: 3.1.0");
        mvc.perform(get("/api/v1/document-versions/" + id(created, "$.openVersionId")).session(o.session()))
                .andExpect(jsonPath("$.contentFormat").value("application/yaml"));
    }

    @Test
    void blankTitleAndUnknownTypeAreRejected() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String pid = createProject(o);
        mvc.perform(as(post("/api/v1/projects/" + pid + "/documents"), o).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ADR\",\"title\":\" \",\"content\":\"x\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(as(post("/api/v1/projects/" + pid + "/documents"), o).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"NOPE\",\"title\":\"t\",\"content\":\"x\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void nonMemberGets404EverywhereAndNothingLeaks() throws Exception {
        createUser("o@example.com", PW);
        createUser("x@example.com", PW);
        Auth o = login("o@example.com", PW);
        Auth x = login("x@example.com", PW);
        String pid = createProject(o);
        MvcResult created = createDoc(o, pid, "ADR", "t", "body");
        String did = id(created, "$.id");
        String vid = id(created, "$.openVersionId");

        mvc.perform(get("/api/v1/projects/" + pid + "/documents").session(x.session())).andExpect(status().isNotFound());
        mvc.perform(as(post("/api/v1/projects/" + pid + "/documents"), x).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"ADR\",\"title\":\"t\",\"content\":\"c\"}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/documents/" + did).session(x.session())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/documents/" + did + "/versions").session(x.session())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/document-versions/" + vid).session(x.session())).andExpect(status().isNotFound());
        mvc.perform(as(post("/api/v1/documents/" + did + "/versions"), x).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isNotFound());
        mvc.perform(as(patch("/api/v1/document-versions/" + vid), x).contentType(MediaType.APPLICATION_JSON)
                .header("If-Match", "\"whatever\"").content("{\"content\":\"hijack\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void viewerCanReadButCannotWrite() throws Exception {
        createUser("o@example.com", PW);
        UUID viewerId = createUser("v@example.com", PW);
        Auth o = login("o@example.com", PW);
        Auth v = login("v@example.com", PW);
        String pid = createProject(o);
        jdbc.update("insert into project_member(project_id, user_id, role) values (?, ?, 'VIEWER')",
                UUID.fromString(pid), viewerId);
        MvcResult created = createDoc(o, pid, "ADR", "t", "body");
        String did = id(created, "$.id");
        String vid = id(created, "$.openVersionId");

        mvc.perform(get("/api/v1/documents/" + did).session(v.session())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/document-versions/" + vid).session(v.session())).andExpect(status().isOk());
        mvc.perform(as(post("/api/v1/projects/" + pid + "/documents"), v).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"ADR\",\"title\":\"t\",\"content\":\"c\"}")).andExpect(status().isForbidden());
        mvc.perform(as(post("/api/v1/documents/" + did + "/versions"), v).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isForbidden());
        mvc.perform(as(patch("/api/v1/document-versions/" + vid), v).contentType(MediaType.APPLICATION_JSON)
                .header("If-Match", "\"x\"").content("{\"content\":\"nope\"}")).andExpect(status().isForbidden());
    }

    @Test
    void onlyOneOpenVersionPerDocument() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        MvcResult created = createDoc(o, createProject(o), "ADR", "t", "body");
        String did = id(created, "$.id");
        String vid = id(created, "$.openVersionId");
        mvc.perform(as(post("/api/v1/documents/" + did + "/versions"), o).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPEN_VERSION_EXISTS"))
                .andExpect(jsonPath("$.openVersionId").value(vid));
    }

    @Test
    void newDraftAfterDiscardGetsNextNumberAndNoParentWhenNothingIsApproved() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        MvcResult created = createDoc(o, createProject(o), "ADR", "t", "first");
        String did = id(created, "$.id");
        discard(id(created, "$.openVersionId"));

        mvc.perform(as(post("/api/v1/documents/" + did + "/versions"), o).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"second\",\"changeSummary\":\"redo\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNo").value(2))
                .andExpect(jsonPath("$.parentVersionId").value((Object) null))
                .andExpect(jsonPath("$.content").value("second"))
                .andExpect(jsonPath("$.changeSummary").value("redo"));
    }

    @Test
    void fromVersionOfAnotherDocumentIs404_andFromVersionOfSameDocumentCopiesItsContent() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String pid = createProject(o);
        MvcResult a = createDoc(o, pid, "ADR", "A", "alpha body");
        MvcResult b = createDoc(o, pid, "ADR", "B", "beta body");
        String aDoc = id(a, "$.id");
        String aV1 = id(a, "$.openVersionId");
        String bV1 = id(b, "$.openVersionId");
        discard(aV1);

        mvc.perform(as(post("/api/v1/documents/" + aDoc + "/versions"), o).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromVersionId\":\"" + bV1 + "\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(as(post("/api/v1/documents/" + aDoc + "/versions"), o).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromVersionId\":\"" + aV1 + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentVersionId").value(aV1))
                .andExpect(jsonPath("$.content").value("alpha body"));
    }

    @Test
    void patchNeedsIfMatch_staleIs412_correctWorksAndAuditHasHashesOnly() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        MvcResult created = createDoc(o, createProject(o), "ADR", "t", "old text");
        String vid = id(created, "$.openVersionId");
        String hash = ContentHash.of("old text");

        mvc.perform(as(patch("/api/v1/document-versions/" + vid), o).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"top secret body\"}"))
                .andExpect(status().is(428)).andExpect(jsonPath("$.code").value("PRECONDITION_REQUIRED"));
        mvc.perform(as(patch("/api/v1/document-versions/" + vid), o).contentType(MediaType.APPLICATION_JSON)
                        .header("If-Match", "\"sha256:stale\"").content("{\"content\":\"top secret body\"}"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("PRECONDITION_FAILED"))
                .andExpect(jsonPath("$.currentHash").value(hash));
        mvc.perform(as(patch("/api/v1/document-versions/" + vid), o).contentType(MediaType.APPLICATION_JSON)
                        .header("If-Match", "\"" + hash + "\"").content("{}"))
                .andExpect(status().isBadRequest());

        MvcResult ok = mvc.perform(as(patch("/api/v1/document-versions/" + vid), o)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("If-Match", "\"" + hash + "\"").content("{\"content\":\"top secret body\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("top secret body"))
                .andReturn();
        String newHash = ContentHash.of("top secret body");
        assertThat(id(ok, "$.contentHash")).isEqualTo(newHash);
        assertThat(ok.getResponse().getHeader("ETag")).isEqualTo("\"" + newHash + "\"");

        // the old hash is now stale
        mvc.perform(as(patch("/api/v1/document-versions/" + vid), o).contentType(MediaType.APPLICATION_JSON)
                        .header("If-Match", "\"" + hash + "\"").content("{\"changeSummary\":\"x\"}"))
                .andExpect(status().isPreconditionFailed());

        assertThat(count("select count(*) from audit_log where entity_id = ? and action = 'VERSION_UPDATED'",
                UUID.fromString(vid))).isEqualTo(1);
        assertThat(count("""
                select count(*) from audit_log
                where entity_id = ? and (after_json::text like '%secret%' or before_json::text like '%secret%'
                                         or after_json::text like '%old text%' or before_json::text like '%old text%')
                """, UUID.fromString(vid))).isZero();
    }

    @Test
    void patchOnSubmittedVersionIs409() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        MvcResult created = createDoc(o, createProject(o), "ADR", "t", "text");
        String vid = id(created, "$.openVersionId");
        jdbc.update("update document_version set status = 'IN_REVIEW' where id = ?", UUID.fromString(vid));

        mvc.perform(as(patch("/api/v1/document-versions/" + vid), o).contentType(MediaType.APPLICATION_JSON)
                        .header("If-Match", "\"" + ContentHash.of("text") + "\"").content("{\"content\":\"edit\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void documentListFiltersByTypeAndPaginates() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String pid = createProject(o);
        createDoc(o, pid, "REQUIREMENTS", "r1", "x");
        createDoc(o, pid, "REQUIREMENTS", "r2", "x");
        createDoc(o, pid, "ADR", "a1", "x");

        MvcResult first = mvc.perform(get("/api/v1/projects/" + pid + "/documents?limit=2").session(o.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2)).andReturn();
        String next = id(first, "$.nextCursor");
        assertThat(next).isNotBlank();
        mvc.perform(get("/api/v1/projects/" + pid + "/documents?limit=2&cursor=" + next).session(o.session()))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.nextCursor").value((Object) null));
        mvc.perform(get("/api/v1/projects/" + pid + "/documents?type=ADR").session(o.session()))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("a1"));
        mvc.perform(get("/api/v1/projects/" + pid + "/documents?limit=0").session(o.session()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/projects/" + pid + "/documents?cursor=%25%25").session(o.session()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void versionListIsNewestFirstWithoutBodiesAndPaginates() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        MvcResult created = createDoc(o, createProject(o), "ADR", "t", "v1");
        String did = id(created, "$.id");
        discard(id(created, "$.openVersionId"));
        for (int i = 2; i <= 3; i++) {
            MvcResult nv = mvc.perform(as(post("/api/v1/documents/" + did + "/versions"), o)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"v" + i + "\"}"))
                    .andExpect(status().isCreated()).andReturn();
            if (i < 3) discard(id(nv, "$.id"));
        }

        MvcResult page1 = mvc.perform(get("/api/v1/documents/" + did + "/versions?limit=2").session(o.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].versionNo").value(3))
                .andExpect(jsonPath("$.items[1].versionNo").value(2))
                .andExpect(jsonPath("$.items[0].content").doesNotExist())
                .andReturn();
        assertThat(id(page1, "$.nextCursor")).isEqualTo("2");
        mvc.perform(get("/api/v1/documents/" + did + "/versions?limit=2&cursor=2").session(o.session()))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].versionNo").value(1))
                .andExpect(jsonPath("$.nextCursor").value((Object) null));
        mvc.perform(get("/api/v1/documents/" + did + "/versions?cursor=abc").session(o.session()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // ---- step 1b: submit and discard ----

    private void postAction(Auth a, String vid, String action, org.springframework.test.web.servlet.ResultMatcher expected)
            throws Exception {
        mvc.perform(as(post("/api/v1/document-versions/" + vid + "/" + action), a)).andExpect(expected);
    }

    @Test
    void submitMovesDraftToInReviewAndAudits() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        MvcResult created = createDoc(o, createProject(o), "ADR", "t", "text");
        String did = id(created, "$.id");
        String vid = id(created, "$.openVersionId");

        mvc.perform(as(post("/api/v1/document-versions/" + vid + "/submit"), o))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.submittedAt").isNotEmpty());
        assertThat(count("select count(*) from audit_log where entity_id = ? and action = 'VERSION_SUBMITTED'",
                UUID.fromString(vid))).isEqualTo(1);

        // still the open version: no second draft, no more edits, no second submit, no discard
        mvc.perform(as(post("/api/v1/documents/" + did + "/versions"), o).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPEN_VERSION_EXISTS"));
        mvc.perform(as(patch("/api/v1/document-versions/" + vid), o).contentType(MediaType.APPLICATION_JSON)
                .header("If-Match", "\"" + ContentHash.of("text") + "\"").content("{\"content\":\"x\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"));
        postAction(o, vid, "submit", status().isConflict());
        postAction(o, vid, "discard", status().isConflict());
    }

    @Test
    void emptyContentCannotBeSubmitted() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        MvcResult created = createDoc(o, createProject(o), "REQUIREMENTS", "t", "   ");
        String vid = id(created, "$.openVersionId");
        mvc.perform(as(post("/api/v1/document-versions/" + vid + "/submit"), o))
                .andExpect(status().is(422)).andExpect(jsonPath("$.code").value("INVALID_CONTENT"));
        mvc.perform(get("/api/v1/document-versions/" + vid).session(o.session()))
                .andExpect(jsonPath("$.status").value("DRAFT")).andExpect(jsonPath("$.submittedAt").value((Object) null));
    }

    @Test
    void discardFreesTheOpenSlotAndIsAudited() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        MvcResult created = createDoc(o, createProject(o), "ADR", "t", "text");
        String did = id(created, "$.id");
        String vid = id(created, "$.openVersionId");

        mvc.perform(as(post("/api/v1/document-versions/" + vid + "/discard"), o))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISCARDED"));
        assertThat(count("select count(*) from audit_log where entity_id = ? and action = 'VERSION_DISCARDED'",
                UUID.fromString(vid))).isEqualTo(1);
        mvc.perform(get("/api/v1/documents/" + did).session(o.session()))
                .andExpect(jsonPath("$.openVersionId").value((Object) null));
        mvc.perform(as(post("/api/v1/documents/" + did + "/versions"), o).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"again\"}")).andExpect(status().isCreated());
    }

    @Test
    void submitAndDiscardNeedOwnerAndMembership() throws Exception {
        createUser("o@example.com", PW);
        UUID viewerId = createUser("v@example.com", PW);
        createUser("x@example.com", PW);
        Auth o = login("o@example.com", PW);
        Auth v = login("v@example.com", PW);
        Auth x = login("x@example.com", PW);
        String pid = createProject(o);
        jdbc.update("insert into project_member(project_id, user_id, role) values (?, ?, 'VIEWER')",
                UUID.fromString(pid), viewerId);
        String vid = id(createDoc(o, pid, "ADR", "t", "text"), "$.openVersionId");

        postAction(v, vid, "submit", status().isForbidden());
        postAction(v, vid, "discard", status().isForbidden());
        postAction(x, vid, "submit", status().isNotFound());
        postAction(x, vid, "discard", status().isNotFound());
        postAction(o, UUID.randomUUID().toString(), "submit", status().isNotFound());
        mvc.perform(get("/api/v1/document-versions/" + vid).session(o.session()))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }
}
