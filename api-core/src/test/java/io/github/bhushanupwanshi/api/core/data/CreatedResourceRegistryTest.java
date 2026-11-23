package io.github.bhushanupwanshi.api.core.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

/**
 * The cleanup ledger.
 *
 * <p>Deletions are recorded as lambdas appending to a list, so these tests assert on the
 * <em>order</em> and the <em>isolation</em> without any HTTP. Both properties are the kind
 * that look obviously fine by inspection and are wrong under parallelism.
 */
public class CreatedResourceRegistryTest {

    @AfterMethod(alwaysRun = true)
    public void clearLedger() {
        // The ledger is thread-local and this thread is reused across tests, so a test
        // that left entries behind would hand them to the next one.
        CreatedResourceRegistry.discard();
    }

    @Test
    public void deletesNewestFirst() {
        List<String> deleted = new java.util.ArrayList<>();

        CreatedResourceRegistry.record("booking 1", () -> deleted.add("booking 1"));
        CreatedResourceRegistry.record("booking 2", () -> deleted.add("booking 2"));
        CreatedResourceRegistry.record("booking 3", () -> deleted.add("booking 3"));

        CreatedResourceRegistry.cleanUp();

        // Reverse order: a resource created after another is removed before it. Costs
        // nothing here and stops being optional the moment anything is nested.
        assertThat(deleted).containsExactly("booking 3", "booking 2", "booking 1");
    }

    @Test
    public void reportsWhatItAttempted() {
        CreatedResourceRegistry.record("booking 7", () -> { });
        CreatedResourceRegistry.record("booking 8", () -> { });

        assertThat(CreatedResourceRegistry.cleanUp()).containsExactly("booking 8", "booking 7");
    }

    @Test
    public void emptiesTheLedgerAfterCleaningUp() {
        CreatedResourceRegistry.record("booking 1", () -> { });
        assertThat(CreatedResourceRegistry.pending()).isEqualTo(1);

        CreatedResourceRegistry.cleanUp();

        assertThat(CreatedResourceRegistry.pending()).isZero();
    }

    @Test
    public void cleaningUpNothingIsHarmless() {
        assertThat(CreatedResourceRegistry.cleanUp()).isEmpty();
    }

    /**
     * A failed delete must not stop the others.
     *
     * <p>If one throw aborted the loop, a single stale id would leave every later resource
     * behind, and the leak would grow run on run.
     */
    @Test
    public void carriesOnAfterADeleteFails() {
        List<String> deleted = new java.util.ArrayList<>();

        CreatedResourceRegistry.record("first", () -> deleted.add("first"));
        CreatedResourceRegistry.record("explodes", () -> {
            throw new IllegalStateException("already gone");
        });
        CreatedResourceRegistry.record("last", () -> deleted.add("last"));

        CreatedResourceRegistry.cleanUp();

        assertThat(deleted).containsExactly("last", "first");
    }

    /**
     * A failing delete must not throw out of teardown.
     *
     * <p>A teardown that throws replaces the test's own result with a cleanup error, so the
     * reader sees a plumbing failure instead of what the test actually found.
     */
    @Test
    public void neverThrowsOutOfCleanUp() {
        CreatedResourceRegistry.record("explodes", () -> {
            throw new IllegalStateException("already gone");
        });

        assertThat(CreatedResourceRegistry.cleanUp()).containsExactly("explodes");
    }

    @Test
    public void clearsTheLedgerEvenWhenEveryDeleteFails() {
        CreatedResourceRegistry.record("a", () -> {
            throw new IllegalStateException("no");
        });
        CreatedResourceRegistry.record("b", () -> {
            throw new IllegalStateException("no");
        });

        CreatedResourceRegistry.cleanUp();

        // Otherwise the same doomed deletes are retried in every later test.
        assertThat(CreatedResourceRegistry.pending()).isZero();
    }

    @Test
    public void discardRemovesEntriesWithoutDeleting() {
        List<String> deleted = new java.util.ArrayList<>();
        CreatedResourceRegistry.record("booking 1", () -> deleted.add("booking 1"));

        CreatedResourceRegistry.discard();

        assertThat(CreatedResourceRegistry.pending()).isZero();
        assertThat(deleted).isEmpty();
    }

    @Test
    public void recordsAnIdWithADeletingConsumer() {
        List<Integer> deleted = new java.util.ArrayList<>();

        CreatedResourceRegistry.record("booking", 1151, deleted::add);
        List<String> attempted = CreatedResourceRegistry.cleanUp();

        assertThat(deleted).containsExactly(1151);
        assertThat(attempted).containsExactly("booking 1151");
    }

    /**
     * The reason the ledger is thread-local.
     *
     * <p>A static ledger lets one thread's teardown delete fixtures another thread is still
     * using. That surfaces as a 404 in a random test each run and reproduces for nobody.
     */
    @Test
    public void eachThreadCleansUpOnlyItsOwn() throws Exception {
        List<String> deleted = Collections.synchronizedList(new java.util.ArrayList<>());

        int threads = 4;
        CountDownLatch recorded = new CountDownLatch(threads);
        CountDownLatch cleanUp = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            int id = i;
            new Thread(() -> {
                try {
                    CreatedResourceRegistry.record("thread-" + id, () -> deleted.add("thread-" + id));
                    recorded.countDown();

                    // Every thread holds one entry at this point. A static ledger would
                    // show four here and the first thread to clean up would take all of them.
                    cleanUp.await(5, TimeUnit.SECONDS);

                    List<String> mine = CreatedResourceRegistry.cleanUp();
                    if (mine.size() != 1 || !mine.get(0).equals("thread-" + id)) {
                        deleted.add("LEAKED on thread-" + id + ": " + mine);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }, "registry-thread-" + i).start();
        }

        assertThat(recorded.await(5, TimeUnit.SECONDS)).isTrue();
        cleanUp.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();

        assertThat(deleted).hasSize(threads);
        assertThat(deleted).noneMatch(entry -> entry.startsWith("LEAKED"));
    }
}
