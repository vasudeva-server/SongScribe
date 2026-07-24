/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.util;

import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Unit tests for {@link Debounce}.
 * <p>
 * Every case runs against <b>both</b> factories. The two implementations exist for cost reasons
 * alone, so any observable difference between them is a bug in one of them — testing them through
 * one parameterized body is what makes that guarantee, rather than two suites that could drift.
 * <p>
 * {@code Debounce} is documented as EDT-confined, so every construct/trigger/cancel here goes
 * through {@link EventQueue#invokeAndWait}. The waiting is done on the test thread, leaving the
 * EDT free to dispatch the action under test.
 */
class DebounceTest extends UnitTest {

    /** The debounce delay for cases that only need the action to eventually run. */
    private static final int DELAY_MS = 50;

    /**
     * Poll interval for the {@code polling} factory. Well under every delay used here, so
     * quantization never dominates the measurements.
     */
    private static final int POLL_INTERVAL_MS = 10;

    /**
     * A deliberately long delay for {@link #testRetriggerExtendsTheDeadline}, so the gap between
     * the two triggers can be large in absolute terms and the case cannot flake on a loaded
     * machine mistaking scheduling jitter for an unextended deadline.
     */
    private static final int EXTENSION_DELAY_MS = 300;

    /** How long to wait between the two triggers in {@link #testRetriggerExtendsTheDeadline}. */
    private static final int EXTENSION_RETRIGGER_GAP_MS = 150;

    /**
     * How long to keep watching after the action should <i>not</i> have run. Several times the
     * delay, so a debounce that fires when it should not has ample opportunity to do so.
     */
    private static final int QUIET_WATCH_MS = 250;

    /** Upper bound on waiting for an action that is expected to run; failure, not pacing. */
    private static final long AWAIT_TIMEOUT_MS = 5000;

    private static final long NANOS_PER_MS = 1_000_000L;

    /** Every debounce built by a test, cancelled in teardown so no timer outlives its case. */
    private final List<Debounce> created = new ArrayList<>();

    /** Builds a debounce of one particular flavour with the delay the test chooses. */
    @FunctionalInterface
    private interface DebounceFactory {
        Debounce create(int delayMs, Runnable action);
    }

    private static Stream<Arguments> factories() {
        return Stream.of(
            arguments("rescheduling", (DebounceFactory) Debounce::rescheduling),
            arguments(
                "polling",
                (DebounceFactory) (delayMs, action) -> Debounce.polling(delayMs, POLL_INTERVAL_MS, action)
            )
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        // A Swing timer outlives the test method that armed it, so anything still pending would
        // fire against the next test.
        EventQueue.invokeAndWait(() -> created.forEach(Debounce::cancel));
        created.clear();
    }

    // -----------------------------------------------------------------------
    // The action actually runs
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @MethodSource("factories")
    void testTriggerRunsTheActionExactlyOnce(String name, DebounceFactory factory) throws Exception {
        var runCount = new AtomicInteger();
        var ran = new CountDownLatch(1);
        var debounce = create(factory, DELAY_MS, () -> {
            runCount.incrementAndGet();
            ran.countDown();
        });

        trigger(debounce);

        assertThat(awaitLatch(ran)).as("the action must run after the delay elapses").isTrue();

        // A debounce that left its timer repeating would keep firing after the first run.
        Thread.sleep(QUIET_WATCH_MS);

        assertThat(runCount.get()).as("one trigger must produce exactly one run").isEqualTo(1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("factories")
    void testActionRunsOnTheEventDispatchThread(String name, DebounceFactory factory) throws Exception {
        var onEventDispatchThread = new AtomicBoolean();
        var ran = new CountDownLatch(1);
        var debounce = create(factory, DELAY_MS, () -> {
            onEventDispatchThread.set(EventQueue.isDispatchThread());
            ran.countDown();
        });

        trigger(debounce);

        assertThat(awaitLatch(ran)).as("the action must run").isTrue();
        assertThat(onEventDispatchThread.get()).as("the action must run on the EDT").isTrue();
    }

    // -----------------------------------------------------------------------
    // The deadline moves with every trigger
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @MethodSource("factories")
    void testRetriggerExtendsTheDeadline(String name, DebounceFactory factory) throws Exception {
        var elapsedSinceLastTriggerNanos = new AtomicLong();
        var ran = new CountDownLatch(1);
        var lastTriggerNanos = new AtomicLong();
        var debounce = create(factory, EXTENSION_DELAY_MS, () -> {
            elapsedSinceLastTriggerNanos.set(System.nanoTime() - lastTriggerNanos.get());
            ran.countDown();
        });

        trigger(debounce);
        Thread.sleep(EXTENSION_RETRIGGER_GAP_MS);

        assertThat(ran.getCount())
            .as("the action must not run while the gap is shorter than the delay")
            .isEqualTo(1);

        // Stamped on the EDT immediately before the trigger, so the measured elapsed time can
        // only understate the real interval — never overstate it into a false pass.
        EventQueue.invokeAndWait(() -> {
            lastTriggerNanos.set(System.nanoTime());
            debounce.trigger();
        });

        assertThat(awaitLatch(ran)).as("the action must run once the pointer settles").isTrue();

        // The whole point of a debounce: the delay is measured from the *last* trigger. Had the
        // second trigger not moved the deadline, this would be the leftover remainder of the
        // first window instead.
        assertThat(elapsedSinceLastTriggerNanos.get() / NANOS_PER_MS)
            .as("the action must run no sooner than the full delay after the final trigger")
            .isGreaterThanOrEqualTo(EXTENSION_DELAY_MS);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("factories")
    void testActionMayRetriggerFromWithinItself(String name, DebounceFactory factory) throws Exception {
        var runCount = new AtomicInteger();
        var ranTwice = new CountDownLatch(2);
        var debounceRef = new AtomicReference<Debounce>();
        var debounce = create(factory, DELAY_MS, () -> {
            ranTwice.countDown();

            // Re-arm from inside the action exactly once. An implementation that tore its timer
            // down *after* running the action would stop the window this just opened.
            if (runCount.incrementAndGet() == 1) {
                var self = debounceRef.get();

                if (self != null) {
                    self.trigger();
                }
            }
        });

        debounceRef.set(debounce);
        trigger(debounce);

        assertThat(awaitLatch(ranTwice)).as("an action that re-triggers must run a second time").isTrue();
    }

    // -----------------------------------------------------------------------
    // Arming, cancelling, and isArmed()
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @MethodSource("factories")
    void testIsNotArmedBeforeAnyTrigger(String name, DebounceFactory factory) throws Exception {
        var debounce = create(factory, DELAY_MS, () -> {
        });

        assertThat(isArmed(debounce)).as("a freshly built debounce must be idle").isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("factories")
    void testIsArmedBetweenTriggerAndAction(String name, DebounceFactory factory) throws Exception {
        var ran = new CountDownLatch(1);
        var debounce = create(factory, EXTENSION_DELAY_MS, ran::countDown);

        trigger(debounce);

        assertThat(isArmed(debounce)).as("a triggered debounce must report itself armed").isTrue();

        assertThat(awaitLatch(ran)).as("the action must run").isTrue();
        assertThat(isArmed(debounce)).as("a debounce must disarm once its action has run").isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("factories")
    void testCancelPreventsThePendingAction(String name, DebounceFactory factory) throws Exception {
        var runCount = new AtomicInteger();
        var debounce = create(factory, DELAY_MS, runCount::incrementAndGet);

        trigger(debounce);
        cancel(debounce);

        assertThat(isArmed(debounce)).as("a cancelled debounce must report itself idle").isFalse();

        Thread.sleep(QUIET_WATCH_MS);

        assertThat(runCount.get()).as("a cancelled action must never run").isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("factories")
    void testCancelWhileIdleIsANoOp(String name, DebounceFactory factory) throws Exception {
        var ran = new CountDownLatch(1);
        var debounce = create(factory, DELAY_MS, ran::countDown);

        cancel(debounce);

        assertThat(isArmed(debounce)).as("cancelling an idle debounce must leave it idle").isFalse();

        // A cancel must not wedge the debounce permanently.
        trigger(debounce);

        assertThat(awaitLatch(ran)).as("a debounce must still arm after an idle cancel").isTrue();
    }

    // -----------------------------------------------------------------------
    // EDT plumbing
    // -----------------------------------------------------------------------

    private Debounce create(DebounceFactory factory, int delayMs, Runnable action) throws Exception {
        var holder = new AtomicReference<Debounce>();
        EventQueue.invokeAndWait(() -> holder.set(factory.create(delayMs, action)));

        var debounce = holder.get();

        if (debounce == null) {
            throw new AssertionError("the factory returned no debounce");
        }

        created.add(debounce);
        return debounce;
    }

    private static void trigger(Debounce debounce) throws Exception {
        EventQueue.invokeAndWait(debounce::trigger);
    }

    private static void cancel(Debounce debounce) throws Exception {
        EventQueue.invokeAndWait(debounce::cancel);
    }

    private static boolean isArmed(Debounce debounce) throws Exception {
        var armed = new AtomicBoolean();
        EventQueue.invokeAndWait(() -> armed.set(debounce.isArmed()));
        return armed.get();
    }

    private static boolean awaitLatch(CountDownLatch latch) throws InterruptedException {
        return latch.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }
}
