package io.github.bhushanupwanshi.api.tests.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.assertj.core.api.SoftAssertions;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * The datasets are code, so they get tests.
 *
 * <p>A typo in a JSON case otherwise shows up as a confusing failure inside an unrelated
 * test, or worse, as a case that quietly never runs. Needs no network, so it runs in
 * every suite, smoke included.
 */
public class DatasetContractTest {

    private static final List<String> ENVIRONMENTS = List.of("dev", "qa", "prod");

    @DataProvider(name = "files")
    public Object[][] everyDatasetFile() throws IOException, URISyntaxException {
        URL root = getClass().getClassLoader().getResource("testdata");
        assertThat(root).as("testdata on the classpath").isNotNull();
        Path base = Path.of(root.toURI());
        try (Stream<Path> files = Files.walk(base)) {
            return files.filter(p -> p.toString().endsWith(".json"))
                    .map(p -> new Object[] {base.relativize(p).toString().replace('\\', '/')})
                    .toArray(Object[][]::new);
        }
    }

    private static Class<? extends DatasetCase> caseTypeFor(String file) {
        return file.endsWith("booking-search.json") ? SearchCase.class : ApiTestCase.class;
    }

    @Test(dataProvider = "files", groups = {"regression", "smoke", "hermetic"})
    public void everyCaseIsWellFormed(String file) {
        String env = file.substring(0, file.indexOf('/'));
        String name = file.substring(file.indexOf('/') + 1, file.length() - ".json".length());
        TestCaseFile<? extends DatasetCase> dataset =
                Datasets.file(name, caseTypeFor(file), env.equals("default") ? "__none__" : env);

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(dataset.dataset()).as("%s: dataset name matches the file name", file).isEqualTo(name);
        softly.assertThat(dataset.cases()).as("%s: has cases", file).isNotEmpty();

        Set<String> ids = new HashSet<>();
        for (DatasetCase c : dataset.cases()) {
            softly.assertThat(c.id()).as("%s: a case has an id", file).isNotBlank();
            softly.assertThat(ids.add(c.id())).as("%s: id %s is unique", file, c.id()).isTrue();
            softly.assertThat(c.description()).as("%s %s: has a description", file, c.id()).isNotBlank();
            if (c.environments() != null) {
                softly.assertThat(ENVIRONMENTS).as("%s %s: environments are known", file, c.id())
                        .containsAll(c.environments());
            }
            if (c instanceof ApiTestCase api) {
                softly.assertThat(api.request()).as("%s %s: has a request", file, c.id()).isNotNull();
                softly.assertThat(api.expect()).as("%s %s: has an expect block", file, c.id()).isNotNull();
                if (api.expect() != null) {
                    softly.assertThat(api.expect().status()).as("%s %s: expect.status", file, c.id()).isNotNull();
                    if (api.expect().schema() != null) {
                        softly.assertThat(getClass().getClassLoader().getResource(api.expect().schema()))
                                .as("%s %s: schema %s exists", file, c.id(), api.expect().schema()).isNotNull();
                    }
                }
                if (api.isTagged("known-defect")) {
                    softly.assertThat(api.description()).as("%s %s: a known defect says so", file, c.id())
                            .startsWith("KNOWN DEFECT");
                }
            }
            if (c instanceof SearchCase search) {
                softly.assertThat(search.query()).as("%s %s: has a query", file, c.id()).isNotEmpty();
                softly.assertThat(search.expectSeed() != null || search.expectEmpty() != null)
                        .as("%s %s: says what it expects", file, c.id()).isTrue();
            }
        }
        softly.assertAll();
    }

    @Test(groups = {"regression", "smoke", "hermetic"})
    public void anEnvironmentFileReplacesTheDefaultRatherThanMergingIntoIt() {
        assertThat(Datasets.resolve("booking-crud", "qa")).isEqualTo("testdata/qa/booking-crud.json");
        assertThat(Datasets.resolve("booking-crud", "prod")).isEqualTo("testdata/default/booking-crud.json");

        List<String> qaIds = Datasets.file("booking-crud", ApiTestCase.class, "qa").cases().stream()
                .map(DatasetCase::id).toList();
        assertThat(qaIds).allMatch(id -> id.startsWith("BC-QA-"));
    }

    @Test(groups = {"regression", "smoke", "hermetic"})
    public void aCaseLimitedToSomeEnvironmentsIsSkippedElsewhere() {
        ApiTestCase devAndQaOnly = Datasets.file("booking-validation", ApiTestCase.class, "prod").cases().stream()
                .filter(c -> c.id().equals("BV-17")).findFirst().orElseThrow();

        assertThat(devAndQaOnly.runsIn("dev")).isTrue();
        assertThat(devAndQaOnly.runsIn("prod")).isFalse();
    }
}
