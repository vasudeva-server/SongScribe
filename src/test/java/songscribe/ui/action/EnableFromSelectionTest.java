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
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import module java.desktop;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.music.StaffElement;
import songscribe.ui.Mode;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.Score;
import songscribe.ui.selection.SelectionCoordinator;

class EnableFromSelectionTest extends UnitTest {

    // -- enableFromBarSelection: defer during active selection --

    @Test
    void testEnableFromBarSelectionDefersWithActiveSelection() {
        try (var mainFrameMock = mockStatic(MainFrame.class)) {
            var env = setupMockEnv(mainFrameMock);
            when(env.coordinator.hasActiveSelection()).thenReturn(true);

            var action = createNonReflectableWithBarFlag();

            // Even though the action has DISABLE_WHEN_BAR_SELECTED, the method
            // defers (returns true) when a selection is active.
            assertThat(action.enableFromBarSelection(
                env.coordinator.hasActiveSelection())).isTrue();
        }
    }

    @Test
    void testEnableFromBarSelectionRunsNormallyWithoutSelection() {
        try (var mainFrameMock = mockStatic(MainFrame.class)) {
            var env = setupMockEnv(mainFrameMock);
            when(env.coordinator.hasActiveSelection()).thenReturn(false);

            // Without the flag, the method returns true regardless of selection state.
            var action = new UIAction("Test", null, 0, "test", "Test") {
                @Override
                public void actionPerformed(ActionEvent e) {
                }
            };

            assertThat(action.enableFromBarSelection(
                env.coordinator.hasActiveSelection())).isTrue();
        }
    }

    // -- enableFromDurationSelection: defer during active selection --

    @Test
    void testEnableFromDurationSelectionDefersWithActiveSelection() {
        try (var mainFrameMock = mockStatic(MainFrame.class)) {
            var env = setupMockEnv(mainFrameMock);
            when(env.coordinator.hasActiveSelection()).thenReturn(true);

            var action = new UIAction("Test", null, 0, "test", "Test") {
                @Override
                public void actionPerformed(ActionEvent e) {
                }
            };
            action.setFlags(UIAction.Flag.ENABLE_WHEN_DURATION_SELECTED);

            // Even with the flag, the method defers during active selection.
            assertThat(action.enableFromDurationSelection(
                env.coordinator.hasActiveSelection())).isTrue();
        }
    }

    @Test
    void testEnableFromDurationSelectionRunsNormallyWithoutSelection() {
        try (var mainFrameMock = mockStatic(MainFrame.class)) {
            var env = setupMockEnv(mainFrameMock);
            when(env.coordinator.hasActiveSelection()).thenReturn(false);

            var action = new UIAction("Test", null, 0, "test", "Test") {
                @Override
                public void actionPerformed(ActionEvent e) {
                }
            };
            // No ENABLE_WHEN_DURATION_SELECTED flag -> returns true (no check needed)
            assertThat(action.enableFromDurationSelection(
                env.coordinator.hasActiveSelection())).isTrue();
        }
    }

    // -- enableFromSelection: no active selection --

    @Test
    void testNoSelectionReturnsTrue() {
        try (var mainFrameMock = mockStatic(MainFrame.class)) {
            var env = setupMockEnv(mainFrameMock);
            when(env.coordinator.hasActiveSelection()).thenReturn(false);

            var action = new FermataAction();

            assertThat(action.enableFromSelection(
                env.coordinator.hasActiveSelection(), env.score)).isTrue();
        }
    }

    // -- enableFromSelection: non-reflectable with DISABLE_WHEN_BAR_SELECTED --

    @Test
    void testNonReflectableWithFlagAndDurationsReturnsTrue() {
        try (var mainFrameMock = mockStatic(MainFrame.class)) {
            var env = setupMockEnv(mainFrameMock);
            when(env.coordinator.hasActiveSelection()).thenReturn(true);
            when(env.coordinator.selectionHasDurations()).thenReturn(true);

            var action = createNonReflectableWithBarFlag();

            assertThat(action.enableFromSelection(
                env.coordinator.hasActiveSelection(), env.score)).isTrue();
        }
    }

    @Test
    void testNonReflectableWithFlagAndNoDurationsReturnsFalse() {
        try (var mainFrameMock = mockStatic(MainFrame.class)) {
            var env = setupMockEnv(mainFrameMock);
            when(env.coordinator.hasActiveSelection()).thenReturn(true);
            when(env.coordinator.selectionHasDurations()).thenReturn(false);

            var action = createNonReflectableWithBarFlag();

            assertThat(action.enableFromSelection(
                env.coordinator.hasActiveSelection(), env.score)).isFalse();
        }
    }

    @Test
    void testNonReflectableWithoutFlagReturnsTrue() {
        try (var mainFrameMock = mockStatic(MainFrame.class)) {
            var env = setupMockEnv(mainFrameMock);
            when(env.coordinator.hasActiveSelection()).thenReturn(true);

            var action = new UIAction("Test", null, 0, "test", "Test") {
                @Override
                public void actionPerformed(ActionEvent e) {
                }
            };

            assertThat(action.enableFromSelection(
                env.coordinator.hasActiveSelection(), env.score)).isTrue();
        }
    }

    // -- enableFromSelection: reflectable actions --

    @Test
    void testReflectableApplicableReturnsTrue() {
        try (var mainFrameMock = mockStatic(MainFrame.class)) {
            var env = setupMockEnv(mainFrameMock);
            when(env.coordinator.hasActiveSelection()).thenReturn(true);

            var action = new FermataAction();
            when(env.coordinator.isApplicableToSelection(action)).thenReturn(true);

            assertThat(action.enableFromSelection(
                env.coordinator.hasActiveSelection(), env.score)).isTrue();
        }
    }

    @Test
    void testReflectableInapplicableReturnsFalse() {
        try (var mainFrameMock = mockStatic(MainFrame.class)) {
            var env = setupMockEnv(mainFrameMock);
            when(env.coordinator.hasActiveSelection()).thenReturn(true);

            var action = new AccidentalAction(
                StaffElement.Accidental.SHARP, "Sharp", null, 0, "sharp", "Sharp"
            );
            when(env.coordinator.isApplicableToSelection(action)).thenReturn(false);

            assertThat(action.enableFromSelection(
                env.coordinator.hasActiveSelection(), env.score)).isFalse();
        }
    }

    // -- helpers --

    private record MockEnv(
        MainFrame frame,
        Score score,
        SelectionCoordinator coordinator
    ) {}

    private MockEnv setupMockEnv(
        org.mockito.MockedStatic<MainFrame> mainFrameMock
    ) {
        var mockFrame = mock(MainFrame.class);
        var mockScore = mock(Score.class);
        var mockCoordinator = mock(SelectionCoordinator.class);

        mainFrameMock.when(MainFrame::getInstance).thenReturn(mockFrame);
        when(mockFrame.getScore()).thenReturn(mockScore);
        when(mockScore.getSelectionCoordinator()).thenReturn(mockCoordinator);
        when(mockScore.getMode()).thenReturn(Mode.EDIT);
        when(mockScore.getSelectionSize()).thenReturn(0);

        return new MockEnv(mockFrame, mockScore, mockCoordinator);
    }

    private UIAction createNonReflectableWithBarFlag() {
        var action = new UIAction("Test", null, 0, "test", "Test") {
            @Override
            public void actionPerformed(ActionEvent e) {
            }
        };
        action.setFlags(UIAction.Flag.DISABLE_WHEN_BAR_SELECTED);
        return action;
    }
}
