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

package songscribe;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.jspecify.annotations.Nullable;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

/**
 * Prints a compact one-line failure report (test path, exception message, and
 * first project stack frame) as each test fails, followed by a final summary.
 * Registered via ServiceLoader so the ConsoleLauncher picks it up automatically.
 */
public class CompactTestReporter implements TestExecutionListener {

    @Nullable
    private TestPlan testPlan;

    private final AtomicInteger passed = new AtomicInteger(0);
    private final AtomicInteger failed = new AtomicInteger(0);
    private final AtomicInteger skipped = new AtomicInteger(0);

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        this.testPlan = testPlan;
    }

    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
        if (testIdentifier.isTest()) {
            skipped.incrementAndGet();
        }
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult result) {
        if (!testIdentifier.isTest()) {
            return;
        }

        switch (result.getStatus()) {
            case SUCCESSFUL -> passed.incrementAndGet();
            case FAILED, ABORTED -> {
                failed.incrementAndGet();
                System.err.println("\nFAILED: " + buildDisplayPath(testIdentifier));
                result.getThrowable().ifPresent(this::printThrowable);
            }
        }
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        var sb = new StringBuilder("\nResults: ");
        sb.append(passed.get()).append(" passed");

        if (failed.get() > 0) {
            sb.append(", ").append(failed.get()).append(" FAILED");
        }

        if (skipped.get() > 0) {
            sb.append(", ").append(skipped.get()).append(" skipped");
        }

        System.err.println(sb);
    }

    private String buildDisplayPath(TestIdentifier identifier) {
        var plan = testPlan;

        if (plan == null) {
            return identifier.getDisplayName();
        }

        var parts = new ArrayList<String>();
        var current = identifier;

        while (current != null) {
            var name = current.getDisplayName();

            if (!name.startsWith("[engine:")) {
                parts.addFirst(name);
            }

            current = plan.getParent(current).orElse(null);
        }

        return String.join(" > ", parts);
    }

    private void printThrowable(Throwable t) {
        var message = t.getMessage();

        if (message != null) {
            for (var line : message.split("\n")) {
                System.err.println("  " + line.strip());
            }
        } else {
            System.err.println("  " + t.getClass().getName());
        }

        // Print the first project frame so you can jump straight to the failure site
        for (var frame : t.getStackTrace()) {
            if (frame.getClassName().startsWith("songscribe.")) {
                System.err.println("  at " + frame);
                return;
            }
        }

        // Fallback when no project frame is present (e.g. pure framework assertion)
        if (t.getStackTrace().length > 0) {
            System.err.println("  at " + t.getStackTrace()[0]);
        }
    }
}
