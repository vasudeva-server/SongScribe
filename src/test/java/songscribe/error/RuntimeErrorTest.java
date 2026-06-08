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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import songscribe.UnitTest;

class RuntimeErrorTest extends UnitTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void setUpLogCapture() {
        logger = (Logger) LoggerFactory.getLogger(RuntimeError.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDownLogCapture() {
        logger.detachAppender(appender);
        appender.stop();
    }

    // Installs an exit handler that does NOT reset ALERT_SHOWN and does NOT throw,
    // so the caller gets AssertionError("unreachable") from showDialogAndExit and
    // ALERT_SHOWN remains true after the call returns.
    private AtomicInteger installNonResettingExitHandler() {
        var capturedStatus = new AtomicInteger(Integer.MIN_VALUE);
        RuntimeError.setExitHandlerForTesting(capturedStatus::set);
        return capturedStatus;
    }

    // -------------------------------------------------------------------------
    // exit(String) — row 9
    // -------------------------------------------------------------------------

    @Test
    void testExitLogsMessageAtErrorLevel() {
        installNonResettingExitHandler();

        // exit() always throws; we catch and ignore.
        catchThrowable(() -> RuntimeError.exit("fatal invariant violated"));

        assertThat(appender.list)
            .anyMatch(e ->
                e.getLevel() == Level.ERROR
                    && e.getFormattedMessage().contains("fatal invariant violated"));
    }

    // -------------------------------------------------------------------------
    // exit(String, Throwable) — row 10
    // -------------------------------------------------------------------------

    @Test
    void testExitWithCauseLogsMessageAndCauseAtErrorLevel() {
        installNonResettingExitHandler();
        var cause = new RuntimeException("root cause");

        catchThrowable(() -> RuntimeError.exit("fatal with cause", cause));

        assertThat(appender.list)
            .anyMatch(e ->
                e.getLevel() == Level.ERROR
                    && e.getFormattedMessage().contains("fatal with cause")
                    && e.getThrowableProxy() != null
                    && e.getThrowableProxy().getMessage().equals("root cause"));
    }

    // -------------------------------------------------------------------------
    // showDialogAndExit — CAS succeeds first call (row 11)
    // -------------------------------------------------------------------------

    @Test
    void testShowDialogAndExitCasSucceedsExitHandlerInvoked() {
        var capturedStatus = installNonResettingExitHandler();

        // exit() always throws (AssertionError("unreachable") after handler returns).
        catchThrowable(() -> RuntimeError.exit("first call"));

        // Exit handler must have been called with -1.
        assertThat(capturedStatus.get()).isEqualTo(-1);
    }

    // -------------------------------------------------------------------------
    // showDialogAndExit — re-entrant call throws ExitInProgressError (row 12)
    // -------------------------------------------------------------------------

    @Test
    void testShowDialogAndExitReentrantCallThrowsExitInProgressError() {
        installNonResettingExitHandler();

        // First call: CAS succeeds, exit handler runs (handler just records status, doesn't reset).
        // AssertionError("unreachable") propagates — ALERT_SHOWN stays true.
        catchThrowable(() -> RuntimeError.exit("first call"));

        // Second call: CAS fails (ALERT_SHOWN already true) → ExitInProgressError.
        assertThatThrownBy(() -> RuntimeError.exit("second call"))
            .isInstanceOf(RuntimeError.ExitInProgressError.class);
    }

    // -------------------------------------------------------------------------
    // ExitInProgressError — extends Error, no stack trace, no suppressed (row 13)
    // -------------------------------------------------------------------------

    @Test
    void testExitInProgressErrorExtendsErrorWithNoStackTrace() {
        installNonResettingExitHandler();

        // Trigger it: ALERT_SHOWN already false (reset by @BeforeEach), prime with first call.
        catchThrowable(() -> RuntimeError.exit("prime"));

        // Second call throws ExitInProgressError (private constructor, can only obtain via throw).
        var thrown = catchThrowable(() -> RuntimeError.exit("trigger"));

        assertThat(thrown).isInstanceOf(Error.class);
        assertThat(thrown.getStackTrace()).isEmpty();
        assertThat(thrown.getSuppressed()).isEmpty();
    }
}
