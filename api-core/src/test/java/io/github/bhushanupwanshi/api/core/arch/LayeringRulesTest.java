package io.github.bhushanupwanshi.api.core.arch;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import io.github.bhushanupwanshi.api.core.transport.ApiResponse;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * The layer rules from the README, as a build failure rather than a convention.
 *
 * <pre>
 * transport  →  nothing above it
 * clients    →  transport, models, serialization
 * flows      →  clients, models, assertions   (never transport, except the response envelope)
 * models     →  nothing in the framework
 * </pre>
 *
 * <p>Scoped to {@code src/main}: that is what the published artifact contains, and what a
 * consumer inherits. The framework's own tests use TestNG and WireMock, which is fine,
 * because test-scoped dependencies are not transitive.
 *
 * <p>Seen failing before it was trusted: adding {@code import ...transport.Specs} to
 * {@code BookingFlow} and using it fails {@link #flowsDoNotReachIntoTransport} with the
 * offending class and line. A guard nobody has watched fail is not known to work.
 */
public class LayeringRulesTest {

    private static final String ROOT = "io.github.bhushanupwanshi.api.core";

    private JavaClasses main;

    @BeforeClass(alwaysRun = true)
    public void importMainClasses() {
        main = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .importPackages(ROOT);
    }

    private void check(ArchRule rule) {
        rule.check(main);
    }

    @Test
    public void noTestFrameworkInThePublishedArtifact() {
        check(noClasses().should().dependOnClassesThat()
                .resideInAnyPackage("org.testng..", "io.cucumber..", "org.junit..")
                .because("api-core is a library; a consumer must not inherit a test runner"));
    }

    @Test
    public void modelsDependOnNothingInTheFramework() {
        check(noClasses().that().resideInAPackage(ROOT + ".models..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        ROOT + ".clients..", ROOT + ".flows..", ROOT + ".transport..",
                        ROOT + ".assertions..", ROOT + ".data..", ROOT + ".config..")
                .because("models are plain data; anything can use them, they use nothing"));
    }

    @Test
    public void flowsDoNotReachIntoTransport() {
        check(noClasses().that().resideInAPackage(ROOT + ".flows..")
                .should().dependOnClassesThat(
                        resideInAPackage(ROOT + ".transport..").and(not(equivalentTo(ApiResponse.class))))
                .because("flows describe business operations; specs, retries, filters and"
                        + " tokens belong to the clients beneath them"));
    }

    @Test
    public void transportDoesNotKnowAboutAnythingAboveIt() {
        check(noClasses().that().resideInAPackage(ROOT + ".transport..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        ROOT + ".clients..", ROOT + ".flows..", ROOT + ".assertions..", ROOT + ".data..")
                .because("transport is the bottom layer"));
    }

    @Test
    public void clientsDoNotDependOnFlowsOrAssertions() {
        check(noClasses().that().resideInAPackage(ROOT + ".clients..")
                .should().dependOnClassesThat().resideInAnyPackage(ROOT + ".flows..", ROOT + ".assertions..")
                .because("a client is one HTTP call and must not decide whether it passed"));
    }

    @Test
    public void noCyclesBetweenPackages() {
        check(slices().matching(ROOT + ".(*)..").should().beFreeOfCycles());
    }
}
