package io.github.bhushanupwanshi.api.core.data;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Remembers what a test created, so teardown can remove it.
 *
 * <p><strong>Why this exists.</strong> Without it the suite leaves its bookings behind,
 * and every subsequent run asserts against a dataset shaped by all previous runs. The
 * first thing to break is a count: "GET /booking returns N" passes on a clean service and
 * drifts a little further out every run, which looks like an application defect and is
 * not one.
 *
 * <p><strong>Why per thread.</strong> The suite runs with {@code parallel="methods"}. A
 * static ledger would let one thread's teardown delete fixtures another thread is still
 * using, producing a 404 in a test that did nothing wrong. That failure appears in a
 * random test each run and reproduces for nobody.
 *
 * <p><strong>Why reverse order.</strong> Resources are removed newest first, so a
 * dependent created after its parent is gone before the parent is. It costs nothing here,
 * where bookings are independent, and it is the behaviour that stops being optional the
 * moment anything is nested.
 *
 * <p>Cleanup is deliberately forgiving: a resource already gone is not a failure, and a
 * failed delete is logged rather than thrown. A teardown that throws masks the real test
 * result with a cleanup error, which is the wrong thing to report.
 */
public final class CreatedResourceRegistry {

    private static final Logger log = LoggerFactory.getLogger(CreatedResourceRegistry.class);

    /** One resource awaiting removal. */
    private record Entry(String description, Runnable delete) {
    }

    private static final ThreadLocal<Deque<Entry>> ENTRIES = ThreadLocal.withInitial(ArrayDeque::new);

    private CreatedResourceRegistry() {
    }

    /**
     * Records something to remove at teardown.
     *
     * @param description what it is, for log lines, e.g. {@code "booking 1151"}
     * @param delete      removes it; must tolerate the resource already being gone
     */
    public static void record(String description, Runnable delete) {
        ENTRIES.get().push(new Entry(description, delete));
        log.debug("registered {} for cleanup on thread {}",
                description, Thread.currentThread().getName());
    }

    /**
     * Removes everything this thread created, newest first.
     *
     * <p>Clears the ledger whatever happens, so a failed delete cannot cause the same
     * resource to be attempted again on the next test and fail again.
     *
     * @return descriptions of the resources it attempted to remove
     */
    public static List<String> cleanUp() {
        Deque<Entry> entries = ENTRIES.get();
        List<String> attempted = new java.util.ArrayList<>(entries.size());

        try {
            while (!entries.isEmpty()) {
                Entry entry = entries.pop();
                attempted.add(entry.description());
                try {
                    entry.delete().run();
                    log.debug("cleaned up {}", entry.description());
                } catch (RuntimeException e) {
                    // Logged, not thrown: a cleanup failure must not replace the test's
                    // own result with a teardown error.
                    log.warn("could not clean up {}: {}", entry.description(), e.getMessage());
                }
            }
        } finally {
            ENTRIES.remove();
        }

        return List.copyOf(attempted);
    }

    /** How many resources this thread is holding for cleanup. */
    public static int pending() {
        return ENTRIES.get().size();
    }

    /**
     * Discards the ledger without deleting anything.
     *
     * <p>For tests of the registry itself, and for a suite run against a disposable
     * environment where cleanup is wasted effort.
     */
    public static void discard() {
        ENTRIES.remove();
    }

    /** Convenience for the common case of one id and a deleting consumer. */
    public static void record(String type, int id, Consumer<Integer> delete) {
        record(type + " " + id, () -> delete.accept(id));
    }
}
