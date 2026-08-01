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
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.message.notification.MusicSelectionDidChangeNotification;

// Row 37: musicSelectionDidChange enablement for KeySignatureChangeAction
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
}
