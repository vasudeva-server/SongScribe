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
import songscribe.Strings;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.ui.action.Actions;

/**
 * Unit tests for {@link TupletPopupButton} covering:
 * <ul>
 *   <li>Row 32 — {@code musicSelectionDidChange}: button disabled when no tuplet action is enabled</li>
 *   <li>Row 33 — {@code configureButtonFromAction}: overrides tooltip to fixed tuplet string
 *       regardless of the action's own tooltip</li>
 * </ul>
 */
class TupletPopupButtonTest extends MainFrameMockTest {

    private TupletPopupButton button;
    private boolean[] savedEnabledStates;

    @BeforeEach
    void setUp() {
        // Save and disable all TOGGLE_TUPLET_ACTIONS to establish a known baseline;
        // restore in @AfterEach to avoid polluting other tests.
        var actions = Actions.TOGGLE_TUPLET_ACTIONS;
        savedEnabledStates = new boolean[actions.size()];

        for (int i = 0; i < actions.size(); i++) {
            savedEnabledStates[i] = actions.get(i).isEnabled();
        }

        for (var action : actions) {
            action.setEnabled(false);
        }

        button = new TupletPopupButton();
    }

    @AfterEach
    void restoreActionEnabledStates() {
        var actions = Actions.TOGGLE_TUPLET_ACTIONS;

        for (int i = 0; i < actions.size(); i++) {
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
    // Row 32: musicSelectionDidChange — button disabled when no tuplet action is enabled
    // -----------------------------------------------------------------------

    @Test
    void testMusicSelectionDidChangeDisabledWhenAllActionsDisabled() {
        // All actions disabled in @BeforeEach
        button.musicSelectionDidChange(makeNotification());
        assertThat(button.isEnabled()).isFalse();
    }

    @Test
    void testMusicSelectionDidChangeEnabledWhenOneActionIsEnabled() {
        Actions.TOGGLE_TUPLET_ACTIONS.get(0).setEnabled(true);
        button.musicSelectionDidChange(makeNotification());
        assertThat(button.isEnabled()).isTrue();
    }

    // -----------------------------------------------------------------------
    // Row 33: configureButtonFromAction — tooltip is always the fixed tuplet string
    // -----------------------------------------------------------------------

    @Test
    void testConfigureButtonFromActionUsesFixedTupletTooltip() {
        // configureButtonFromAction is called by setCurrentAction;
        // the action's own tooltip is irrelevant — the override must win.
        var action = Actions.TOGGLE_TUPLET_ACTIONS.get(0);
        button.setCurrentAction(action);
        assertThat(button.getToolTipText()).isEqualTo(Strings.get(Strings.TOOLTIP_TUPLET));
    }
}
