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

// Row 34: musicSelectionDidChange enablement for TempoChangeAction
class TempoChangeActionTest extends MainFrameMockTest {

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class MusicSelectionDidChange {

        @Test
        void testDisabledWhenNoSelection() {
            // REQUIRES_SINGLE_SELECTION: 0 notes → updateEnabledState() returns false,
            // so canChangeTempo() is never consulted — the action stays disabled.
            when(mockEnv().score().getSelectionSize()).thenReturn(0);
            var action = TempoChangeAction.createAction(mainFrame());

            action.musicSelectionDidChange(new MusicSelectionDidChangeNotification(mockEnv().score()));

            assertThat(action.isEnabled()).isFalse();
        }

        @Test
        void testDisabledWhenBarElementSelected() {
            // DISABLE_WHEN_BAR_SELECTED: a bar element has no durations, so
            // enableFromSelection returns false even though selectionSize=1 and
            // canChangeTempo() would return true. The flag is the sole reason for
            // the disabled state — a mutant removing it would cause this to pass.
            when(mockEnv().score().getSelectionSize()).thenReturn(1);
            when(mockEnv().coordinator().hasActiveSelection()).thenReturn(true);
            when(mockEnv().coordinator().selectionHasDurations()).thenReturn(false);
            var action = TempoChangeAction.createAction(mainFrame());

            action.musicSelectionDidChange(new MusicSelectionDidChangeNotification(mockEnv().score()));

            assertThat(action.isEnabled()).isFalse();
        }

        @Test
        void testEnabledWhenSingleNote() {
            // REQUIRES_SINGLE_SELECTION + canChangeTempo() = true → enabled.
            // selectionHasDurations()=true confirms the selection is a duration element
            // (not a bar), satisfying the DISABLE_WHEN_BAR_SELECTED check.
            when(mockEnv().score().getSelectionSize()).thenReturn(1);
            when(mockEnv().coordinator().hasActiveSelection()).thenReturn(true);
            when(mockEnv().coordinator().selectionHasDurations()).thenReturn(true);
            when(mockEnv().ctrl().canChangeTempo()).thenReturn(true);
            var action = TempoChangeAction.createAction(mainFrame());

            action.musicSelectionDidChange(new MusicSelectionDidChangeNotification(mockEnv().score()));

            assertThat(action.isEnabled()).isTrue();
        }
    }
}
