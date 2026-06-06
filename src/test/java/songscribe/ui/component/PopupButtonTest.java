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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

import java.awt.event.ActionEvent;

import javax.swing.event.PopupMenuEvent;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.ui.action.UIAction;
import songscribe.util.UIUtils;

/**
 * Unit tests for {@link PopupButton} covering:
 * <ul>
 *   <li>Row 25 — {@code setCurrentAction(null)}: is a no-op — no NPE thrown,
 *       {@code currentAction} is set to null, {@code configureButtonFromAction} is not called</li>
 *   <li>Row 26 — {@code setCurrentAction(non-null Selectable)}: calls {@code setSelected(true)}
 *       on the action and deselects the button</li>
 *   <li>Row 27 — {@code actionPerformed} when {@code popupWasCanceledByButton} is true:
 *       clears flag and deselects button without showing the popup</li>
 *   <li>Row 29 — {@code popupMenuWillBecomeInvisible}: deselects the button</li>
 * </ul>
 */
class PopupButtonTest extends UnitTest {

    private PopupButton button;

    @BeforeAll
    static void setUpClass() throws Exception {
        installFlatLafDefaults();
    }

    @BeforeEach
    void setUp() {
        // Construct with an empty action array and no default action
        button = new PopupButton(new UIAction[0], null);
    }

    // -----------------------------------------------------------------------
    // Row 25: setCurrentAction(null) is a no-op — no NPE, currentAction becomes null
    // -----------------------------------------------------------------------

    @Test
    void testSetCurrentActionNullDoesNotThrow() {
        assertThatCode(() -> button.setCurrentAction(null)).doesNotThrowAnyException();
    }

    @Test
    void testSetCurrentActionNullSetsCurrentActionToNull() {
        button.setCurrentAction(null);
        assertThat(button.getCurrentAction()).isNull();
    }

    // -----------------------------------------------------------------------
    // Row 26: setCurrentAction(non-null Selectable) calls setSelected(true) on
    // the action and deselects the button
    // -----------------------------------------------------------------------

    @Test
    void testSetCurrentActionSelectableCallsSetSelectedTrue() {
        // A mock that is both UIAction and UIAction.Selectable so the
        // instanceof check in setCurrentAction triggers
        var action = mock(UIAction.class, withSettings().extraInterfaces(UIAction.Selectable.class));
        button.setCurrentAction(action);
        verify((UIAction.Selectable) action).setSelected(true);
    }

    @Test
    void testSetCurrentActionSelectableDeselectsButton() {
        // Pre-select the button so the change is observable
        button.setSelected(true);
        var action = mock(UIAction.class, withSettings().extraInterfaces(UIAction.Selectable.class));
        button.setCurrentAction(action);
        assertThat(button.isSelected()).isFalse();
    }

    // -----------------------------------------------------------------------
    // Row 27: actionPerformed when popupWasCanceledByButton==true clears the
    // flag and deselects the button without re-showing the popup
    // -----------------------------------------------------------------------

    @Test
    void testActionPerformedWhenCanceledByButtonDeselectsButton() {
        // Set popupWasCanceledByButton = true by driving through popupMenuCanceled,
        // mocking UIUtils.getComponentUnderMouse() to return this button.
        try (var uiUtilsMock = mockStatic(UIUtils.class)) {
            uiUtilsMock.when(UIUtils::getComponentUnderMouse).thenReturn(button);
            button.popupMenuCanceled(new PopupMenuEvent(button));
        }

        // Pre-select so we can observe the deselect
        button.setSelected(true);
        button.actionPerformed(new ActionEvent(button, ActionEvent.ACTION_PERFORMED, ""));
        assertThat(button.isSelected()).isFalse();
    }

    // -----------------------------------------------------------------------
    // Row 29: popupMenuWillBecomeInvisible deselects the button
    // -----------------------------------------------------------------------

    @Test
    void testPopupMenuWillBecomeInvisibleDeselectsButton() {
        button.setSelected(true);
        button.popupMenuWillBecomeInvisible(new PopupMenuEvent(button));
        assertThat(button.isSelected()).isFalse();
    }
}
