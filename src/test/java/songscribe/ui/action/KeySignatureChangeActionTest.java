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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.ui.component.ScoreView;

// Rows 37–38: musicSelectionDidChange enablement and null-guard for KeySignatureChangeAction
class KeySignatureChangeActionTest extends MainFrameMockTest {

    // Row 37: enablement is gated on whether a line is currently selected
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class MusicSelectionDidChange {

        @Test
        void testDisabledWhenNoLineSelected() {
            // getSelectedLine() == -1 means no line is selected → action must be disabled
            when(mockEnv().score().getSelectedLine()).thenReturn(-1);
            var action = KeySignatureChangeAction.createAction(mainFrame());

            action.musicSelectionDidChange(new MusicSelectionDidChangeNotification(mockEnv().score()));

            assertThat(action.isEnabled()).isFalse();
        }

        @Test
        void testEnabledWhenLineSelected() {
            // getSelectedLine() >= 0 means a line is selected → action may be enabled
            when(mockEnv().score().getSelectedLine()).thenReturn(0);
            var action = KeySignatureChangeAction.createAction(mainFrame());

            action.musicSelectionDidChange(new MusicSelectionDidChangeNotification(mockEnv().score()));

            assertThat(action.isEnabled()).isTrue();
        }
    }

    // Row 38: null-guard prevents NPE when the notification's score view is absent.
    // BUG (now fixed): the original code called message.getScoreView().getSelectedLine()
    // without a null check. Unlike TempoChangeAction (which guards on ctrl != null),
    // KeySignatureChangeAction had no guard. A null score view would NPE at
    // message.getScoreView().getSelectedLine() — see KeySignatureChangeAction.musicSelectionDidChange.
    // The fix: check scoreView != null before calling getSelectedLine().
    @Test
    void testNullScoreViewDoesNotThrow() {
        // Simulate a notification whose score view is null (e.g., constructed via
        // a mock, as happens in tests that do not pass a real ScoreView).
        // Without the guard, this NPEs. With the guard, updateEnabledState() still
        // runs and sets the enabled state via flag checks; setEnabled(line != -1) is skipped.
        var message = mock(MusicSelectionDidChangeNotification.class);
        when(message.getScoreView()).thenReturn((ScoreView) null);
        var action = KeySignatureChangeAction.createAction(mainFrame());

        // Must not throw — the guard in the fixed implementation handles null safely
        action.musicSelectionDidChange(message);

        // updateEnabledState() ran; since the line check was skipped the action
        // was left in whatever state the flag checks set (enabled by default stubs)
        assertThat(action.isEnabled()).isTrue();
    }
}
