package com.workspace.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.workspace.support.IntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** ADR-002: creating and updating a project writes audit rows in the same transaction. */
class ProjectAuditTest extends IntegrationTestBase {

    @Test
    void createAndUpdateWriteAuditRows() throws Exception {
        UUID userId = createUser("o@example.com", "correct-horse-battery");
        Auth o = login("o@example.com", "correct-horse-battery");
        String body = mvc.perform(as(post("/api/v1/projects"), o).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alpha\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID pid = UUID.fromString(JsonPath.read(body, "$.id"));
        mvc.perform(as(patch("/api/v1/projects/" + pid), o).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Beta\"}"))
                .andExpect(status().isOk());

        String created = jdbc.queryForObject(
                "select after_json->>'name' from audit_log where entity_id = ? and action = 'PROJECT_CREATED'",
                String.class, pid);
        assertThat(created).isEqualTo("Alpha");
        String before = jdbc.queryForObject(
                "select before_json->>'name' from audit_log where entity_id = ? and action = 'PROJECT_UPDATED'",
                String.class, pid);
        String after = jdbc.queryForObject(
                "select after_json->>'name' from audit_log where entity_id = ? and action = 'PROJECT_UPDATED'",
                String.class, pid);
        assertThat(before).isEqualTo("Alpha");
        assertThat(after).isEqualTo("Beta");
        String actor = jdbc.queryForObject(
                "select actor_id from audit_log where entity_id = ? and action = 'PROJECT_CREATED'", String.class, pid);
        assertThat(actor).isEqualTo(userId.toString());
    }
}
