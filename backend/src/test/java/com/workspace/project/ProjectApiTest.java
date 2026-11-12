package com.workspace.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.workspace.support.IntegrationTestBase;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ProjectApiTest extends IntegrationTestBase {

    private String createProject(Auth auth, String name) throws Exception {
        String body = mvc.perform(as(post("/api/v1/projects"), auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"description\":\"d\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.myRole").value("OWNER"))
                .andExpect(jsonPath("$.name").value(name))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void createListAndGet() throws Exception {
        createUser("owner@example.com", "correct-horse-battery");
        Auth auth = login("owner@example.com", "correct-horse-battery");
        String id = createProject(auth, "Alpha");

        mvc.perform(get("/api/v1/projects").session(auth.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(id))
                .andExpect(jsonPath("$.nextCursor").value((Object) null));

        mvc.perform(get("/api/v1/projects/" + id).session(auth.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alpha"))
                .andExpect(jsonPath("$.myRole").value("OWNER"))
                .andExpect(jsonPath("$.dailyTokenBudget").value((Object) null))
                .andExpect(jsonPath("$.gitRepoUrl").doesNotExist());
    }

    @Test
    void nonMemberGets404ForGetAndPatch_andProjectIsNotListed() throws Exception {
        createUser("a@example.com", "correct-horse-battery");
        createUser("b@example.com", "correct-horse-battery");
        Auth a = login("a@example.com", "correct-horse-battery");
        Auth b = login("b@example.com", "correct-horse-battery");
        String id = createProject(a, "Private");

        mvc.perform(get("/api/v1/projects/" + id).session(b.session()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(as(patch("/api/v1/projects/" + id), b).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"hijack\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/projects").session(b.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void nonExistentProjectLooksTheSameAsNonMember() throws Exception {
        createUser("c@example.com", "correct-horse-battery");
        Auth c = login("c@example.com", "correct-horse-battery");
        mvc.perform(get("/api/v1/projects/" + UUID.randomUUID()).session(c.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void viewerCanReadButNotUpdate() throws Exception {
        UUID ownerId = createUser("o@example.com", "correct-horse-battery");
        UUID viewerId = createUser("v@example.com", "correct-horse-battery");
        Auth owner = login("o@example.com", "correct-horse-battery");
        String id = createProject(owner, "Shared");
        jdbc.update("insert into project_member(project_id, user_id, role) values (?, ?, 'VIEWER')",
                UUID.fromString(id), viewerId);
        assertThat(ownerId).isNotEqualTo(viewerId);

        Auth viewer = login("v@example.com", "correct-horse-battery");
        mvc.perform(get("/api/v1/projects/" + id).session(viewer.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("VIEWER"));
        mvc.perform(as(patch("/api/v1/projects/" + id), viewer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"nope\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void ownerCanUpdate() throws Exception {
        createUser("u@example.com", "correct-horse-battery");
        Auth auth = login("u@example.com", "correct-horse-battery");
        String id = createProject(auth, "Before");
        mvc.perform(as(patch("/api/v1/projects/" + id), auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"After\",\"dailyTokenBudget\":1000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("After"))
                .andExpect(jsonPath("$.dailyTokenBudget").value(1000))
                .andExpect(jsonPath("$.description").value("d"));
    }

    @Test
    void validationErrorsListTheOffendingFields() throws Exception {
        createUser("val@example.com", "correct-horse-battery");
        Auth auth = login("val@example.com", "correct-horse-battery");
        mvc.perform(as(post("/api/v1/projects"), auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"dailyTokenBudget\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields[?(@.field=='name')]").isNotEmpty())
                .andExpect(jsonPath("$.fields[?(@.field=='dailyTokenBudget')]").isNotEmpty());
    }

    @Test
    void mutatingEndpointsRequireCsrf_andAuthentication() throws Exception {
        createUser("csrf2@example.com", "correct-horse-battery");
        Auth auth = login("csrf2@example.com", "correct-horse-battery");
        // authenticated but no CSRF header -> 403
        mvc.perform(post("/api/v1/projects").session(auth.session()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isForbidden());
        // valid CSRF but no session -> 401
        Cookie csrf = freshCsrf();
        mvc.perform(withCsrf(post("/api/v1/projects"), csrf).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        // reads need authentication too
        mvc.perform(get("/api/v1/projects")).andExpect(status().isUnauthorized());
    }

    @Test
    void cursorPaginationWalksAllProjectsExactlyOnce() throws Exception {
        createUser("page@example.com", "correct-horse-battery");
        Auth auth = login("page@example.com", "correct-horse-battery");
        Set<String> created = new HashSet<>();
        for (int i = 0; i < 5; i++) created.add(createProject(auth, "P" + i));

        List<String> seen = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            String url = "/api/v1/projects?limit=2" + (cursor == null ? "" : "&cursor=" + cursor);
            String body = mvc.perform(get(url).session(auth.session()))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            List<String> ids = JsonPath.read(body, "$.items[*].id");
            seen.addAll(ids);
            cursor = JsonPath.read(body, "$.nextCursor");
            pages++;
        } while (cursor != null && pages < 10);

        assertThat(pages).isEqualTo(3);
        assertThat(seen).hasSize(5).doesNotHaveDuplicates();
        assertThat(new HashSet<>(seen)).isEqualTo(created);
    }

    @Test
    void invalidLimitAndCursorAreValidationErrors() throws Exception {
        createUser("lim@example.com", "correct-horse-battery");
        Auth auth = login("lim@example.com", "correct-horse-battery");
        mvc.perform(get("/api/v1/projects?limit=0").session(auth.session()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/v1/projects?limit=201").session(auth.session()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/projects?cursor=garbage").session(auth.session()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
