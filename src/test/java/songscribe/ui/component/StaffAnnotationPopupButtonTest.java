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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.ui.action.Actions;

/**
 * Unit tests for {@link StaffAnnotationPopupButton} covering:
 * <ul>
 *   <li>Row 31 — {@code musicSelectionDidChange}: button enabled iff at least one action
 *       in {@link Actions#STAFF_ANNOTATION_ACTIONS} is enabled</li>
 * </ul>
 */
class StaffAnnotationPopupButtonTest extends MainFrameMockTest {

    private StaffAnnotationPopupButton button;
    private boolean[] savedEnabledStates;

    @BeforeEach
    void setUp() {
        // Save the current enabled state of all STAFF_ANNOTATION_ACTIONS so we
        // can restore them after each test (they are shared global static state).
        var actions = Actions.STAFF_ANNOTATION_ACTIONS;
        savedEnabledStates = new boolean[actions.size()];

        for (var i = 0; i < actions.size(); i++) {
            savedEnabledStates[i] = actions.get(i).isEnabled();
        }

        // Disable all actions to establish a known baseline
        for (var action : actions) {
            action.setEnabled(false);
        }

        button = new StaffAnnotationPopupButton();
    }

    @AfterEach
    void restoreActionEnabledStates() {
        // Restore global action state so other tests are not affected
        var actions = Actions.STAFF_ANNOTATION_ACTIONS;

        for (var i = 0; i < actions.size(); i++) {
            actions.get(i).setEnabled(savedEnabledStates[i]);
        }
    }

    private MusicSelectionDidChangeNotification makeNotification() {
        var scoreView = mock(ScoreView.class);
        when(scoreView.getSelectionSize()).thenReturn(0);
        when(scoreView.getSelectionCoordinator()).thenReturn(mockEnv().coordinator());
        return new MusicSelectionDidChangeNotification(scoreView);
    }

    // -----------------------------------------------------------------------
    // Row 31: musicSelectionDidChange — enabled iff at least one action is enabled
    // -----------------------------------------------------------------------

    @Test
    void testMusicSelectionDidChangeDisabledWhenAllActionsDisabled() {
        // All actions are disabled (set up in @BeforeEach)
        button.musicSelectionDidChange(makeNotification());
        assertThat(button.isEnabled()).isFalse();
    }

    @Test
    void testMusicSelectionDidChangeEnabledWhenOneActionIsEnabled() {
        // Enable exactly one action — the button must become enabled
        Actions.STAFF_ANNOTATION_ACTIONS.get(0).setEnabled(true);
        button.musicSelectionDidChange(makeNotification());
        assertThat(button.isEnabled()).isTrue();
    }
}
