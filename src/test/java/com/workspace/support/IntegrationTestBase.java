package com.workspace.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Real PostgreSQL via Testcontainers (triggers and constraints are part of the product; no H2). */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    static {
        POSTGRES.start(); // one container for the whole test JVM; Testcontainers' Ryuk removes it afterwards
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected PasswordEncoder encoder;

    /** Clean after (not before) each test so a fresh context's first-user bootstrap sees an empty table. */
    @AfterEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE project_member, project, app_user CASCADE");
    }

    protected record Auth(MockHttpSession session, Cookie xsrf) {}

    protected UUID createUser(String email, String password) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into app_user(id, email, password_hash, display_name) values (?, ?, ?, ?)",
                id, email, encoder.encode(password), "Test " + email);
        return id;
    }

    /** Any GET carries the XSRF-TOKEN cookie, even a 401 one. */
    protected Cookie freshCsrf() throws Exception {
        MvcResult r = mvc.perform(get("/api/v1/me")).andReturn();
        return Objects.requireNonNull(r.getResponse().getCookie("XSRF-TOKEN"), "XSRF-TOKEN cookie missing");
    }

    protected MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder b, Cookie xsrf) {
        return b.cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue());
    }

    protected Auth login(String email, String password) throws Exception {
        Cookie csrf = freshCsrf();
        MvcResult res = mvc.perform(withCsrf(post("/api/v1/auth/login"), csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) Objects.requireNonNull(res.getRequest().getSession(false));
        Cookie rotated = res.getResponse().getCookie("XSRF-TOKEN");
        return new Auth(session, rotated != null ? rotated : csrf);
    }

    /** Adds the session and CSRF cookie/header of a logged-in user to a request. */
    protected MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, Auth auth) {
        return withCsrf(b.session(auth.session()), auth.xsrf());
    }
}
