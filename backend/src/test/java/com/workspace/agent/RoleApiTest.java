package com.workspace.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/** M2b: default roles, immutable config versions, activation, context-profile validation. Real PostgreSQL. */
class RoleApiTest extends IntegrationTestBase {

    private static final String PW = "correct-horse-battery";
    private static final String PROFILE =
            "{\"documents\":[{\"type\":\"API_CONTRACT\",\"mode\":\"FULL\",\"required\":true}],\"includeUnapproved\":false,\"recentMessages\":10}";
    private static final String PERMS = "[{\"code\":\"READ_DOCUMENT\",\"resourceType\":\"API_CONTRACT\"}]";

    private String createProject(Auth auth) throws Exception {
        String body = mvc.perform(as(post("/api/v1/projects"), auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"P\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String rolesOf(Auth a, String pid) throws Exception {
        return mvc.perform(get("/api/v1/projects/" + pid + "/roles").session(a.session()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String roleId(Auth a, String pid, String code) throws Exception {
        List<String> ids = JsonPath.read(rolesOf(a, pid), "$.items[?(@.code=='" + code + "')].id");
        return ids.get(0);
    }

    private ResultActions newVersion(Auth a, String rid, String prompt, String profile, String perms) throws Exception {
        return mvc.perform(as(post("/api/v1/roles/" + rid + "/config-versions"), a)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"systemPrompt\":\"%s\",\"contextProfile\":%s,\"permissions\":%s}".formatted(prompt, profile, perms)));
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    @Test
    void newProjectHasTheFiveDefaultRoles_eachWithActiveVersionOne() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String pid = createProject(o);

        String body = rolesOf(o, pid);
        List<String> codes = JsonPath.read(body, "$.items[*].code");
        assertThat(codes).containsExactlyInAnyOrder("ARCHITECT", "BACKEND", "FRONTEND", "QA", "REVIEWER");
        List<Boolean> builtin = JsonPath.read(body, "$.items[*].isBuiltin");
        assertThat(builtin).containsOnly(true);
        List<Integer> active = JsonPath.read(body, "$.items[*].activeConfigVersion.versionNo");
        assertThat(active).containsOnly(1);

        String rid = roleId(o, pid, "ARCHITECT");
        mvc.perform(get("/api/v1/roles/" + rid + "/config-versions").session(o.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].isActive").value(true))
                .andExpect(jsonPath("$.items[0].systemPrompt").value(org.hamcrest.Matchers.containsString("Software Architect")))
                .andExpect(jsonPath("$.items[0].contextProfile.documents.length()").value(4))
                .andExpect(jsonPath("$.items[0].contextProfile.includeUnapproved").value(false))
                .andExpect(jsonPath("$.items[0].permissions[?(@.code=='PROPOSE_DOCUMENT')].resourceType")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("ADR", "API_CONTRACT", "ARCHITECTURE")));
        assertThat(count("select count(*) from audit_log where action = 'ROLE_CREATED' and project_id = ?",
                UUID.fromString(pid))).isEqualTo(5);

        // the reviewer has no PROPOSE_DOCUMENT, and nobody has an APPROVE-like permission (CHECK constraint)
        assertThat(count("select count(*) from role_permission where permission_code not in ('READ_DOCUMENT','PROPOSE_DOCUMENT','COMMENT_ON_VERSION','READ_REPO','EXPORT_PATCH','PROPOSE_TASK')")).isZero();
    }

    @Test
    void configVersionsAreImmutableAndCreatingOneDoesNotActivateIt() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String pid = createProject(o);
        String rid = roleId(o, pid, "BACKEND");

        newVersion(o, rid, "second prompt", PROFILE, PERMS).andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNo").value(2))
                .andExpect(jsonPath("$.isActive").value(false));
        mvc.perform(get("/api/v1/roles/" + rid).session(o.session()))
                .andExpect(jsonPath("$.activeConfigVersion.versionNo").value(1));

        mvc.perform(as(post("/api/v1/roles/" + rid + "/config-versions/2/activate"), o)).andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(true)).andExpect(jsonPath("$.versionNo").value(2));
        mvc.perform(get("/api/v1/roles/" + rid).session(o.session()))
                .andExpect(jsonPath("$.activeConfigVersion.versionNo").value(2));
        mvc.perform(as(post("/api/v1/roles/" + rid + "/config-versions/2/activate"), o)).andExpect(status().isOk()); // idempotent
        mvc.perform(as(post("/api/v1/roles/" + rid + "/config-versions/9/activate"), o)).andExpect(status().isNotFound());

        assertThat(count("select count(*) from audit_log where action = 'ROLE_CONFIG_CREATED' and entity_id = ?", UUID.fromString(rid))).isEqualTo(1);
        assertThat(count("select count(*) from audit_log where action = 'ROLE_CONFIG_ACTIVATED' and entity_id = ?", UUID.fromString(rid))).isEqualTo(1);

        UUID rolePk = UUID.fromString(rid);
        assertThatThrownBy(() -> jdbc.update("update role_config_version set system_prompt = 'x' where role_id = ?", rolePk))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("delete from role_config_version where role_id = ?", rolePk))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("update role_permission set resource_type = 'ADR'"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void contextProfileMustBeCoveredByReadDocument() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String pid = createProject(o);
        String rid = roleId(o, pid, "QA");
        String adr = "{\"documents\":[{\"type\":\"ADR\",\"mode\":\"FULL\",\"required\":false}],\"includeUnapproved\":false,\"recentMessages\":5}";

        newVersion(o, rid, "p", adr, PERMS).andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("CONTEXT_PROFILE_EXCEEDS_PERMISSION"))
                .andExpect(jsonPath("$.documentType").value("ADR"));
        newVersion(o, rid, "p", adr, "[{\"code\":\"READ_DOCUMENT\",\"resourceType\":\"*\"}]").andExpect(status().isCreated());
        newVersion(o, rid, "p", adr, "[{\"code\":\"READ_DOCUMENT\",\"resourceType\":\"ADR\"}]").andExpect(status().isCreated());
        // a different permission code does not grant reading
        newVersion(o, rid, "p", adr, "[{\"code\":\"READ_REPO\",\"resourceType\":\"*\"}]").andExpect(status().is(422));
        assertThat(count("select count(*) from role_config_version where role_id = ?", UUID.fromString(rid))).isEqualTo(3);
    }

    @Test
    void invalidProfilesAndPermissionsAreRejected() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String pid = createProject(o);
        String rid = roleId(o, pid, "QA");
        String doc = "{\"type\":\"API_CONTRACT\",\"mode\":\"FULL\",\"required\":true}";

        newVersion(o, rid, "p", "{\"documents\":[],\"includeUnapproved\":true,\"recentMessages\":5}", PERMS).andExpect(status().isBadRequest());
        newVersion(o, rid, "p", "{\"documents\":[],\"includeUnapproved\":false,\"recentMessages\":0}", PERMS).andExpect(status().isBadRequest());
        newVersion(o, rid, "p", "{\"documents\":[],\"includeUnapproved\":false,\"recentMessages\":101}", PERMS).andExpect(status().isBadRequest());
        newVersion(o, rid, "p", "{\"documents\":[" + doc + "," + doc + "],\"includeUnapproved\":false,\"recentMessages\":5}", PERMS).andExpect(status().isBadRequest());
        newVersion(o, rid, "p", "{\"documents\":[{\"type\":\"NOPE\",\"mode\":\"FULL\"}],\"includeUnapproved\":false,\"recentMessages\":5}", PERMS).andExpect(status().isBadRequest());
        newVersion(o, rid, "p", "{\"documents\":[{\"type\":\"ADR\",\"mode\":\"HALF\"}],\"includeUnapproved\":false,\"recentMessages\":5}", PERMS).andExpect(status().isBadRequest());
        newVersion(o, rid, "p", PROFILE, "[{\"code\":\"APPROVE\",\"resourceType\":\"*\"}]").andExpect(status().isBadRequest());
        newVersion(o, rid, "p", PROFILE, "[{\"code\":\"READ_DOCUMENT\",\"resourceType\":\"BOGUS\"}]").andExpect(status().isBadRequest());
        newVersion(o, rid, "p", PROFILE, "[{\"code\":\"READ_DOCUMENT\",\"resourceType\":\"*\"},{\"code\":\"READ_DOCUMENT\",\"resourceType\":\"*\"}]").andExpect(status().isBadRequest());
        newVersion(o, rid, "   ", PROFILE, PERMS).andExpect(status().isBadRequest());
        assertThat(count("select count(*) from role_config_version where role_id = ?", UUID.fromString(rid))).isEqualTo(1);
    }

    @Test
    void customRoleStartsWithVersionOneActive_andCodesAreUnique() throws Exception {
        createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        String pid = createProject(o);
        String body = "{\"code\":\"DEVOPS\",\"name\":\"DevOps\",\"systemPrompt\":\"You do devops\",\"contextProfile\":%s,\"permissions\":%s}"
                .formatted(PROFILE, PERMS);

        mvc.perform(as(post("/api/v1/projects/" + pid + "/roles"), o).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("DEVOPS"))
                .andExpect(jsonPath("$.isBuiltin").value(false))
                .andExpect(jsonPath("$.activeConfigVersion.versionNo").value(1));
        mvc.perform(as(post("/api/v1/projects/" + pid + "/roles"), o).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_ROLE"));
        mvc.perform(as(post("/api/v1/projects/" + pid + "/roles"), o).contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("DEVOPS", "bad code")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyOwnersManageRoles_outsidersSeeNotFound() throws Exception {
        createUser("o@example.com", PW);
        UUID approverId = createUser("a@example.com", PW);
        createUser("x@example.com", PW);
        Auth o = login("o@example.com", PW);
        Auth a = login("a@example.com", PW);
        Auth x = login("x@example.com", PW);
        String pid = createProject(o);
        jdbc.update("insert into project_member(project_id, user_id, role) values (?, ?, 'APPROVER')", UUID.fromString(pid), approverId);
        String rid = roleId(o, pid, "QA");

        mvc.perform(get("/api/v1/projects/" + pid + "/roles").session(a.session())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/projects/" + pid + "/roles").session(x.session())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/roles/" + rid).session(a.session())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/roles/" + rid).session(x.session())).andExpect(status().isNotFound());
        newVersion(a, rid, "p", PROFILE, PERMS).andExpect(status().isForbidden());
        newVersion(x, rid, "p", PROFILE, PERMS).andExpect(status().isNotFound());
        mvc.perform(as(post("/api/v1/roles/" + rid + "/config-versions/1/activate"), x)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/roles/" + UUID.randomUUID()).session(o.session())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/projects/" + pid + "/roles")).andExpect(status().isUnauthorized());
    }

    @Test
    void projectsCreatedBeforeM2bGetTheirDefaultRolesOnFirstUse() throws Exception {
        UUID owner = createUser("o@example.com", PW);
        Auth o = login("o@example.com", PW);
        UUID pid = UUID.randomUUID();
        jdbc.update("insert into project(id, name, created_by) values (?, 'legacy', ?)", pid, owner);
        jdbc.update("insert into project_member(project_id, user_id, role) values (?, ?, 'OWNER')", pid, owner);

        assertThat((List<?>) JsonPath.read(rolesOf(o, pid.toString()), "$.items")).hasSize(5);
        assertThat((List<?>) JsonPath.read(rolesOf(o, pid.toString()), "$.items")).hasSize(5); // idempotent
        assertThat(count("select count(*) from role_definition where project_id = ?", pid)).isEqualTo(5);
    }
}
