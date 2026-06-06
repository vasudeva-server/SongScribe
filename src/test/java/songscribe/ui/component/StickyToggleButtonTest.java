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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.awt.event.ActionEvent;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.ui.action.SelectableUIAction;

/**
 * Unit tests for {@link StickyToggleButton} covering:
 * <ul>
 *   <li>Row 23 — {@code actionPerformed}: when button is NOT selected after click, reselects it
 *       (sticky behavior) and does NOT fire the underlying action</li>
 *   <li>Row 24 — {@code actionPerformed}: when button IS selected after click, marks action selected
 *       and fires {@code actionPerformed} on the action</li>
 * </ul>
 */
class StickyToggleButtonTest extends UnitTest {

    private SelectableUIAction action;
    private StickyToggleButton button;

    @BeforeAll
    static void setUpClass() throws Exception {
        installFlatLafDefaults();
    }

    @BeforeEach
    void setUp() {
        action = mock(SelectableUIAction.class);
        button = new StickyToggleButton(action);
    }

    // -----------------------------------------------------------------------
    // Row 23: actionPerformed — button not selected → reselects, action NOT fired
    // -----------------------------------------------------------------------

    @Test
    void testActionPerformedWhenButtonNotSelectedReSelectsButtonWithoutFiringAction() {
        // Simulate Swing having toggled the button from selected → unselected before calling the listener
        button.setSelected(false);

        var event = new ActionEvent(button, ActionEvent.ACTION_PERFORMED, "");
        button.actionPerformed(event);

        // The button must be re-latched to true (sticky behavior)
        assertThat(button.isSelected()).isTrue();

        // The underlying action must NOT have been invoked
        verify(action, never()).setSelected(true);
        verify(action, never()).actionPerformed(any());
    }

    // -----------------------------------------------------------------------
    // Row 24: actionPerformed — button IS selected → marks action selected and fires it
    // -----------------------------------------------------------------------

    @Test
    void testActionPerformedWhenButtonSelectedMarksActionSelectedAndFiresIt() {
        // Simulate Swing having toggled the button from unselected → selected before calling the listener
        button.setSelected(true);

        var event = new ActionEvent(button, ActionEvent.ACTION_PERFORMED, "");
        button.actionPerformed(event);

        // The underlying action must be selected and its handler invoked
        verify(action).setSelected(true);
        verify(action).actionPerformed(event);
    }
}
