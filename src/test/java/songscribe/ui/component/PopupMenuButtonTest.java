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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static songscribe.ui.component.PopupButtonTestSupport.clickButton;
import static songscribe.ui.component.PopupButtonTestSupport.showPopup;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.event.PopupMenuEvent;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import songscribe.UnitTest;
import songscribe.ui.action.UIAction;
import songscribe.util.UIUtils;

/**
 * Unit tests for {@link PopupMenuButton} covering:
 * <ul>
 *   <li>Row 25 — {@code setCurrentAction(null)}: is a no-op — no NPE thrown,
 *       {@code currentAction} is set to null, {@code configureButtonFromAction} is not called</li>
 *   <li>Row 26 — {@code setCurrentAction(non-null Selectable)}: calls {@code setSelected(true)}
 *       on the action and deselects the button</li>
 *   <li>Row 27 — {@code actionPerformed} when {@code popupWasCanceledByButton} is true:
 *       clears flag and deselects button without showing the popup</li>
 *   <li>Row 29 — {@code popupMenuWillBecomeInvisible}: deselects the button</li>
 *   <li>{@code actionPerformed} while the popup is open: hides it, so a second click on the
 *       button closes the menu</li>
 *   <li>{@code hostsAction}: true for an action bound to an item in the popup, false for an
 *       action that is not in the popup</li>
 *   <li>the enabled state {@code BasePopupButton} derives from the hosted actions, both as
 *       computed at construction and as re-derived in either direction when an action fires a
 *       property change afterwards</li>
 *   <li>{@code ItemStyle.AUTO}: a selectable action becomes a check box item</li>
 * </ul>
 */
class PopupMenuButtonTest extends UnitTest {

    /**
     * The property name {@link javax.swing.AbstractAction#setEnabled} fires under. Swing
     * publishes no constant for it, so the literal lives here rather than at each use.
     */
    private static final String ENABLED_PROPERTY = "enabled";

    private PopupMenuButton button;

    @BeforeAll
    static void setUpClass() throws Exception {
        installFlatLafDefaults();
    }

    @BeforeEach
    void setUp() {
        // Construct with an empty action array and no default action
        button = new PopupMenuButton(new UIAction[0], null);
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
        // Start from a real action, since the field is already null after construction and
        // clearing nothing would prove nothing.
        var action = mockAction("initial");
        button.setCurrentAction(action);

        assertThat(button.getCurrentAction()).as("precondition").isEqualTo(action);

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
        clickButton(button);
        assertThat(button.isSelected()).isFalse();
    }

    // -----------------------------------------------------------------------
    // A second click on the button closes the menu it opened
    // -----------------------------------------------------------------------

    @Test
    void testClickingTheButtonWhileThePopupIsOpenHidesIt() {
        var popupButton = new PopupMenuButton(new UIAction[] { mockAction("hosted") }, null);
        var popup = showPopup(popupButton);

        clickButton(popupButton);

        assertThat(popup.isVisible()).isFalse();
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

    // -----------------------------------------------------------------------
    // hostsAction: true for an action in the popup, false for a foreign action
    // -----------------------------------------------------------------------

    @Test
    void testHostsActionTrueForActionInPopup() {
        var hostedAction = mockAction("hosted");
        var popupButton = new PopupMenuButton(new UIAction[] { hostedAction }, null);
        assertThat(popupButton.hostsAction(hostedAction)).isTrue();
    }

    @Test
    void testHostsActionFalseForForeignAction() {
        var hostedAction = mockAction("hosted");
        var foreignAction = mockAction("foreign");
        var popupButton = new PopupMenuButton(new UIAction[] { hostedAction }, null);
        assertThat(popupButton.hostsAction(foreignAction)).isFalse();
    }

    // -----------------------------------------------------------------------
    // Enabled state, hoisted into BasePopupButton, is the OR of the hosted actions'
    // enabled states.
    // -----------------------------------------------------------------------

    @Test
    void testButtonIsEnabledWhenOneOfTheHostedActionsIsEnabled() {
        var popupButton = new PopupMenuButton(
            new UIAction[] { mockAction("disabled", false), mockAction("enabled", true) },
            null
        );

        assertThat(popupButton.isEnabled()).isTrue();
    }

    @Test
    void testButtonIsDisabledWhenAllHostedActionsAreDisabled() {
        var popupButton = new PopupMenuButton(
            new UIAction[] { mockAction("first", false), mockAction("second", false) },
            null
        );

        assertThat(popupButton.isEnabled()).isFalse();
    }

    @Test
    void testButtonRelightsWhenAnActionBecomesEnabledAfterConstruction() {
        var action = mockAction("hosted", false);
        var popupButton = new PopupMenuButton(new UIAction[] { action }, null);

        assertThat(popupButton.isEnabled()).as("nothing is enabled yet").isFalse();

        var listeners = ArgumentCaptor.forClass(PropertyChangeListener.class);
        verify(action, atLeastOnce()).addPropertyChangeListener(listeners.capture());

        when(action.isEnabled()).thenReturn(true);

        var event = new PropertyChangeEvent(action, ENABLED_PROPERTY, false, true);
        listeners.getAllValues().forEach(listener -> listener.propertyChange(event));

        assertThat(popupButton.isEnabled()).isTrue();
    }

    /**
     * The derivation has to run both ways. A version that only ever lights the button up would
     * leave it clickable after its actions died — the exact staleness this derivation replaced.
     */
    @Test
    void testButtonGoesDarkWhenItsLastEnabledActionIsDisabledAfterConstruction() {
        var action = mockAction("hosted", true);
        var popupButton = new PopupMenuButton(new UIAction[] { action }, null);

        assertThat(popupButton.isEnabled()).as("the action starts live").isTrue();

        var listeners = ArgumentCaptor.forClass(PropertyChangeListener.class);
        verify(action, atLeastOnce()).addPropertyChangeListener(listeners.capture());

        when(action.isEnabled()).thenReturn(false);

        var event = new PropertyChangeEvent(action, ENABLED_PROPERTY, true, false);
        listeners.getAllValues().forEach(listener -> listener.propertyChange(event));

        assertThat(popupButton.isEnabled()).isFalse();
    }

    /**
     * A button with nothing behind it can only open an empty popup. The shared fixture is built
     * from an empty action array, which is also the case that separates "any action is live"
     * from "every action is live" — the latter is vacuously true and would light the button.
     */
    @Test
    void testButtonWithNoHostedActionsIsDisabled() {
        assertThat(button.isEnabled()).isFalse();
    }

    // -----------------------------------------------------------------------
    // ItemStyle.AUTO gives a selectable action a check box it can show its state in
    // -----------------------------------------------------------------------

    @Test
    void testSelectableActionIsRenderedAsACheckBoxItem() {
        var action = mock(UIAction.class, withSettings().extraInterfaces(UIAction.Selectable.class));
        var popupButton = new PopupMenuButton(new UIAction[] { action }, null);

        assertThat(popupButton.requirePopup().getComponent(0))
            .as("a plain item has nowhere to show that the action is selected")
            .isInstanceOf(JCheckBoxMenuItem.class);
    }

    private static UIAction mockAction(String actionCommand) {
        return mockAction(actionCommand, false);
    }

    private static UIAction mockAction(String actionCommand, boolean enabled) {
        var action = mock(UIAction.class);
        when(action.getActionCommand()).thenReturn(actionCommand);
        when(action.isEnabled()).thenReturn(enabled);
        return action;
    }
}
