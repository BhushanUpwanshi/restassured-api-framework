package io.github.bhushanupwanshi.api.tests.arch;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.github.bhushanupwanshi.api.core.transport.ApiResponse;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * The consumer side of the layer rules.
 *
 * <p>Suites and step definitions use flows, clients, assertions and data. They never build
 * a request specification, run a retry loop or handle a token: that is the framework's
 * job, and a test that does it is a test that will break when the framework changes.
 */
public class TestLayeringRulesTest {

    private static final String TESTS = "io.github.bhushanupwanshi.api.tests";
    private static final String TRANSPORT = "io.github.bhushanupwanshi.api.core.transport..";

    private JavaClasses tests;

    // alwaysRun: the suites filter by group, and a configuration method without one is
    // otherwise skipped, leaving the rules to run against null.
    @BeforeClass(alwaysRun = true)
    public void importTestClasses() {
        tests = new ClassFileImporter().importPackages(TESTS);
    }

    @Test(groups = {"regression", "smoke", "hermetic"})
    public void suitesAndStepsStayOutOfTransport() {
        noClasses().that().resideInAPackage(TESTS + "..")
                .should().dependOnClassesThat(resideInAPackage(TRANSPORT).and(not(equivalentTo(ApiResponse.class))))
                .because("tests talk to the framework through flows and clients, not HTTP mechanics")
                .check(tests);
    }

    @Test(groups = {"regression", "smoke", "hermetic"})
    public void stepDefinitionsDoNotCallTestNgAndSuitesDoNotCallSteps() {
        noClasses().that().resideInAPackage(TESTS + ".bdd.steps..")
                .should().dependOnClassesThat().resideInAnyPackage("org.testng..")
                .because("Cucumber steps and TestNG suites are separate consumers of one library")
                .check(tests);

        noClasses().that().resideInAnyPackage(TESTS + ".booking..", TESTS + ".auth..")
                .should().dependOnClassesThat().resideInAPackage(TESTS + ".bdd..")
                .check(tests);
    }
}
