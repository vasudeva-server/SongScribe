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
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static songscribe.ui.component.PopupButtonTestSupport.showPopup;

import java.awt.Container;
import java.awt.Insets;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JPanel;
import javax.swing.event.PopupMenuEvent;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import songscribe.UnitTest;
import songscribe.ui.action.SelectableUIAction;
import songscribe.ui.component.toolbar.Toolbar;

/**
 * Unit tests for {@link PopupToolbarButton}.
 */
class PopupToolbarButtonTest extends UnitTest {

    private static final String FIRST_COMMAND = "first";
    private static final String SECOND_COMMAND = "second";
    private static final String THIRD_COMMAND = "third";
    private static final String FOREIGN_COMMAND = "foreign";

    /**
     * The property name {@link javax.swing.AbstractAction#setEnabled} fires under. Swing
     * publishes no constant for it, so the literal lives here rather than at each use.
     */
    private static final String ENABLED_PROPERTY = "enabled";

    /**
     * A button deliberately much wider than it is tall, and much wider than the popup it drops
     * out, so that geometry reaching for the wrong dimension — or dropping a term altogether —
     * cannot agree with the expected value by accident.
     */
    private static final int BUTTON_WIDTH_PX = 100;
    private static final int BUTTON_HEIGHT_PX = 24;

    /** A stand-in toolbar panel, taller than the button and with the button partway down it. */
    private static final int PANEL_WIDTH_PX = 200;
    private static final int PANEL_HEIGHT_PX = 90;
    private static final int BUTTON_Y_IN_PANEL_PX = 30;

    /** Centering divides an odd width difference in half, so it can land a pixel off. */
    private static final double CENTERING_TOLERANCE_PX = 1.0;

    @BeforeAll
    static void setUpClass() throws Exception {
        installFlatLafDefaults();
    }

    // -----------------------------------------------------------------------
    // The popup hosts one button per action, in the order the actions were given
    // -----------------------------------------------------------------------

    @Test
    void testPopupHostsOneButtonPerActionInOrder() {
        var button = new PopupToolbarButton(
            enabledAction(FIRST_COMMAND),
            enabledAction(SECOND_COMMAND),
            enabledAction(THIRD_COMMAND)
        );

        assertThat(panelButtons(button))
            .extracting(AbstractButton::getName)
            .containsExactly(FIRST_COMMAND, SECOND_COMMAND, THIRD_COMMAND);
    }

    // -----------------------------------------------------------------------
    // Selected state is the OR of the actions' selected states
    // -----------------------------------------------------------------------

    @Test
    void testButtonIsSelectedWhenAnyActionIsSelected() {
        var selectedAction = enabledAction(SECOND_COMMAND);
        when(selectedAction.isSelected()).thenReturn(true);

        var button = new PopupToolbarButton(enabledAction(FIRST_COMMAND), selectedAction);

        assertThat(button.isSelected()).isTrue();
    }

    @Test
    void testButtonIsNotSelectedWhenNoActionIsSelected() {
        var button = new PopupToolbarButton(enabledAction(FIRST_COMMAND), enabledAction(SECOND_COMMAND));

        assertThat(button.isSelected()).isFalse();
    }

    /**
     * The constructor's own computation only proves the state at construction. What the user
     * actually does is pick a barline and watch the toolbar button light up, which runs through
     * the property listener the constructor registers on each action.
     */
    @Test
    void testButtonRelightsWhenAnActionBecomesSelectedAfterConstruction() {
        var action = enabledAction(FIRST_COMMAND);
        var button = new PopupToolbarButton(action);

        assertThat(button.isSelected()).as("nothing is selected yet").isFalse();

        when(action.isSelected()).thenReturn(true);
        firePropertyChange(action, Action.SELECTED_KEY, false, true);

        assertThat(button.isSelected()).isTrue();
    }

    /**
     * The derivation has to run both ways. A version that only ever lit the button would leave
     * it looking armed after the user picked something else.
     */
    @Test
    void testButtonGoesDarkWhenTheLastSelectedActionIsDeselectedAfterConstruction() {
        var action = enabledAction(FIRST_COMMAND);
        when(action.isSelected()).thenReturn(true);

        var button = new PopupToolbarButton(action);

        assertThat(button.isSelected()).as("the action starts armed").isTrue();

        when(action.isSelected()).thenReturn(false);
        firePropertyChange(action, Action.SELECTED_KEY, true, false);

        assertThat(button.isSelected()).isFalse();
    }

    // -----------------------------------------------------------------------
    // Closing the popup must not extinguish the button while an action is armed.
    // The inherited popupDidClose() deselects unconditionally; the override recomputes.
    // -----------------------------------------------------------------------

    @Test
    void testButtonStaysSelectedAfterPopupClosesWithSelectedAction() {
        var selectedAction = enabledAction(FIRST_COMMAND);
        when(selectedAction.isSelected()).thenReturn(true);

        var button = new PopupToolbarButton(selectedAction);
        button.popupMenuWillBecomeInvisible(new PopupMenuEvent(button.getPopup()));

        assertThat(button.isSelected()).isTrue();
    }

    @Test
    void testButtonIsDeselectedAfterPopupClosesWithNoSelectedAction() {
        var button = new PopupToolbarButton(enabledAction(FIRST_COMMAND));
        button.setSelected(true);
        button.popupMenuWillBecomeInvisible(new PopupMenuEvent(button.getPopup()));

        assertThat(button.isSelected()).isFalse();
    }

    // -----------------------------------------------------------------------
    // Enabled state is the OR of the actions' enabled states. A button that stays
    // enabled while every hosted action is dead opens a panel the user cannot use.
    // -----------------------------------------------------------------------

    @Test
    void testButtonIsEnabledWhenAnyActionIsEnabled() {
        var button = new PopupToolbarButton(disabledAction(FIRST_COMMAND), enabledAction(SECOND_COMMAND));

        assertThat(button.isEnabled()).isTrue();
    }

    @Test
    void testButtonIsDisabledWhenAllActionsAreDisabled() {
        var button = new PopupToolbarButton(disabledAction(FIRST_COMMAND), disabledAction(SECOND_COMMAND));

        assertThat(button.isEnabled()).isFalse();
    }

    /**
     * The two tests above only prove the state the constructor computed. What the user sees is
     * an action coming back to life — leaving rest mode, stopping playback — and the button
     * following it, which runs through the base class's half of {@code refreshFromActions()}.
     * Drop that half and both tests above still pass.
     */
    @Test
    void testButtonRelightsWhenAnActionBecomesEnabledAfterConstruction() {
        var action = disabledAction(FIRST_COMMAND);
        var button = new PopupToolbarButton(action);

        assertThat(button.isEnabled()).as("nothing is enabled yet").isFalse();

        when(action.isEnabled()).thenReturn(true);
        firePropertyChange(action, ENABLED_PROPERTY, false, true);

        assertThat(button.isEnabled()).isTrue();
    }

    /**
     * A button with nothing behind it can only open an empty panel. This is also the case that
     * separates "any action is live" from "every action is live" — the latter is vacuously true
     * on an empty list and would leave the button clickable.
     */
    @Test
    void testButtonWithNoHostedActionsIsDisabled() {
        assertThat(new PopupToolbarButton().isEnabled()).isFalse();
    }

    // -----------------------------------------------------------------------
    // Clicking a panel button dismisses the popup — but only if it is live
    // -----------------------------------------------------------------------

    @Test
    void testClickingEnabledPanelButtonHidesPopup() {
        var button = new PopupToolbarButton(enabledAction(FIRST_COMMAND));
        var popup = showPopup(button);

        panelButtons(button).getFirst().doClick();

        assertThat(popup.isVisible()).isFalse();
    }

    @Test
    void testClickingDisabledPanelButtonLeavesPopupVisible() {
        var button = new PopupToolbarButton(disabledAction(FIRST_COMMAND));
        var popup = showPopup(button);
        var panelButton = panelButtons(button).getFirst();

        assertThat(panelButton.isEnabled()).as("panel button should be dead").isFalse();

        panelButton.doClick();

        assertThat(popup.isVisible()).isTrue();

        popup.setVisible(false);
    }

    // -----------------------------------------------------------------------
    // Where the popup lands
    // -----------------------------------------------------------------------

    @Test
    void testPopupIsCenteredUnderTheButton() {
        var button = sizedButton(enabledAction(FIRST_COMMAND));
        var popupWidth = button.requirePopup().getPreferredSize().width;
        var popupCenterX = button.popupX() + (popupWidth / 2.0);

        assertThat(popupCenterX)
            .as("the panel's center should sit on the button's center")
            .isCloseTo(BUTTON_WIDTH_PX / 2.0, within(CENTERING_TOLERANCE_PX));
    }

    /**
     * In the window the popup clears the whole toolbar panel, not just the button, so that it
     * never overlaps the toolbars beside it.
     */
    @Test
    void testPopupHangsBelowTheWholeToolbarPanelWhenThereIsOne() {
        var panel = new JPanel(null);
        panel.setSize(PANEL_WIDTH_PX, PANEL_HEIGHT_PX);

        var button = new AnchoredPopupToolbarButton(panel, enabledAction(FIRST_COMMAND));
        button.setBounds(0, BUTTON_Y_IN_PANEL_PX, BUTTON_WIDTH_PX, BUTTON_HEIGHT_PX);
        panel.add(button);

        // Measured from the button's own frame, the panel's bottom edge is as far down as the
        // panel is tall, less how far down the panel the button already sits.
        assertThat(button.popupY())
            .isEqualTo(PANEL_HEIGHT_PX - BUTTON_Y_IN_PANEL_PX + PopupToolbarButton.POPUP_GAP_PX);
    }

    @Test
    void testPopupYFallsBackToButtonHeightPlusGapWithoutToolbarPanelAncestor() {
        var button = sizedButton(enabledAction(FIRST_COMMAND));

        assertThat(button.popupY()).isEqualTo(BUTTON_HEIGHT_PX + PopupToolbarButton.POPUP_GAP_PX);
    }

    // -----------------------------------------------------------------------
    // hostsAction resolves through the panel buttons' names, since StickyToggleButton
    // deliberately does not bind itself to the action
    // -----------------------------------------------------------------------

    @Test
    void testHostsActionIsTrueForAToolbarHostedAction() {
        var hostedAction = enabledAction(FIRST_COMMAND);
        var button = new PopupToolbarButton(hostedAction);

        assertThat(button.hostsAction(hostedAction)).isTrue();
    }

    @Test
    void testHostsActionIsFalseForAForeignAction() {
        var button = new PopupToolbarButton(enabledAction(FIRST_COMMAND));

        assertThat(button.hostsAction(enabledAction(FOREIGN_COMMAND))).isFalse();
    }

    // -----------------------------------------------------------------------
    // The opaque toolbar paints the whole panel. A popup border inset is painted by the
    // popup in the menu background, so any inset left on it shows as a mismatched band
    // around the toolbar — the toolbar has to carry the padding instead.
    // -----------------------------------------------------------------------

    @Test
    void testPopupKeepsNoBorderInsetOfItsOwn() {
        var button = new PopupToolbarButton(enabledAction(FIRST_COMMAND));

        assertThat(button.requirePopup().getInsets())
            .as("a popup inset is painted in the menu background, leaving a visible band")
            .isEqualTo(new Insets(0, 0, 0, 0));
    }

    @Test
    void testToolbarCarriesThePanelPadding() {
        var button = new PopupToolbarButton(enabledAction(FIRST_COMMAND));

        assertThat(panelToolbar(button).getInsets()).isEqualTo(expectedToolbarInsets());
    }

    @Test
    void testPopupTakesTheToolbarBackground() {
        var button = new PopupToolbarButton(enabledAction(FIRST_COMMAND));

        assertThat(button.requirePopup().getBackground())
            .isEqualTo(panelToolbar(button).getBackground());
    }

    @Test
    void testPanelKeepsItsBordersWhenTheUIIsReinstalled() {
        var button = new PopupToolbarButton(enabledAction(FIRST_COMMAND));

        // A theme change reinstalls the LaF's own borders over both components; the class has to
        // reapply on top, which it cannot do if the borders are only set in the constructor.
        button.updateUI();

        assertThat(button.requirePopup().getInsets()).isEqualTo(new Insets(0, 0, 0, 0));
        assertThat(panelToolbar(button).getInsets()).isEqualTo(expectedToolbarInsets());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * A button anchored to a stand-in panel. The real {@code MainToolbarPanel} is final and builds
     * the entire application toolbar in its constructor, which is far more than this geometry
     * needs.
     */
    private static final class AnchoredPopupToolbarButton extends PopupToolbarButton {

        private final Container anchor;

        AnchoredPopupToolbarButton(Container anchor, SelectableUIAction... actions) {
            super(actions);
            this.anchor = anchor;
        }

        @Override
        Container popupAnchor() {
            return anchor;
        }
    }

    private static Insets expectedToolbarInsets() {
        return new Insets(
            PopupToolbarButton.TOOLBAR_PADDING_Y_PX,
            PopupToolbarButton.TOOLBAR_PADDING_X_PX,
            PopupToolbarButton.TOOLBAR_PADDING_Y_PX,
            PopupToolbarButton.TOOLBAR_PADDING_X_PX
        );
    }

    private static PopupToolbarButton sizedButton(SelectableUIAction... actions) {
        var button = new PopupToolbarButton(actions);
        button.setSize(BUTTON_WIDTH_PX, BUTTON_HEIGHT_PX);

        return button;
    }

    private static SelectableUIAction enabledAction(String actionCommand) {
        return mockAction(actionCommand, true);
    }

    private static SelectableUIAction disabledAction(String actionCommand) {
        return mockAction(actionCommand, false);
    }

    /**
     * Delivers a property change to every listener registered on the action. Both the popup
     * button and the panel's {@link StickyToggleButton} listen on it, so the event has to reach
     * all of them rather than relying on registration order.
     */
    private static void firePropertyChange(
        SelectableUIAction action,
        String propertyName,
        Object oldValue,
        Object newValue
    ) {
        var listeners = ArgumentCaptor.forClass(PropertyChangeListener.class);
        verify(action, atLeastOnce()).addPropertyChangeListener(listeners.capture());

        var event = new PropertyChangeEvent(action, propertyName, oldValue, newValue);
        listeners.getAllValues().forEach(listener -> listener.propertyChange(event));
    }

    private static SelectableUIAction mockAction(String actionCommand, boolean enabled) {
        var action = mock(SelectableUIAction.class);
        when(action.getActionCommand()).thenReturn(actionCommand);
        when(action.isEnabled()).thenReturn(enabled);

        return action;
    }

    private static Toolbar panelToolbar(PopupToolbarButton button) {
        return (Toolbar) button.requirePopup().getComponent(0);
    }

    private static List<AbstractButton> panelButtons(PopupToolbarButton button) {
        return Arrays.stream(panelToolbar(button).getComponents())
            .filter(AbstractButton.class::isInstance)
            .map(AbstractButton.class::cast)
            .toList();
    }
}
