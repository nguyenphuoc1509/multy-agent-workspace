package com.workspace.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.workspace.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** Own Spring context: the runner executes once at startup against an empty app_user table. */
@TestPropertySource(properties = {
        "workspace.bootstrap.email=Boot@Example.com",
        "workspace.bootstrap.password=correct-horse-battery"
})
class FirstUserBootstrapTest extends IntegrationTestBase {

    @Test
    void firstUserIsCreatedFromEnvironmentAndCanLogIn() throws Exception {
        Auth auth = login("boot@example.com", "correct-horse-battery");
        mvc.perform(get("/api/v1/me").session(auth.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("boot@example.com"))
                .andExpect(jsonPath("$.displayName").value("Owner"));
    }
}
