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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.ui.OptionDialogs;

/**
 * Runtime error reporting. Logs the error and shows an alert dialog to the user.
 */
public final class RuntimeError {

    private static final Logger LOG = LoggerFactory.getLogger(RuntimeError.class);

    private static final String FATAL_ALERT_TITLE = "Fatal Error";
    private static final String FATAL_USER_MESSAGE =
        "Sorry, but a fatal error has occurred and the application must quit.";

    // Guards against showing the alert more than once if exit() is called re-entrantly.
    private static final AtomicBoolean ALERT_SHOWN = new AtomicBoolean(false);

    // Replaceable in tests so System.exit(-1) doesn't kill the test JVM.
    private static IntConsumer exitHandler = System::exit;

    /**
     * Logs the message, shows an error dialog to the user, and exits.
     * <p>
     * Always call as {@code throw RuntimeError.exit("reason")} so the compiler
     * and NullAway know the calling code is unreachable after this point.
     *
     * @param message Description of the violated invariant
     * @return never returns; declared as RuntimeException for use in {@code throw} expressions
     */
    public static RuntimeException exit(String message) {
        return exit(message, new RuntimeException((String) null));
    }

    /**
     * Logs the message and cause, shows an error dialog to the user, and exits.
     * <p>
     * Always call as {@code throw RuntimeError.exit("reason", cause)} so the compiler
     * and NullAway know the calling code is unreachable after this point.
     *
     * @param message Description of the violated invariant
     * @param cause   the exception that triggered the fatal error
     * @return never returns; declared as RuntimeException for use in {@code throw} expressions
     */
    public static RuntimeException exit(String message, Throwable cause) {
        LOG.error(message, cause);
        throw showDialogAndExit();
    }

    /**
     * Thrown by subsequent calls to {@link #exit} while the fatal-error alert is already showing.
     * The uncaught-exception handler ignores this so the EDT remains free to process the dialog.
     */
    public static final class ExitInProgressError extends Error {
        private ExitInProgressError() {
            super("exit already in progress", null, true, false);
        }
    }

    private static RuntimeException showDialogAndExit() {
        if (!ALERT_SHOWN.compareAndSet(false, true)) {
            throw new ExitInProgressError();
        }

        OptionDialogs.showErrorMessageWithString(null, FATAL_ALERT_TITLE, FATAL_USER_MESSAGE);

        exitHandler.accept(-1);
        throw new AssertionError("unreachable");
    }

    // Package-private — for use only by RuntimeErrorTestHelper in tests.
    static void setExitHandlerForTesting(IntConsumer handler) {
        exitHandler = handler;
    }

    // Package-private — for use only by RuntimeErrorTestHelper in tests.
    static void resetAlertShownForTesting() {
        ALERT_SHOWN.set(false);
    }

    private RuntimeError() {}
}
