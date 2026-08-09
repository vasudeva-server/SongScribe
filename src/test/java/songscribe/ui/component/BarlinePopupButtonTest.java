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

import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.ui.action.Actions;

/**
 * Unit tests for {@link BarlinePopupButton}, the toolbar button that drops out a panel of the
 * barline actions.
 * <p>
 * {@code PopupToolbarButtonTest} covers the panel mechanics against mock actions. What is left
 * to check here is that this button is wired to the real barline actions and derives its state
 * from them — a button pointed at the wrong action list would still pass every test there.
 */
class BarlinePopupButtonTest extends MainFrameMockTest {

    private BarlinePopupButton button;

    @BeforeEach
    void setUp() {
        // Establish a known baseline. Nothing has to be restored afterwards: MainFrameMockTest
        // calls Actions.initialize() before every test, which replaces these actions outright.
        for (var action : Actions.BARLINE_ACTIONS) {
            action.setEnabled(false);
        }

        button = new BarlinePopupButton();
    }

    @Test
    void testPanelHostsEveryBarlineAction() {
        assertThat(Arrays.stream(Actions.BARLINE_ACTIONS).filter(button::hostsAction))
            .as("a barline missing from the panel is one the user cannot reach")
            .hasSize(Actions.BARLINE_ACTIONS.length);
    }

    @Test
    void testButtonIsDisabledWhenEveryBarlineActionIsDisabled() {
        assertThat(button.isEnabled()).isFalse();
    }

    /**
     * Barlines go dead in rest mode, during playback and while the lyric editor is open, and
     * come back afterwards. Nothing tells the button but the action's own property change.
     */
    @Test
    void testButtonRelightsWhenABarlineActionIsEnabledAfterConstruction() {
        Actions.BARLINE_ACTIONS[0].setEnabled(true);

        assertThat(button.isEnabled()).isTrue();
    }
}
