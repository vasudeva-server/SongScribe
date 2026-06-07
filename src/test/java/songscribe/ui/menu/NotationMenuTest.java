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

package songscribe.ui.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import module java.desktop;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.dom.EndingValidationResult;
import songscribe.dom.EndingValidationResult.PrecedingAction;
import songscribe.ui.action.Actions;

class NotationMenuTest extends MainFrameMockTest {

    private NotationMenu notationMenu;

    @BeforeEach
    void setUp() {
        notationMenu = new NotationMenu(mainFrame());
    }

    @AfterEach
    void restoreActionState() {
        // MAKE_ENDING_ACTION is a process-wide singleton; reset to a known
        // neutral state so this test does not affect other tests in the run.
        Actions.MAKE_ENDING_ACTION.setEnabled(true);
    }

    // -------------------------------------------------------------------------
    // menuSelected — controller present: validate() is called and action enabled
    // -------------------------------------------------------------------------

    @Test
    void testMenuSelectedCallsValidateWhenControllerPresent() {
        // getScoreView() and getController() are already stubbed to non-null
        // values by MainFrameMockTest / MockEnvHelper. Stub validate()'s result.
        var validResult = EndingValidationResult.valid(PrecedingAction.NONE, 0, 1);
        when(mockEnv().ctrl().canMakeFirstSecondEnding()).thenReturn(validResult);

        // Start from disabled so the change is observable.
        Actions.MAKE_ENDING_ACTION.setEnabled(false);

        fireMenuSelected();

        assertThat(Actions.MAKE_ENDING_ACTION.isEnabled()).isTrue();
    }

    // -------------------------------------------------------------------------
    // menuSelected — no controller: MAKE_ENDING_ACTION is disabled
    // -------------------------------------------------------------------------

    @Test
    void testMenuSelectedDisablesMakeEndingWhenNoController() {
        // Override the default stub so getController() returns null.
        when(mockEnv().score().getController()).thenReturn(null);

        // Start from enabled so the change is observable.
        Actions.MAKE_ENDING_ACTION.setEnabled(true);

        fireMenuSelected();

        assertThat(Actions.MAKE_ENDING_ACTION.isEnabled()).isFalse();
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    /**
     * Fires the {@code menuSelected} event on every {@link MenuListener}
     * registered on the {@link NotationMenu}. This triggers the anonymous
     * listener added in the constructor without requiring a real AWT event.
     */
    private void fireMenuSelected() {
        var event = new MenuEvent(notationMenu);

        for (var listener : notationMenu.getMenuListeners()) {
            listener.menuSelected(event);
        }
    }
}
