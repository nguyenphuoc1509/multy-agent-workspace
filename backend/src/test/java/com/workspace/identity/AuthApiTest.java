package com.workspace.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.workspace.support.IntegrationTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class AuthApiTest extends IntegrationTestBase {

    private static String loginJson(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    @Test
    void unauthenticatedMeIsProblemJson401() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void anyGetSetsXsrfCookieReadableByScript() throws Exception {
        Cookie c = freshCsrf();
        assertThat(c.getValue()).isNotBlank();
        assertThat(c.isHttpOnly()).as("XSRF-TOKEN must be readable by the SPA").isFalse();
    }

    @Test
    void loginWithoutCsrfIsForbidden() throws Exception {
        createUser("csrf@example.com", "correct-horse-battery");
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("csrf@example.com", "correct-horse-battery")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void loginThenMeReturnsTheUser() throws Exception {
        createUser("alice@example.com", "correct-horse-battery");
        Auth auth = login("alice@example.com", "correct-horse-battery");
        mvc.perform(get("/api/v1/me").session(auth.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.displayName").isNotEmpty())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void emailIsCaseInsensitive() throws Exception {
        createUser("case@example.com", "correct-horse-battery");
        login("CASE@Example.com", "correct-horse-battery");
    }

    @Test
    void loginRotatesTheCsrfToken() throws Exception {
        createUser("rotate@example.com", "correct-horse-battery");
        Cookie before = freshCsrf();
        MvcResult res = mvc.perform(withCsrf(post("/api/v1/auth/login"), before)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("rotate@example.com", "correct-horse-battery")))
                .andExpect(status().isOk()).andReturn();
        Cookie after = res.getResponse().getCookie("XSRF-TOKEN");
        assertThat(after).isNotNull();
        assertThat(after.getValue()).isNotBlank();
        assertThat(after.getValue()).isNotEqualTo(before.getValue());
    }

    @Test
    void wrongPasswordAndUnknownEmailLookIdentical() throws Exception {
        createUser("bob@example.com", "correct-horse-battery");
        Cookie csrf = freshCsrf();
        String wrongPassword = mvc.perform(withCsrf(post("/api/v1/auth/login"), csrf)
                        .contentType(MediaType.APPLICATION_JSON).content(loginJson("bob@example.com", "nope-nope-nope")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = mvc.perform(withCsrf(post("/api/v1/auth/login"), csrf)
                        .contentType(MediaType.APPLICATION_JSON).content(loginJson("ghost@example.com", "nope-nope-nope")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertThat(unknownEmail).isEqualTo(wrongPassword);
    }

    @Test
    void passwordLongerThan72BytesIsAnOrdinary401NotAServerError() throws Exception {
        createUser("long@example.com", "correct-horse-battery");
        Cookie csrf = freshCsrf();
        mvc.perform(withCsrf(post("/api/v1/auth/login"), csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("long@example.com", "x".repeat(200))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blankFieldsAreValidationErrors() throws Exception {
        Cookie csrf = freshCsrf();
        mvc.perform(withCsrf(post("/api/v1/auth/login"), csrf)
                        .contentType(MediaType.APPLICATION_JSON).content(loginJson("", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields").isArray());
    }

    @Test
    void repeatedFailuresAreThrottledEvenForTheCorrectPassword() throws Exception {
        createUser("throttle@example.com", "correct-horse-battery");
        Cookie csrf = freshCsrf();
        for (int i = 0; i < 5; i++) {
            mvc.perform(withCsrf(post("/api/v1/auth/login"), csrf)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson("throttle@example.com", "wrong-wrong-wrong")))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(withCsrf(post("/api/v1/auth/login"), csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("throttle@example.com", "correct-horse-battery")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_THROTTLED"));
    }

    @Test
    void logoutInvalidatesTheSession() throws Exception {
        createUser("carol@example.com", "correct-horse-battery");
        Auth auth = login("carol@example.com", "correct-horse-battery");
        mvc.perform(as(post("/api/v1/auth/logout"), auth)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me").session(auth.session())).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRequiresCsrf() throws Exception {
        createUser("dave@example.com", "correct-horse-battery");
        Auth auth = login("dave@example.com", "correct-horse-battery");
        mvc.perform(post("/api/v1/auth/logout").session(auth.session())).andExpect(status().isForbidden());
    }

    @Test
    void unknownRouteIsProblemJson404() throws Exception {
        createUser("erin@example.com", "correct-horse-battery");
        Auth auth = login("erin@example.com", "correct-horse-battery");
        mvc.perform(get("/api/v1/does-not-exist").session(auth.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
