package com.workspace.agent.application;

import static com.workspace.agent.published.DocumentMode.FULL;
import static com.workspace.agent.published.DocumentMode.SUMMARY;
import static com.workspace.agent.published.PermissionCode.COMMENT_ON_VERSION;
import static com.workspace.agent.published.PermissionCode.EXPORT_PATCH;
import static com.workspace.agent.published.PermissionCode.PROPOSE_DOCUMENT;
import static com.workspace.agent.published.PermissionCode.PROPOSE_TASK;
import static com.workspace.agent.published.PermissionCode.READ_DOCUMENT;
import static com.workspace.agent.published.PermissionCode.READ_REPO;
import static com.workspace.document.published.DocumentType.ADR;
import static com.workspace.document.published.DocumentType.API_CONTRACT;
import static com.workspace.document.published.DocumentType.ARCHITECTURE;
import static com.workspace.document.published.DocumentType.REQUIREMENTS;

import com.workspace.agent.published.ContextDocument;
import com.workspace.agent.published.ContextProfile;
import com.workspace.agent.published.Permission;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.ClassPathResource;

/** The five default roles (brief section 7). Prompts live in src/main/resources/roles/*.md for easy review. */
final class RoleTemplates {

    record Template(String code, String name, String systemPrompt, ContextProfile profile, List<Permission> permissions) {}

    private RoleTemplates() {}

    static List<Template> defaults() {
        return List.of(
                new Template("ARCHITECT", "Architect", prompt("ARCHITECT"),
                        profile(20 + 10,
                                doc(REQUIREMENTS, FULL, false), doc(ARCHITECTURE, FULL, false),
                                doc(API_CONTRACT, FULL, false), doc(ADR, FULL, false)),
                        List.of(star(READ_DOCUMENT), perm(PROPOSE_DOCUMENT, "ARCHITECTURE"),
                                perm(PROPOSE_DOCUMENT, "API_CONTRACT"), perm(PROPOSE_DOCUMENT, "ADR"),
                                star(COMMENT_ON_VERSION), star(READ_REPO), star(PROPOSE_TASK))),
                new Template("BACKEND", "Backend Developer", prompt("BACKEND"),
                        profile(20, doc(ARCHITECTURE, FULL, true), doc(API_CONTRACT, FULL, true),
                                doc(REQUIREMENTS, SUMMARY, false), doc(ADR, SUMMARY, false)),
                        List.of(perm(READ_DOCUMENT, "REQUIREMENTS"), perm(READ_DOCUMENT, "ARCHITECTURE"),
                                perm(READ_DOCUMENT, "API_CONTRACT"), perm(READ_DOCUMENT, "ADR"),
                                star(COMMENT_ON_VERSION), star(READ_REPO), star(EXPORT_PATCH))),
                new Template("FRONTEND", "Frontend Developer", prompt("FRONTEND"),
                        profile(20, doc(API_CONTRACT, FULL, true), doc(REQUIREMENTS, SUMMARY, false),
                                doc(ARCHITECTURE, SUMMARY, false)),
                        List.of(perm(READ_DOCUMENT, "REQUIREMENTS"), perm(READ_DOCUMENT, "API_CONTRACT"),
                                perm(READ_DOCUMENT, "ARCHITECTURE"), star(COMMENT_ON_VERSION), star(READ_REPO),
                                star(EXPORT_PATCH))),
                new Template("REVIEWER", "Code Reviewer", prompt("REVIEWER"),
                        profile(20, doc(ARCHITECTURE, FULL, true), doc(API_CONTRACT, FULL, true),
                                doc(ADR, FULL, false), doc(REQUIREMENTS, SUMMARY, false)),
                        List.of(star(READ_DOCUMENT), star(COMMENT_ON_VERSION), star(READ_REPO), star(PROPOSE_TASK))),
                new Template("QA", "QA Engineer", prompt("QA"),
                        profile(20, doc(REQUIREMENTS, FULL, true), doc(API_CONTRACT, FULL, true),
                                doc(ARCHITECTURE, SUMMARY, false)),
                        List.of(perm(READ_DOCUMENT, "REQUIREMENTS"), perm(READ_DOCUMENT, "API_CONTRACT"),
                                star(COMMENT_ON_VERSION), star(READ_REPO), star(PROPOSE_TASK))));
    }

    private static ContextProfile profile(int recent, ContextDocument... docs) {
        return new ContextProfile(List.of(docs), false, recent);
    }

    private static ContextDocument doc(com.workspace.document.published.DocumentType t,
                                       com.workspace.agent.published.DocumentMode m, boolean required) {
        return new ContextDocument(t, m, required);
    }

    private static Permission star(com.workspace.agent.published.PermissionCode c) {
        return new Permission(c, "*");
    }

    private static Permission perm(com.workspace.agent.published.PermissionCode c, String type) {
        return new Permission(c, type);
    }

    private static String prompt(String code) {
        try (var in = new ClassPathResource("roles/" + code + ".md").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
