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

package songscribe.ui.action;

import module java.desktop;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.MainFrameMockTest;
import songscribe.dom.ElementType;
import songscribe.message.MessageCenter;
import songscribe.message.notification.RestModeDidChangeNotification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class RestModeActionTest extends MainFrameMockTest {

    private RestModeAction action;

    @BeforeEach
    void setUp() {
        action = RestModeAction.createAction(mainFrame());
    }

    // Row 24: actionPerformed — keyboard source (JRootPane) toggles selected state AND posts notification;
    //          non-keyboard source (button) does NOT toggle BUT still posts notification

    @Test
    void testActionPerformedFromKeyboardTogglesSelectedState() {
        try (var messageCenterMock = mockStatic(MessageCenter.class)) {
            // JRootPane source simulates the keyboard shortcut code path
            var rootPane = mock(JRootPane.class);
            action.setSelected(false);

            action.actionPerformed(
                new ActionEvent(rootPane, ActionEvent.ACTION_PERFORMED, "rest-mode"));

            assertThat(action.isSelected()).isTrue();
            messageCenterMock.verify(
                () -> MessageCenter.post(any(RestModeDidChangeNotification.class)));
        }
    }

    @Test
    void testActionPerformedFromButtonDoesNotToggleSelectedState() {
        try (var messageCenterMock = mockStatic(MessageCenter.class)) {
            var button = new JButton();
            action.setSelected(false);

            action.actionPerformed(
                new ActionEvent(button, ActionEvent.ACTION_PERFORMED, "rest-mode"));

            // Button-triggered action: Swing toggles the button itself; action must not double-toggle
            assertThat(action.isSelected()).isFalse();
            messageCenterMock.verify(
                () -> MessageCenter.post(any(RestModeDidChangeNotification.class)));
        }
    }

    // Row 25: enablement — REQUIRES_EMPTY_SELECTION and ENABLE_WHEN_DURATION_SELECTED untested

    @Test
    void testDisabledWhenSelectionIsNonEmpty() {
        // REQUIRES_EMPTY_SELECTION: selection size > 0 → disabled
        when(mockEnv().score().getSelectionSize()).thenReturn(1);

        var enabled = action.enableFromSelectionSize(mockEnv().score());
        assertThat(enabled).isFalse();
    }

    @Test
    void testEnabledWhenSelectionIsEmpty() {
        // REQUIRES_EMPTY_SELECTION: selection size == 0 → enabled
        when(mockEnv().score().getSelectionSize()).thenReturn(0);

        var enabled = action.enableFromSelectionSize(mockEnv().score());
        assertThat(enabled).isTrue();
    }

    @Test
    void testEnabledForRegularDurationWhenNoActiveSelection() {
        // ENABLE_WHEN_DURATION_SELECTED: regular durations (e.g. quarter) allow rest mode
        Actions.DURATION_ACTION_GROUP.setSelected(Actions.QUARTER_NOTE_ACTION, true);

        var enabled = action.enableFromDurationSelection(false);
        assertThat(enabled).isTrue();
    }

    @Test
    void testDisabledForGraceNoteWhenNoActiveSelection() {
        // ENABLE_WHEN_DURATION_SELECTED: grace eighth note → rest mode disabled
        Actions.DURATION_ACTION_GROUP.setSelected(Actions.GRACE_EIGHTH_NOTE_ACTION, true);

        var enabled = action.enableFromDurationSelection(false);
        assertThat(enabled).isFalse();
    }

    @Test
    void testEnabledForAnyDurationWhenActiveSelection() {
        // Active selection bypasses the duration check entirely
        Actions.DURATION_ACTION_GROUP.setSelected(Actions.GRACE_EIGHTH_NOTE_ACTION, true);

        var enabled = action.enableFromDurationSelection(true);
        assertThat(enabled).isTrue();
    }

    // Row 26: appliesTo — returns true for duration elements, false for non-duration elements

    @Test
    void testAppliesToReturnsTrueForDurationElement() {
        var quarterNote = ElementType.CROTCHET.newInstance();
        assertThat(action.appliesTo(quarterNote)).isTrue();
    }

    @Test
    void testAppliesToReturnsFalseForBarlineElement() {
        var barline = ElementType.SINGLE_BARLINE.newInstance();
        assertThat(action.appliesTo(barline)).isFalse();
    }

    @Test
    void testAppliesToReturnsTrueForRestElement() {
        var rest = ElementType.CROTCHET_REST.newInstance();
        assertThat(action.appliesTo(rest)).isTrue();
    }
}
