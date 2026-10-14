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

package songscribe.error;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.ui.OptionDialogs;

/** Fatal runtime error reporting: logs the error and alerts the user. */
public final class RuntimeError {

    private static final Logger LOG = LoggerFactory.getLogger(RuntimeError.class);

    private static final String FATAL_ALERT_TITLE = "Fatal Error";
    private static final String FATAL_USER_MESSAGE =
        "Sorry, but a fatal error has occurred and the application must quit.";
    private static final String MISSING_RESOURCE_USER_MESSAGE =
        "A required resource is missing. Please reinstall the application.";

    private static final int EXIT_STATUS = -1;

    // Guards against showing the alert twice when exit() is called re-entrantly.
    private static final AtomicBoolean ALERT_SHOWN = new AtomicBoolean(false);

    // Replaceable in tests so System.exit(-1) doesn't kill the test JVM.
    private static IntConsumer exitHandler = System::exit;

    /** Always call as {@code throw RuntimeError.exit(...)}, so the compiler and NullAway see the code after it as unreachable. */
    public static RuntimeException exit(String message) {
        return logAndExit(message, FATAL_USER_MESSAGE, null);
    }

    public static RuntimeException exit(String message, Throwable cause) {
        return logAndExit(message, FATAL_USER_MESSAGE, cause);
    }

    public static RuntimeException exit(String logMessage, String userMessage) {
        return logAndExit(logMessage, userMessage, null);
    }

    /** The dynamic detail goes to the log; the user sees a fixed message. Always call as {@code throw}. */
    public static RuntimeException missingResource(String logMessage) {
        return logAndExit(logMessage, MISSING_RESOURCE_USER_MESSAGE, null);
    }

    public static RuntimeException missingResource(String logMessage, Throwable cause) {
        return logAndExit(logMessage, MISSING_RESOURCE_USER_MESSAGE, cause);
    }

    /** The uncaught-exception handler ignores this, so the EDT stays free to process the alert. */
    public static final class ExitInProgressError extends Error {
        private ExitInProgressError() {
            super("exit already in progress", null, true, false);
        }
    }

    /** @param cause {@code null} when the caller has no exception to attribute the exit to */
    private static RuntimeException logAndExit(
        String logMessage,
        String userMessage,
        @Nullable Throwable cause
    ) {
        if (cause == null) {
            LOG.error(logMessage);
        } else {
            LOG.error(logMessage, cause);
        }

        if (!ALERT_SHOWN.compareAndSet(false, true)) {
            throw new ExitInProgressError();
        }

        OptionDialogs.showErrorMessageWithString(null, FATAL_ALERT_TITLE, userMessage);

        exitHandler.accept(EXIT_STATUS);
        throw new AssertionError("unreachable");
    }

    static void setExitHandlerForTesting(IntConsumer handler) {
        exitHandler = handler;
    }

    static void resetAlertShownForTesting() {
        ALERT_SHOWN.set(false);
    }

    private RuntimeError() {}
}
