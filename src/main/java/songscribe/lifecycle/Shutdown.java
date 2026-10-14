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
 * Process-global shutdown registry. Every user-invoked quit path calls {@link #now()}: confirm
 * tasks run in registration order and any may veto, then EDT tasks and JVM tasks run LIFO. The
 * emergency paths skip straight to JVM exit, where the shutdown hook runs the JVM tasks; each
 * runs at most once across both paths.
 */
public final class Shutdown {

    private static final Logger LOG = LoggerFactory.getLogger(Shutdown.class);

    private static final Shutdown INSTANCE = new Shutdown();

    private final CopyOnWriteArrayList<ConfirmEntry> confirmEntries = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<CleanupTask> edtTasks = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<CleanupTask> jvmTasks = new CopyOnWriteArrayList<>();

    private volatile boolean inProgress = false;
    private volatile boolean completed = false;

    @FunctionalInterface
    public interface ConfirmTask {
        /** @return true to continue shutdown, false to abort */
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

    public static void now() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Shutdown.now() must be called on the EDT");
        }

        if (shutdown()) {
            LOG.info("Application shutting down");
            System.exit(0);
        }
    }

    /** Runs on the EDT; may show modal dialogs. */
    public static void registerConfirmTask(String tag, ConfirmTask task) {
        checkUniqueTag(INSTANCE.confirmEntries, tag, "Duplicate confirm tag: " + tag);
        LOG.info("Registered confirm task: {}", tag);
        INSTANCE.confirmEntries.add(new ConfirmEntry(tag, task));
    }

    /** Runs only on the user-invoked quit path. Not invoked from the JVM hook. */
    public static void registerEDTTask(String tag, Runnable task) {
        registerCleanup(INSTANCE.edtTasks, tag, task, "EDT");
    }

    /** Runs on emergency JVM exit too, so the task MUST be thread-safe and EDT-independent. */
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

    private static boolean shutdown() {
        if (INSTANCE.completed || INSTANCE.inProgress) {
            return false;
        }
        INSTANCE.inProgress = true;
        try {
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
