package com.workspace.document.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

/** Pure unit tests (no Spring, no DB) for the structural OpenAPI check used when submitting an API_CONTRACT. */
class ContractValidatorTest {

    private static final String VALID_31 = """
            openapi: 3.1.0
            info:
              title: T
              version: 1.0.0
            paths:
              /things:
                get:
                  operationId: listThings
                  responses:
                    "200":
                      description: ok
                      content:
                        application/json:
                          schema:
                            $ref: '#/components/schemas/Thing'
            components:
              schemas:
                Thing:
                  type: object
            """;

    private static List<ContractValidator.Issue> check(String yaml) {
        return ContractValidator.validate(yaml);
    }

    private static boolean has(List<ContractValidator.Issue> issues, int line, String fragment) {
        return issues.stream().anyMatch(i -> i.line() == line && i.message().contains(fragment));
    }

    @Test
    void validDocumentsPass() {
        assertThat(check(VALID_31)).isEmpty();
        assertThat(check(VALID_31.replace("3.1.0", "3.0.3"))).isEmpty();
        assertThat(check("openapi: 3.1.0\ninfo: {title: T, version: '1'}\ncomponents: {}\n")).isEmpty();
    }

    @Test
    void jsonIsAcceptedBecauseItIsYaml() {
        String json = "{\"openapi\":\"3.1.0\",\"info\":{\"title\":\"T\",\"version\":\"1\"},\"paths\":{}}";
        assertThat(check(json)).isEmpty();
    }

    @Test
    void yamlSyntaxErrorIsReportedWithALine() {
        List<ContractValidator.Issue> issues = check("openapi: 3.1.0\ninfo: [unclosed\npaths: {}\n");
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).message()).startsWith("Invalid YAML");
        assertThat(issues.get(0).line()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void topLevelMustBeAnObject() {
        assertThat(has(check("- a\n- b\n"), 1, "must be an object")).isTrue();
        assertThat(check("")).isNotEmpty();
    }

    @Test
    void missingTopLevelFieldsAreListed() {
        List<ContractValidator.Issue> issues = check("foo: bar\n");
        assertThat(issues).extracting(ContractValidator.Issue::message)
                .anyMatch(m -> m.contains("'openapi'"))
                .anyMatch(m -> m.contains("'info'"))
                .anyMatch(m -> m.contains("'paths', 'components' or 'webhooks'"));
    }

    @Test
    void versionMustBe30or31() {
        assertThat(has(check("openapi: \"2.0\"\ninfo: {title: T, version: '1'}\npaths: {}\n"), 1, "3.0.x or 3.1.x")).isTrue();
        assertThat(has(check("openapi: 3.0\ninfo: {title: T, version: '1'}\npaths: {}\n"), 1, "3.0.x or 3.1.x")).isTrue();
    }

    @Test
    void duplicateKeysAreReportedOnTheSecondOccurrence() {
        List<ContractValidator.Issue> issues = check("""
                openapi: 3.1.0
                info:
                  title: A
                  title: B
                  version: "1"
                paths: {}
                """);
        assertThat(has(issues, 4, "Duplicate key 'title'")).isTrue();
        assertThat(issues.stream().filter(i -> i.line() == 4).findFirst().orElseThrow().message())
                .contains("first used on line 3");
    }

    @Test
    void version30RequiresPathsAndResponses() {
        assertThat(check("openapi: 3.0.3\ninfo: {title: T, version: '1'}\n")).extracting(ContractValidator.Issue::message)
                .anyMatch(m -> m.contains("'paths'"));
        List<ContractValidator.Issue> issues = check("""
                openapi: 3.0.3
                info: {title: T, version: '1'}
                paths:
                  /a:
                    get:
                      summary: x
                """);
        assertThat(has(issues, 6, "has no 'responses'")).isTrue();
        // 3.1 does not require responses
        assertThat(check("""
                openapi: 3.1.0
                info: {title: T, version: '1'}
                paths:
                  /a:
                    get:
                      summary: x
                """)).isEmpty();
    }

    @Test
    void pathShapeAndUnknownFieldsAreChecked() {
        List<ContractValidator.Issue> issues = check("""
                openapi: 3.1.0
                info: {title: T, version: '1'}
                paths:
                  things:
                    get: {}
                  /b:
                    gett: {}
                """);
        assertThat(has(issues, 4, "must start with '/'")).isTrue();
        assertThat(has(issues, 7, "Unknown field 'gett'")).isTrue();
    }

    @Test
    void operationIdsMustBeUnique() {
        List<ContractValidator.Issue> issues = check("""
                openapi: 3.1.0
                info: {title: T, version: '1'}
                paths:
                  /a:
                    get:
                      operationId: same
                  /b:
                    get:
                      operationId: same
                """);
        assertThat(has(issues, 9, "Duplicate operationId 'same'")).isTrue();
    }

    @Test
    void localRefsMustResolveAndRemoteRefsAreRefused() {
        List<ContractValidator.Issue> issues = check("""
                openapi: 3.1.0
                info: {title: T, version: '1'}
                components:
                  schemas:
                    A:
                      $ref: '#/components/schemas/Missing'
                    B:
                      $ref: 'https://example.com/x.yaml'
                    C:
                      $ref: '#/components/schemas/B'
                """);
        assertThat(has(issues, 6, "Unresolved reference '#/components/schemas/Missing'")).isTrue();
        assertThat(has(issues, 8, "Only local references")).isTrue();
        assertThat(issues).hasSize(2); // C resolves
    }

    @Test
    void cyclicAliasesDoNotHangTheValidator() {
        Assertions.assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            List<ContractValidator.Issue> issues = check("""
                    openapi: 3.1.0
                    info: {title: T, version: '1'}
                    components:
                      schemas:
                        Node: &n
                          properties:
                            next: *n
                    """);
            assertThat(issues).isNotNull();
        });
    }

    @Test
    void errorListIsCapped() {
        StringBuilder sb = new StringBuilder("openapi: 3.1.0\ninfo: {title: T, version: '1'}\npaths:\n");
        for (int i = 0; i < 200; i++) sb.append("  bad").append(i).append(": {}\n");
        assertThat(check(sb.toString())).hasSize(50);
    }
}
