package io.github.bhushanupwanshi.api.tests.bdd.runners;

import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;

/**
 * Runs the feature files through TestNG, so Cucumber results land in the same Surefire
 * and Allure reports as everything else.
 *
 * <p>mvn test -pl api-tests -Dsuite=bdd
 */
@CucumberOptions(
        features = "classpath:features",
        glue = "io.github.bhushanupwanshi.api.tests.bdd.steps",
        plugin = {
            "pretty",
            "summary",
            "html:target/cucumber-report.html",
            "io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm"
        })
public class CucumberTestNgRunner extends AbstractTestNGCucumberTests {
}
