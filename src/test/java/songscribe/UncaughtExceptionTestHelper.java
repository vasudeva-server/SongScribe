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
package songscribe;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Test-only capture of exceptions thrown on background threads.
 *
 * <p>A raw {@code Thread} (e.g. {@code PlayThread}, {@code MainFrame}'s startup-gate
 * thread, {@code SystemThemeDetector}'s poll thread) that throws goes to the JVM's
 * default uncaught-exception handler, which just prints a stack trace and moves on —
 * invisible to the test that triggered it. This installs a handler that records the
 * throwable instead, so {@code UnitTest}'s teardown can fail the test loudly.
 *
 * <p>Reinstalled before every test because some tests (e.g. {@code SongScribeTest}'s
 * {@code main()} tests) call {@code Thread.setDefaultUncaughtExceptionHandler}
 * themselves, replacing whatever handler is currently installed.
 */
public final class UncaughtExceptionTestHelper {

    private static final List<String> uncaughtExceptions = Collections.synchronizedList(new ArrayList<>());

    /**
     * Installs the recording handler as the JVM's default uncaught-exception handler.
     * Call from a {@code @BeforeEach} in each test base class.
     */
    public static void install() {
        Thread.setDefaultUncaughtExceptionHandler(
            (thread, throwable) -> uncaughtExceptions.add(
                "Uncaught exception on thread \"" + thread.getName() + "\":\n" + stackTraceOf(throwable)));
    }

    private static String stackTraceOf(Throwable throwable) {
        var writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    /**
     * Discards recorded uncaught exceptions. Call after intentionally driving the
     * handler (e.g. simulating an uncaught exception) so teardown does not flag it.
     */
    public static void clearUncaughtExceptions() {
        uncaughtExceptions.clear();
    }

    /**
     * Fails if any exception reached the default uncaught-exception handler since the
     * last clear. Recorded exceptions are cleared before throwing so a failure does not
     * cascade into later tests.
     */
    public static void assertNoUncaughtExceptions() {
        if (uncaughtExceptions.isEmpty()) {
            return;
        }

        var details = String.join("\n\n", uncaughtExceptions);
        uncaughtExceptions.clear();
        throw new AssertionError(
            "An exception reached the default uncaught-exception handler during this test — "
                + "a background thread threw and nothing else reported it:\n\n" + details);
    }

    private UncaughtExceptionTestHelper() {}
}
