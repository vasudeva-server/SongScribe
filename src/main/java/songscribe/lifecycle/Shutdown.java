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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Process-global shutdown registry. Funnels every user-invoked quit path
 * through one ordered, vetoable sequence; owns the JVM shutdown hook so
 * thread-safe cleanup runs even on emergency exits.
 *
 * <p>The user-invoked paths — {@code QuitAction}, {@code MainFrame.windowClosing},
 * {@code CloseWindowAction}, and the macOS Desktop quit — all
 * call {@link #now()}. That runs the confirm phase on the EDT in registration order, where any
 * handler may veto; if none does, the EDT cleanup tasks run LIFO, then the JVM cleanup tasks run
 * LIFO, then {@code System.exit(0)}.
 *
 * <p>The emergency paths — {@code RuntimeError.exit()}, SIGTERM, and the last non-daemon thread
 * ending — skip straight to JVM exit. Either way the registry-owned shutdown hook runs the JVM
 * tasks LIFO on the hook thread, and the {@code CleanupTask} wrapper guarantees each runs at most
 * once across both paths.
 *
 * <p>See {@code docs/lifecycle.md} for the full diagram.
 */
public final class Shutdown {

    private static final Logger LOG = LoggerFactory.getLogger(Shutdown.class);

    private static final Shutdown INSTANCE = new Shutdown();

    private final CopyOnWriteArrayList<ConfirmEntry> confirmEntries = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<CleanupTask> edtTasks = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<CleanupTask> jvmTasks = new CopyOnWriteArrayList<>();

    private volatile boolean inProgress = false;
    private volatile boolean completed = false;

    /** Confirm tasks run first. Any task returning false aborts the shutdown. */
    @FunctionalInterface
    public interface ConfirmTask {
        /** @return true to continue shutdown, false to abort. */
        boolean confirm();
    }

    @FunctionalInterface
    private interface Tagged {
        String tag();
    }

    private Shutdown() {
        var jvmHook = new Thread(Shutdown::runJVMTasksFromHook, "ShutdownJVMHook");
        Runtime.getRuntime().addShutdownHook(jvmHook);
    }

    /**
     * Sole quit entry point. Runs the registry; on success, calls {@code System.exit(0)}.
     * Returns (without exiting) if a confirm task vetoed.
     *
     * <p>Must be called on the EDT.
     */
    public static void now() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Shutdown.now() must be called on the EDT");
        }

        if (shutdown()) {
            LOG.info("Application shutting down");
            System.exit(0);
        }
    }

    /**
     * Register a confirm task (e.g. dirty-document prompt).
     * Runs on the EDT; may show modal dialogs.
     *
     * @throws IllegalArgumentException if {@code tag} duplicates an existing confirm tag.
     */
    public static void registerConfirmTask(String tag, ConfirmTask task) {
        checkUniqueTag(INSTANCE.confirmEntries, tag, "Duplicate confirm tag: " + tag);
        LOG.info("Registered confirm task: {}", tag);
        INSTANCE.confirmEntries.add(new ConfirmEntry(tag, task));
    }

    /**
     * Register a cleanup task that requires the EDT (e.g. {@code mainFrame.setEnabled(false)}).
     * Runs only on the user-invoked quit path. Not invoked from the JVM hook.
     *
     * @throws IllegalArgumentException if {@code tag} duplicates an existing EDT-task tag.
     */
    public static void registerEDTTask(String tag, Runnable task) {
        registerCleanup(INSTANCE.edtTasks, tag, task, "EDT");
    }

    /**
     * Register a thread-safe cleanup task (e.g. close MIDI).
     * Runs on the user-invoked quit path AND on emergency JVM exit.
     * MUST be thread-safe and EDT-independent — the JVM hook fires on a JVM-managed thread.
     * The registry guarantees at-most-once execution across both paths.
     *
     * @throws IllegalArgumentException if {@code tag} duplicates an existing JVM-task tag.
     */
    public static void registerJVMTask(String tag, Runnable task) {
        registerCleanup(INSTANCE.jvmTasks, tag, task, "JVM");
    }

    private static void registerCleanup(List<CleanupTask> list, String tag, Runnable task, String phase) {
        checkUniqueTag(list, tag, "Duplicate " + phase + "-task tag: " + tag);
        LOG.info("Registered {} task: {}", phase, tag);
        list.add(new CleanupTask(tag, task));
    }

    private static void checkUniqueTag(List<? extends Tagged> items, String tag, String errorMsg) {
        if (items.stream().anyMatch(e -> e.tag().equals(tag))) {
            throw new IllegalArgumentException(errorMsg);
        }
    }

    /**
     * Run the shutdown sequence. Callers use {@link #now()}, which schedules this on the EDT.
     *
     * @return true if shutdown ran to completion, false if aborted or already completed.
     */
    private static boolean shutdown() {
        if (INSTANCE.completed || INSTANCE.inProgress) {
            return false;
        }
        INSTANCE.inProgress = true;
        try {
            // Confirm phase — registration order, exceptions propagate.
            for (var entry : INSTANCE.confirmEntries) {
                if (!entry.task.confirm()) {
                    LOG.info("Shutdown vetoed by confirm task: {}", entry.tag);
                    return false;
                }
            }
            runCleanupTasksLIFO(INSTANCE.edtTasks, "EDT");
            runCleanupTasksLIFO(INSTANCE.jvmTasks, "JVM");
            INSTANCE.completed = true;
            return true;
        } finally {
            INSTANCE.inProgress = false;
        }
    }

    private static void runCleanupTasksLIFO(List<CleanupTask> tasks, String phase) {
        var iter = tasks.listIterator(tasks.size());

        while (iter.hasPrevious()) {
            var t = iter.previous();

            try {
                if (t.run()) {
                    LOG.info("Ran {} cleanup: {}", phase, t.tag());
                }
            } catch (Exception e) {
                LOG.error("Exception in {} cleanup task '{}'", phase, t.tag(), e);
            }
        }
    }

    /** Entry point for the registry-owned JVM shutdown hook. */
    private static void runJVMTasksFromHook() {
        try {
            runCleanupTasksLIFO(INSTANCE.jvmTasks, "JVM (hook)");
        } catch (Throwable t) {
            // Boundary catch so the JVM doesn't print a raw stack trace on its way out.
            LOG.error("Exception escaping JVM shutdown hook", t);
        }
    }

    private record ConfirmEntry(String tag, ConfirmTask task) implements Tagged {}

    @SuppressWarnings("PackageVisibleInnerClass")
    static final class CleanupTask implements Tagged {
        private final String tag;
        private final Runnable body;
        private volatile boolean ran;

        CleanupTask(String tag, Runnable body) {
            this.tag = tag;
            this.body = body;
        }

        synchronized boolean run() {
            if (ran) {
                return false;
            }

            ran = true;
            body.run();
            return true;
        }

        @Override
        public String tag() { return tag; }
    }
}
