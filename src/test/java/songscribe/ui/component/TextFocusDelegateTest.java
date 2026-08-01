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

package songscribe.ui.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.awt.Component;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;

import javax.swing.JTextArea;
import javax.swing.JTextField;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.notification.TextEditingDidChangeNotification;

/**
 * Unit tests for {@link TextFocusDelegate} covering:
 * <ul>
 *   <li>Row 67 — {@code focusGained}: sets {@code ignoreTabKey=true}, posts {@code TextEditingDidChangeNotification(true)}</li>
 *   <li>Row 68 — {@code focusLost}: posts {@code TextEditingDidChangeNotification(false)}</li>
 *   <li>Row 69 — {@code processKeyEvent} for JTextField host: returns false, clears ignoreTabKey</li>
 *   <li>Row 70 — {@code processKeyEvent} for JTextArea host, Tab on first key after focus (ignoreTabKey=true): consumed but focus NOT transferred</li>
 *   <li>Row 71 — {@code processKeyEvent} for JTextArea host, Tab (not first): consumed, transferFocus() called</li>
 *   <li>Row 72 — {@code processKeyEvent} for JTextArea host, Shift+Tab: consumed, transferFocusBackward() called</li>
 *   <li>Row 73 — {@code processKeyEvent}: non-Tab key → returns false</li>
 * </ul>
 */
class TextFocusDelegateTest extends UnitTest {

    @Nullable
    private MockedStatic<MessageCenter> messageCenterMock;

    @AfterEach
    void tearDown() {
        if (messageCenterMock != null) {
            messageCenterMock.close();
            messageCenterMock = null;
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Creates a FocusEvent with the given source and event id. */
    private static FocusEvent focusEvent(Object source, int id) {
        return new FocusEvent((Component) source, id);
    }

    /**
     * Creates a KeyEvent for a Tab press on the given source component.
     * The event is a KEY_PRESSED so {@code getKeyCode()} returns VK_TAB.
     */
    private static KeyEvent tabKeyEvent(Component source, boolean shiftDown) {
        var modifiers = shiftDown ? KeyEvent.SHIFT_DOWN_MASK : 0;
        return new KeyEvent(source, KeyEvent.KEY_PRESSED, 0L, modifiers, KeyEvent.VK_TAB, KeyEvent.CHAR_UNDEFINED);
    }

    /** Creates a non-Tab KeyEvent (letter 'a') on the given source. */
    private static KeyEvent nonTabKeyEvent(Component source) {
        return new KeyEvent(source, KeyEvent.KEY_PRESSED, 0L, 0, KeyEvent.VK_A, 'a');
    }

    /**
     * Captures the single {@link TextEditingDidChangeNotification} from the mocked
     * {@link MessageCenter#post} calls and asserts exactly one was posted.
     */
    private TextEditingDidChangeNotification captureTextEditingNotification() {
        var mock = messageCenterMock;

        if (mock == null) {
            throw new IllegalStateException("messageCenterMock must be initialized");
        }

        var captor = ArgumentCaptor.forClass(Message.class);
        mock.verify(() -> MessageCenter.post(captor.capture()), atLeastOnce());

        var notifications = captor.getAllValues().stream()
            .filter(m -> m instanceof TextEditingDidChangeNotification)
            .map(m -> (TextEditingDidChangeNotification) m)
            .toList();

        assertThat(notifications)
            .as("expected exactly one TextEditingDidChangeNotification")
            .hasSize(1);

        return notifications.getFirst();
    }

    // -----------------------------------------------------------------------
    // Row 67: focusGained — sets ignoreTabKey=true, posts notification(true)
    // -----------------------------------------------------------------------

    @Test
    void testFocusGainedPostsTextEditingDidChangeWithTrue() {
        var field = new JTextField();
        var delegate = new TextFocusDelegate(field);

        messageCenterMock = mockStatic(MessageCenter.class);

        delegate.focusGained(focusEvent(field, FocusEvent.FOCUS_GAINED));

        var notification = captureTextEditingNotification();
        assertThat(notification.isEditing()).isTrue();
    }

    // -----------------------------------------------------------------------
    // Row 68: focusLost — posts notification(false)
    // -----------------------------------------------------------------------

    @Test
    void testFocusLostPostsTextEditingDidChangeWithFalse() {
        var field = new JTextField();
        var delegate = new TextFocusDelegate(field);

        messageCenterMock = mockStatic(MessageCenter.class);

        delegate.focusLost(focusEvent(field, FocusEvent.FOCUS_LOST));

        var notification = captureTextEditingNotification();
        assertThat(notification.isEditing()).isFalse();
    }

    // -----------------------------------------------------------------------
    // Row 69: processKeyEvent for JTextField host — returns false
    // -----------------------------------------------------------------------

    @Test
    void testProcessKeyEventReturnsFalseForJTextField() {
        var field = new JTextField();
        var delegate = new TextFocusDelegate(field);

        var tabEvent = tabKeyEvent(field, false);
        var handled = delegate.processKeyEvent(tabEvent);

        // JTextField: always returns false (no tab interception)
        assertThat(handled).isFalse();
    }

    // -----------------------------------------------------------------------
    // Rows 70–71: JTextArea, Tab key variants
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class JTextAreaHostTab {

        @Test
        void testFirstTabAfterFocusGainedConsumedButFocusNotTransferred() {
            // Row 70: ignoreTabKey=true after focusGained — first Tab consumed but focus NOT moved
            var area = spy(new JTextArea());
            var delegate = new TextFocusDelegate(area);

            messageCenterMock = mockStatic(MessageCenter.class);

            // focusGained sets ignoreTabKey = true
            delegate.focusGained(focusEvent(area, FocusEvent.FOCUS_GAINED));

            var tabEvent = tabKeyEvent(area, false);
            var handled = delegate.processKeyEvent(tabEvent);

            assertThat(handled).as("Tab key should be marked handled (consumed)").isTrue();
            assertThat(tabEvent.isConsumed()).as("Tab event should be consumed").isTrue();
            verify(area, never()).transferFocus();
        }

        @Test
        void testTabNotFirstKeyTransfersFocus() {
            var area = spy(new JTextArea());
            var delegate = new TextFocusDelegate(area);

            // No prior focusGained call, so ignoreTabKey starts false
            var tabEvent = tabKeyEvent(area, false);
            var handled = delegate.processKeyEvent(tabEvent);

            assertThat(handled).as("Tab key should be marked handled").isTrue();
            assertThat(tabEvent.isConsumed()).as("Tab event should be consumed").isTrue();
            verify(area).transferFocus();
        }

        @Test
        void testSecondTabAfterFocusGainedTransfersFocus() {
            var area = spy(new JTextArea());
            var delegate = new TextFocusDelegate(area);

            messageCenterMock = mockStatic(MessageCenter.class);

            // focusGained sets ignoreTabKey = true
            delegate.focusGained(focusEvent(area, FocusEvent.FOCUS_GAINED));

            // First Tab: suppressed (ignoreTabKey=true), clears ignoreTabKey
            delegate.processKeyEvent(tabKeyEvent(area, false));
            verify(area, never()).transferFocus();

            // Second Tab: ignoreTabKey is now false, so focus IS transferred
            var secondTab = tabKeyEvent(area, false);
            var handled = delegate.processKeyEvent(secondTab);

            assertThat(handled).isTrue();
            verify(area).transferFocus();
        }
    }

    // -----------------------------------------------------------------------
    // Row 72: JTextArea, Shift+Tab — consumed, transferFocusBackward() called
    // -----------------------------------------------------------------------

    @Test
    void testShiftTabTransfersFocusBackward() {
        var area = spy(new JTextArea());
        var delegate = new TextFocusDelegate(area);

        // No prior focusGained, so ignoreTabKey starts false
        var shiftTabEvent = tabKeyEvent(area, true);
        var handled = delegate.processKeyEvent(shiftTabEvent);

        assertThat(handled).as("Shift+Tab key should be marked handled").isTrue();
        assertThat(shiftTabEvent.isConsumed()).as("Shift+Tab event should be consumed").isTrue();
        verify(area).transferFocusBackward();
        verify(area, never()).transferFocus();
    }

    // -----------------------------------------------------------------------
    // Row 73: non-Tab key → returns false
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class NonTabKey {

        @Test
        void testNonTabKeyReturnsFalseForJTextArea() {
            var area = new JTextArea();
            var delegate = new TextFocusDelegate(area);

            var keyEvent = nonTabKeyEvent(area);
            var handled = delegate.processKeyEvent(keyEvent);

            assertThat(handled).isFalse();
        }

        @Test
        void testNonTabKeyReturnsFalseForJTextField() {
            var field = new JTextField();
            var delegate = new TextFocusDelegate(field);

            var keyEvent = nonTabKeyEvent(field);
            var handled = delegate.processKeyEvent(keyEvent);

            assertThat(handled).isFalse();
        }
    }
}
