package io.github.bhushanupwanshi.api.tests.bdd.steps;

import io.cucumber.java.After;
import io.github.bhushanupwanshi.api.core.data.CreatedResourceRegistry;

/** Deletes whatever a scenario created, on the thread that created it. */
public class Hooks {

    @After
    public void deleteWhatTheScenarioCreated() {
        CreatedResourceRegistry.cleanUp();
    }
}
