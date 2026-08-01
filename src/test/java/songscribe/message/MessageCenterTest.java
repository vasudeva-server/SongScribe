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
package songscribe.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.engio.mbassy.bus.error.PublicationError;
import net.engio.mbassy.listener.Handler;

import songscribe.UnitTest;
import songscribe.message.command.SaveCommand;

class MessageCenterTest extends UnitTest {

    // -------------------------------------------------------------------------
    // Message.toString — row 1
    // -------------------------------------------------------------------------

    @Nested
    class MessageToString {

        @Test
        void testToStringReturnsSimpleClassName() {
            // SaveCommand is a top-level subclass; its simple name must be returned
            assertThat(new SaveCommand().toString()).isEqualTo("SaveCommand");
        }

        @Test
        void testToStringReturnsSimpleClassNameForNestedSubclass() {
            // An anonymous subclass's simple name is "" — confirm toString() returns
            // that rather than the superclass name "Message"
            assertThat(new Message() {}.toString()).isEqualTo("");
        }
    }

    // -------------------------------------------------------------------------
    // MessageCenter.post() dispatches synchronously — row 3
    // -------------------------------------------------------------------------

    /** Listener that counts how many times its handler was invoked. */
    private static final class RecordingListener {
        int count = 0;

        @Handler
        public void onSave(SaveCommand message) {
            count++;
        }
    }

    @Test
    void testPostDispatchesBeforeReturning() {
        var listener = new RecordingListener();
        MessageCenter.subscribe(listener);

        assertThat(listener.count).isZero();
        MessageCenter.post(new SaveCommand());
        // handler must have been called synchronously before post() returned
        assertThat(listener.count).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // MessageCenter.post() dispatches in priority order — row 4
    // -------------------------------------------------------------------------

    private static final int FIRST_PRIORITY = 50;
    private static final int SECOND_PRIORITY = 10;

    /** Records invocation order via a shared list. */
        private record OrderedListener(List<? super Integer> order, int tag) {

        @Handler(priority = FIRST_PRIORITY)
            public void onHighPriority(SaveCommand message) {
                if (tag == FIRST_PRIORITY) {
                    order.add(tag);
                }
            }

            @Handler(priority = SECOND_PRIORITY)
            public void onLowPriority(SaveCommand message) {
                if (tag == SECOND_PRIORITY) {
                    order.add(tag);
                }
            }
        }

    @Test
    void testHighPriorityHandlerRunsBeforeLowPriority() {
        var order = new ArrayList<Integer>();
        var highListener = new OrderedListener(order, FIRST_PRIORITY);
        var lowListener = new OrderedListener(order, SECOND_PRIORITY);

        // Subscribe low-priority first to ensure ordering is by annotation, not registration
        MessageCenter.subscribe(lowListener);
        MessageCenter.subscribe(highListener);

        MessageCenter.post(new SaveCommand());

        assertThat(order).containsExactly(FIRST_PRIORITY, SECOND_PRIORITY);
    }

    // -------------------------------------------------------------------------
    // MessageCenter.subscribe() — late subscriber does not receive prior posts — row 5
    // -------------------------------------------------------------------------

    private static final class LateListener {
        int count = 0;

        @Handler
        public void onSave(SaveCommand message) {
            count++;
        }
    }

    @Test
    void testSubscribeAfterPostDoesNotReceivePriorMessages() {
        // Post before subscribing
        MessageCenter.post(new SaveCommand());

        var listener = new LateListener();
        MessageCenter.subscribe(listener);

        // listener subscribed after post — its counter must still be zero
        assertThat(listener.count).isZero();
    }

    // -------------------------------------------------------------------------
    // MessageCenter.handlePublicationError — throws when handler throws — rows 6 + 7
    //
    // NOTE: MBassador's AbstractPubSubSupport.handlePublicationError wraps every
    // call to the registered error handler in its own try-catch(Throwable) and
    // prints the exception to stderr rather than rethrowing it.  As a result,
    // MessageCenter.post() returns normally even when a handler throws and
    // RuntimeError.exit() is invoked — the bus eats it.  This is the production
    // risk documented in the audit.  Rows 6 and 7 are therefore tested by
    // invoking handlePublicationError directly via reflection.
    // -------------------------------------------------------------------------

    @Nested
    class HandlePublicationError {

        private Method handlePublicationErrorMethod;

        @BeforeEach
        void resolveMethod() throws Exception {
            handlePublicationErrorMethod =
                MessageCenter.class.getDeclaredMethod("handlePublicationError", PublicationError.class);
            handlePublicationErrorMethod.setAccessible(true);
        }

        private void invokeHandlePublicationError(PublicationError error) throws Throwable {
            try {
                handlePublicationErrorMethod.invoke(null, error);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }

        // These tests drive the error path deliberately; discard what the probe recorded
        // so UnitTest's teardown does not flag it as an unexpected handler failure.
        @AfterEach
        void discardDeliberateErrors() {
            MessageCenterTestHelper.clearPublicationErrors();
        }

        // Row 6: when cause is non-null, RuntimeError.exit(detail, cause) is called and
        // the resulting AssertionError (from RuntimeErrorTestHelper) propagates to the caller.
        @Test
        void testWithCausePropagatesError() throws Throwable {
            var cause = new RuntimeException("deliberate failure");
            var error = new PublicationError()
                .setCause(cause)
                .setListener(new Object())
                .setPublishedMessage(new SaveCommand());

            // Use catchThrowable (not assertThatThrownBy) because RuntimeErrorTestHelper
            // throws AssertionError, which assertThatThrownBy intentionally re-throws
            // instead of capturing.
            var thrown = catchThrowable(() -> invokeHandlePublicationError(error));
            assertThat(thrown)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("System.exit");
        }

        // Row 7: when listener/handler/message are all null, the null-safe path is exercised
        // and RuntimeError.exit() is still reached without an NPE.
        @Test
        void testNullFieldsDoNotThrowNpe() throws Throwable {
            var cause = new RuntimeException("cause");
            var error = new PublicationError()
                .setCause(cause)
                .setListener(null)
                .setHandler(null)
                .setPublishedMessage(null);

            var thrown = catchThrowable(() -> invokeHandlePublicationError(error));
            assertThat(thrown)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("System.exit");
        }

        // Row 7 (no-cause branch): when cause is null and errorMsg is present,
        // RuntimeError.exit(message) is called.
        @Test
        void testNoCauseBranchUsesErrorMessage() throws Throwable {
            var error = new PublicationError()
                .setMessage("bus error without cause")
                .setListener(null)
                .setHandler(null)
                .setPublishedMessage(null);
            // cause is null → code path: RuntimeError.exit(error.getMessage())
            var thrown = catchThrowable(() -> invokeHandlePublicationError(error));
            assertThat(thrown)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("System.exit");
        }
    }
}
