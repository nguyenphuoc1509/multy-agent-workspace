package com.workspace.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.domain.JavaClasses;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Brief section 4: ArchUnit rules 1-6 plus the allowed-dependency matrix. Fails the build on violation. */
class ArchitectureTest {

    private static final String ROOT = "com.workspace";

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages(ROOT);

    /**
     * Allowed DIRECT dependencies per module (brief section 4). ASSUMPTION: every module may also use
     * the shared kernel (listed explicitly here); the brief only names it for identity. See M0 report.
     */
    private static final Map<String, Set<String>> ALLOWED = new LinkedHashMap<>();

    static {
        ALLOWED.put("shared", Set.of());
        ALLOWED.put("audit", Set.of("shared"));
        ALLOWED.put("event", Set.of("shared"));
        ALLOWED.put("identity", Set.of("shared"));
        ALLOWED.put("project", Set.of("identity", "shared"));
        ALLOWED.put("document", Set.of("project", "audit", "event", "shared"));
        ALLOWED.put("credential", Set.of("project", "audit", "shared"));
        ALLOWED.put("agent", Set.of("project", "credential", "audit", "event", "shared"));
        ALLOWED.put("attention", Set.of("project", "event", "shared"));
        ALLOWED.put("conversation", Set.of("agent", "audit", "event", "shared"));
        ALLOWED.put("llm", Set.of("shared"));
        ALLOWED.put("context", Set.of("document", "agent", "shared"));
        ALLOWED.put("run", Set.of("context", "llm", "agent", "credential", "conversation", "audit", "event", "shared"));
        ALLOWED.put("task", Set.of("run", "context", "agent", "document", "audit", "event", "shared"));
        ALLOWED.put("workflow", Set.of("document", "conversation", "agent", "project", "audit", "event", "shared"));
    }

    @Test
    void sanityCheckImportedClasses() {
        // guards against a silently empty import (e.g. wrong root package) making every rule pass vacuously
        if (CLASSES.size() < 10) throw new AssertionError("ArchUnit imported only " + CLASSES.size() + " classes");
    }

    @Test
    void rule1_noCyclesBetweenModules() {
        slices().matching(ROOT + ".(*)..").should().beFreeOfCycles().check(CLASSES);
    }

    @Test
    void rule2_crossModuleAccessOnlyThroughPublished() {
        for (String m : ALLOWED.keySet()) {
            DescribedPredicate<com.tngtech.archunit.core.domain.JavaClass> internalsOfM =
                    resideInAPackage(ROOT + "." + m + "..")
                            .and(DescribedPredicate.not(resideInAPackage(ROOT + "." + m + ".published..")));
            noClasses().that().resideOutsideOfPackage(ROOT + "." + m + "..")
                    .and().resideInAPackage(ROOT + "..")
                    .should().dependOnClassesThat(internalsOfM)
                    .allowEmptyShould(true)
                    .as("only " + m + ".published may be used outside module " + m)
                    .check(CLASSES);
        }
    }

    @Test
    void allowedDependencyMatrix() {
        for (Map.Entry<String, Set<String>> e : ALLOWED.entrySet()) {
            List<String> forbidden = new ArrayList<>();
            for (String other : ALLOWED.keySet()) {
                if (!other.equals(e.getKey()) && !e.getValue().contains(other)) forbidden.add(ROOT + "." + other + "..");
            }
            if (forbidden.isEmpty()) continue;
            noClasses().that().resideInAPackage(ROOT + "." + e.getKey() + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(forbidden.toArray(String[]::new))
                    .allowEmptyShould(true)
                    .as("module " + e.getKey() + " may only depend on " + e.getValue())
                    .check(CLASSES);
        }
    }

    @Test
    void rule3_domainDoesNotDependOnWebInfrastructureOrProviderSdks() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..web..", "..infrastructure..", "com.anthropic..", "org.springframework.web..")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void rule4_webDoesNotCallInfrastructureDirectly() {
        noClasses().that().resideInAPackage("..web..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void rule5_onlyAnthropicAdapterTouchesProviderSdk_onlyCredentialInfrastructureTouchesCrypto() {
        noClasses().that().resideOutsideOfPackage(ROOT + ".llm.adapter.anthropic..")
                .should().dependOnClassesThat().resideInAnyPackage("com.anthropic..")
                .allowEmptyShould(true)
                .check(CLASSES);
        noClasses().that().resideOutsideOfPackage(ROOT + ".credential.infrastructure..")
                .should().dependOnClassesThat().resideInAnyPackage("javax.crypto..")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void rule6_agentExecutingModulesCanNeverReachWorkflow() {
        noClasses().that().resideInAnyPackage(
                        ROOT + ".run..", ROOT + ".llm..", ROOT + ".context..", ROOT + ".conversation..")
                .should().dependOnClassesThat().resideInAPackage(ROOT + ".workflow..")
                .allowEmptyShould(true)
                .check(CLASSES);
    }
}
