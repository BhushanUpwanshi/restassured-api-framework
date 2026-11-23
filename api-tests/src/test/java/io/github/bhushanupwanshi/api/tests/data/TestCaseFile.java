package io.github.bhushanupwanshi.api.tests.data;

import java.util.List;

/** A dataset file: its name and its cases. */
public record TestCaseFile<T extends DatasetCase>(String dataset, List<T> cases) {
}
