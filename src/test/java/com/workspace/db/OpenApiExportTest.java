package com.workspace.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.workspace.support.IntegrationTestBase;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Regenerates docs/api/openapi.yaml from the controllers on every test run (brief section 12).
 * CI should follow the test run with:  git diff --exit-code docs/api/openapi.yaml
 * so an uncommitted contract change fails the build.
 */
@TestPropertySource(properties = "springdoc.api-docs.enabled=true")
class OpenApiExportTest extends IntegrationTestBase {

    @Test
    void exportOpenApiContract() throws Exception {
        String yaml = mvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(yaml).contains("/api/v1/projects").contains("/api/v1/auth/login").contains("/api/v1/me");

        Path out = Path.of("docs", "api", "openapi.yaml");
        Files.createDirectories(out.getParent());
        Files.writeString(out, yaml);
    }
}
