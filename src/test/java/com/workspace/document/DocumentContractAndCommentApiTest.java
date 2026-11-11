package com.workspace.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.workspace.document.domain.ContentHash;
import com.workspace.support.IntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** M1 step 1d: API_CONTRACT validation on submit, and comments on versions. Real PostgreSQL. */
class DocumentContractAndCommentApiTest extends IntegrationTestBase {

    private static final String PW = "correct-horse-battery";
    private static final String VALID_CONTRACT =
            "openapi: 3.1.0\ninfo:\n  title: T\n  version: 1.0.0\npaths: {}\n";

    private static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private String createProject(Auth auth) throws Exception {
        String body = mvc.perform(as(post("/api/v1/projects"), auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"P\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private MvcResult createDoc(Auth auth, String pid, String type, String text) throws Exception {
        return mvc.perform(as(post("/api/v1/projects/" + pid + "/documents"), auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"%s\",\"title\":\"t\",\"content\":%s}".formatted(type, jsonString(text))))
                .andExpect(status().isCreated()).andReturn();
    }

    private static String id(MvcResult r, String path) throws Exception {
        return JsonPath.read(r.getResponse().getContentAsString(), path);
    }

    private ResultActions submit(Auth a, String vid) throws Exception {
        return mvc.perform(as(post("/api/v1/document-versions/" + vid + "/submit"), a));
    }

    private ResultActions comment(Auth a, String vid, String body) throws Exception {
        return mvc.perform(as(post("/api/v1/document-versions/" + vid + "/comments"), a)
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":%s}".formatted(jsonString(body))));
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    @Test
    void invalidContractIs422WithLineNumbers_andCanBeFixedAndResubmitted() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String bad = "openapi: 3.1.0\ninfo:\n  title: T\n  title: Again\n  version: 1.0.0\npaths: {}\n";
        String vid = id(createDoc(o, createProject(o), "API_CONTRACT", bad), "$.openVersionId");

        submit(o, vid).andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("INVALID_CONTRACT"))
                .andExpect(jsonPath("$.errors[0].line").value(4))
                .andExpect(jsonPath("$.errors[0].message").value(org.hamcrest.Matchers.containsString("Duplicate key")));
        mvc.perform(get("/api/v1/document-versions/" + vid).session(o.session()))
                .andExpect(jsonPath("$.status").value("DRAFT")).andExpect(jsonPath("$.submittedAt").value((Object) null));

        mvc.perform(as(patch("/api/v1/document-versions/" + vid), o).contentType(MediaType.APPLICATION_JSON)
                        .header("If-Match", "\"" + ContentHash.of(bad) + "\"")
                        .content("{\"content\":%s}".formatted(jsonString(VALID_CONTRACT))))
                .andExpect(status().isOk());
        submit(o, vid).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_REVIEW"));
    }

    @Test
    void otherDocumentTypesAreNotValidatedAsOpenApi() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String vid = id(createDoc(o, createProject(o), "ADR", "this: [is not openapi"), "$.openVersionId");
        submit(o, vid).andExpect(status().isOk());
    }

    @Test
    void commentsAreAppendedListedOldestFirstAndPaginated() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String vid = id(createDoc(o, createProject(o), "ADR", "text"), "$.openVersionId");

        MvcResult first = comment(o, vid, "  first note  ").andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("first note"))
                .andExpect(jsonPath("$.authorType").value("HUMAN"))
                .andReturn();
        comment(o, vid, "second note").andExpect(status().isCreated());

        MvcResult page1 = mvc.perform(get("/api/v1/document-versions/" + vid + "/comments?limit=1")
                        .session(o.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(id(first, "$.id")))
                .andReturn();
        String next = id(page1, "$.nextCursor");
        assertThat(next).isNotBlank();
        mvc.perform(get("/api/v1/document-versions/" + vid + "/comments?limit=1&cursor=" + next).session(o.session()))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].body").value("second note"))
                .andExpect(jsonPath("$.nextCursor").value((Object) null));
        mvc.perform(get("/api/v1/document-versions/" + vid + "/comments?cursor=%25%25").session(o.session()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void commentRulesBlankBodyRolesAndAuditWithoutText() throws Exception {
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

        comment(o, vid, "   ").andExpect(status().isBadRequest());
        comment(v, vid, "viewers read only").andExpect(status().isForbidden());
        comment(x, vid, "outsider").andExpect(status().isNotFound());
        comment(o, UUID.randomUUID().toString(), "no such version").andExpect(status().isNotFound());
        comment(a, vid, "an approver may comment").andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorRef").value(approverId.toString()));

        mvc.perform(get("/api/v1/document-versions/" + vid + "/comments").session(v.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get("/api/v1/document-versions/" + vid + "/comments").session(x.session()))
                .andExpect(status().isNotFound());

        assertThat(count("select count(*) from audit_log where action = 'COMMENT_ADDED' and after_json->>'documentVersionId' = ?",
                vid)).isEqualTo(1);
        assertThat(count("""
                select count(*) from audit_log
                where action = 'COMMENT_ADDED' and after_json->>'documentVersionId' = ?
                  and (after_json::text like '%approver may%' or before_json::text like '%approver may%')
                """, vid)).isZero();
    }

    @Test
    void anApprovedVersionStillAcceptsComments() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String vid = id(createDoc(o, createProject(o), "ADR", "text"), "$.openVersionId");
        submit(o, vid).andExpect(status().isOk());
        mvc.perform(as(post("/api/v1/document-versions/" + vid + "/approve"), o)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedHash\":\"%s\"}".formatted(ContentHash.of("text"))))
                .andExpect(status().isOk());
        comment(o, vid, "post-approval note").andExpect(status().isCreated());
    }
}
