/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package songscribe.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ShutdownTest {

    @BeforeEach
    void setUp() {
        Shutdown.reset();
    }

    @AfterEach
    void tearDown() {
        Shutdown.reset();
    }

    // 1. Empty registry: shutdown() returns true.
    @Test
    void emptyRegistryReturnsTrue() {
        assertThat(Shutdown.shutdown()).isTrue();
    }

    // 2. All confirms pass; EDT then JVM cleanups run; each phase LIFO; phases ordered confirm → EDT → JVM.
    @Test
    void confirmThenEdtThenJvmAllRunInOrderWithLifoPerPhase() {
        var log = new ArrayList<String>();

        Shutdown.registerConfirmTask("c1", () -> { log.add("c1"); return true; });
        Shutdown.registerConfirmTask("c2", () -> { log.add("c2"); return true; });
        Shutdown.registerEDTTask("e1", () -> log.add("e1"));
        Shutdown.registerEDTTask("e2", () -> log.add("e2"));
        Shutdown.registerJVMTask("j1", () -> log.add("j1"));
        Shutdown.registerJVMTask("j2", () -> log.add("j2"));

        assertThat(Shutdown.shutdown()).isTrue();
        // Confirms registration order, then EDT LIFO, then JVM LIFO.
        assertThat(log).containsExactly("c1", "c2", "e2", "e1", "j2", "j1");
    }

    // 3. First confirm fails: no further confirms, no cleanup, returns false, registry re-armed.
    @Test
    void firstConfirmVetoAbortsAndReArms() {
        var log = new ArrayList<String>();

        Shutdown.registerConfirmTask("c1", () -> { log.add("c1"); return false; });
        Shutdown.registerConfirmTask("c2", () -> { log.add("c2"); return true; });
        Shutdown.registerEDTTask("e1", () -> log.add("e1"));
        Shutdown.registerJVMTask("j1", () -> log.add("j1"));

        assertThat(Shutdown.shutdown()).isFalse();
        assertThat(log).containsExactly("c1");

        // Re-armed: retry succeeds when veto is gone (we can't change c1, but inProgress must be cleared).
        // Verify by calling again — same veto, same result, no exception.
        assertThat(Shutdown.shutdown()).isFalse();
        assertThat(log).containsExactly("c1", "c1");
    }

    // 4. Middle confirm fails: prior confirms ran, no cleanup.
    @Test
    void middleConfirmVetoRunsPriorConfirmsButNoCleanup() {
        var log = new ArrayList<String>();

        Shutdown.registerConfirmTask("c1", () -> { log.add("c1"); return true; });
        Shutdown.registerConfirmTask("c2", () -> { log.add("c2"); return false; });
        Shutdown.registerConfirmTask("c3", () -> { log.add("c3"); return true; });
        Shutdown.registerEDTTask("e1", () -> log.add("e1"));
        Shutdown.registerJVMTask("j1", () -> log.add("j1"));

        assertThat(Shutdown.shutdown()).isFalse();
        assertThat(log).containsExactly("c1", "c2");
    }

    // 5. Cleanup throws RuntimeException: subsequent cleanup tasks still run, exception logged.
    @Test
    void cleanupRuntimeExceptionDoesNotStopSubsequent() {
        var log = new ArrayList<String>();

        Shutdown.registerEDTTask("e1", () -> log.add("e1"));
        Shutdown.registerEDTTask("e2", () -> { throw new RuntimeException("boom"); });
        Shutdown.registerEDTTask("e3", () -> log.add("e3"));
        Shutdown.registerJVMTask("j1", () -> log.add("j1"));
        Shutdown.registerJVMTask("j2", () -> { throw new RuntimeException("boom2"); });
        Shutdown.registerJVMTask("j3", () -> log.add("j3"));

        assertThat(Shutdown.shutdown()).isTrue();
        // EDT LIFO: e3 ran, e2 threw, e1 ran. JVM LIFO: j3 ran, j2 threw, j1 ran.
        assertThat(log).containsExactly("e3", "e1", "j3", "j1");
    }

    // 6. Cleanup throws Error: propagates; no further cleanup.
    @Test
    void cleanupErrorPropagatesAndStopsCleanup() {
        var log = new ArrayList<String>();

        Shutdown.registerEDTTask("e1", () -> log.add("e1"));
        Shutdown.registerEDTTask("e2", () -> { throw new OutOfMemoryError("simulated"); });
        Shutdown.registerEDTTask("e3", () -> log.add("e3"));

        assertThatThrownBy(Shutdown::shutdown)
            .isInstanceOf(OutOfMemoryError.class)
            .hasMessageContaining("simulated");

        // Only e3 ran (LIFO); e2 threw before e1 could run.
        assertThat(log).containsExactly("e3");
    }

    // 7. Confirm throws: propagates; no cleanup; registry NOT marked complete (re-armable).
    @Test
    void confirmExceptionPropagatesAndRegistryRemainsArmed() {
        var log = new ArrayList<String>();
        var firstCall = new AtomicInteger(0);

        Shutdown.registerConfirmTask("c1", () -> {
            if (firstCall.getAndIncrement() == 0) {
                throw new RuntimeException("confirm boom");
            }
            return true;
        });
        Shutdown.registerEDTTask("e1", () -> log.add("e1"));

        assertThatThrownBy(Shutdown::shutdown)
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("confirm boom");
        assertThat(log).isEmpty();

        // Re-armable: second attempt completes (no completed flag set, no inProgress flag stuck).
        assertThat(Shutdown.shutdown()).isTrue();
        assertThat(log).containsExactly("e1");
    }

    // 8. Re-entrant shutdown() from inside a confirm task: returns false.
    @Test
    void reentrantShutdownFromConfirmReturnsFalse() {
        var innerResult = new ArrayList<Boolean>();
        var outerLog = new ArrayList<String>();

        Shutdown.registerConfirmTask("c1", () -> {
            innerResult.add(Shutdown.shutdown());
            return true;
        });
        Shutdown.registerEDTTask("e1", () -> outerLog.add("e1"));

        assertThat(Shutdown.shutdown()).isTrue();
        assertThat(innerResult).containsExactly(false);
        assertThat(outerLog).containsExactly("e1");
    }

    // 9. Re-entrant shutdown() from inside a cleanup task: returns false.
    @Test
    void reentrantShutdownFromCleanupReturnsFalse() {
        var innerResult = new ArrayList<Boolean>();

        Shutdown.registerEDTTask("e1", () -> innerResult.add(Shutdown.shutdown()));

        assertThat(Shutdown.shutdown()).isTrue();
        assertThat(innerResult).containsExactly(false);
    }

    // 10. Successful shutdown() then second shutdown(): returns false.
    @Test
    void secondShutdownAfterSuccessReturnsFalse() {
        var counter = new AtomicInteger(0);

        Shutdown.registerEDTTask("e1", counter::incrementAndGet);

        assertThat(Shutdown.shutdown()).isTrue();
        assertThat(Shutdown.shutdown()).isFalse();
        assertThat(counter).hasValue(1);
    }

    // 11. Duplicate confirm tag: IllegalArgumentException.
    @Test
    void duplicateConfirmTagThrows() {
        Shutdown.registerConfirmTask("dup", () -> true);
        assertThatThrownBy(() -> Shutdown.registerConfirmTask("dup", () -> true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("dup");
    }

    // 12. Duplicate EDT-task tag: IllegalArgumentException.
    @Test
    void duplicateEdtTagThrows() {
        Shutdown.registerEDTTask("dup", () -> {});
        assertThatThrownBy(() -> Shutdown.registerEDTTask("dup", () -> {}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("dup");
    }

    // 13. Duplicate JVM-task tag: IllegalArgumentException.
    @Test
    void duplicateJvmTagThrows() {
        Shutdown.registerJVMTask("dup", () -> {});
        assertThatThrownBy(() -> Shutdown.registerJVMTask("dup", () -> {}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("dup");
    }

    // 14. Same string tag across all three phases: succeeds (per-phase namespaces).
    @Test
    void sameTagAcrossPhasesIsAllowed() {
        Shutdown.registerConfirmTask("same", () -> true);
        Shutdown.registerEDTTask("same", () -> {});
        Shutdown.registerJVMTask("same", () -> {});

        assertThat(Shutdown.shutdown()).isTrue();
    }

    // 15. CleanupTask.run() invoked twice on the same instance: body runs exactly once.
    @Test
    void cleanupTaskRunsAtMostOnceOnRepeatedInvocation() {
        var counter = new AtomicInteger(0);
        var task = new Shutdown.CleanupTask("t", counter::incrementAndGet);

        task.run();
        task.run();
        task.run();

        assertThat(counter).hasValue(1);
    }

    // 16. Cross-thread CleanupTask.run() racing on two threads: body runs exactly once.
    @Test
    void cleanupTaskRunsAtMostOnceUnderRace() throws InterruptedException {
        final var threadCount = 8;
        var counter = new AtomicInteger(0);
        var task = new Shutdown.CleanupTask("race", counter::incrementAndGet);

        var startGate = new CountDownLatch(1);
        var doneGate = new CountDownLatch(threadCount);
        var threads = new ArrayList<Thread>();

        for (var i = 0; i < threadCount; i++) {
            var t = new Thread(() -> {
                try {
                    startGate.await();
                    task.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneGate.countDown();
                }
            });
            threads.add(t);
            t.start();
        }

        startGate.countDown();
        doneGate.await();
        for (var t : threads) {
            t.join();
        }

        assertThat(counter).hasValue(1);
    }

    // 17. JVM-hook simulation: registered JVM task runs from the hook. Then shutdown() + hook = at-most-once total.
    @Test
    void jvmHookEntryPointRunsTasksOnceAcrossPaths() {
        var counter = new AtomicInteger(0);
        Shutdown.registerJVMTask("j1", counter::incrementAndGet);
        Shutdown.runJVMTasksFromHook();
        assertThat(counter).hasValue(1);

        // Separate scenario: shutdown() then hook again — at-most-once per task.
        Shutdown.reset();
        var fc = new AtomicInteger(0);
        Shutdown.registerJVMTask("j1", fc::incrementAndGet);
        assertThat(Shutdown.shutdown()).isTrue();
        assertThat(fc).hasValue(1);
        Shutdown.runJVMTasksFromHook();
        assertThat(fc).hasValue(1);
    }

    // 18. EDT-tasks are NOT invoked from the JVM hook entry point.
    @Test
    void edtTasksNotInvokedFromJvmHook() {
        var log = new ArrayList<String>();

        Shutdown.registerEDTTask("e1", () -> log.add("e1"));
        Shutdown.registerJVMTask("j1", () -> log.add("j1"));

        Shutdown.runJVMTasksFromHook();

        assertThat(log).containsExactly("j1");
    }

    // 19. now() called from a non-EDT thread throws IllegalStateException.
    @Test
    void nowThrowsWhenCalledOffEdt() throws InterruptedException {
        var caught = new AtomicReference<Throwable>();
        var done = new CountDownLatch(1);

        var thread = new Thread(() -> {
            try {
                Shutdown.now();
            } catch (Throwable t) {
                caught.set(t);
            } finally {
                done.countDown();
            }
        });
        thread.start();
        done.await();

        assertThat(caught.get())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("EDT");
    }

    // 20. runJVMTasksFromHook() swallows any Error that escapes a JVM task (boundary catch).
    @Test
    void runJvmTasksFromHookSwallowsEscapingError() {
        var afterThrowRan = new AtomicBoolean(false);

        Shutdown.registerJVMTask("throwing", () -> { throw new Error("simulated hook error"); });
        Shutdown.registerJVMTask("afterThrow", () -> afterThrowRan.set(true));

        // Must not throw — outer catch in runJVMTasksFromHook swallows the Error.
        assertThatCode(Shutdown::runJVMTasksFromHook).doesNotThrowAnyException();

        // The error-throwing task runs last (LIFO), so afterThrow ran first.
        assertThat(afterThrowRan).isTrue();
    }
}
