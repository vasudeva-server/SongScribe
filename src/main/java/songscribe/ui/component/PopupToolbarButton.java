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

import module java.desktop;

import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.ui.action.SelectableUIAction;
import songscribe.ui.component.toolbar.MainToolbarPanel;
import songscribe.ui.component.toolbar.Toolbar;

/**
 * A popup button whose panel is a toolbar of {@link StickyToggleButton}s, one per action.
 * <p>
 * The button reflects the aggregate state of its actions: it is enabled if any action is enabled,
 * and selected if any action is selected. Clicking a panel button performs its action and dismisses
 * the popup.
 */
public class PopupToolbarButton extends BasePopupButton {

    /**
     * Vertical gap between the bottom of the toolbar panel and the top of the popup.
     */
    static final int POPUP_GAP_PX = 2;
    private static final String POPUP_BORDER_CORNER_RADIUS_KEY = "Popup.borderCornerRadius";
    private static final int POPUP_BORDER_CORNER_RADIUS_PX = 8;

    /**
     * Padding between the popup's edge and the toolbar's buttons. The toolbar carries all of it,
     * because the popup's own border is zeroed — see {@link #updatePopupUI}.
     */
    static final int TOOLBAR_PADDING_X_PX = 6;
    static final int TOOLBAR_PADDING_Y_PX = 8;

    private final List<SelectableUIAction> actions;

    private final Toolbar toolbar;

    public PopupToolbarButton(SelectableUIAction... actions) {
        // One list, shared with the superclass. The superclass registers listeners that call
        // refreshFromActions(), which this class overrides to read this field — so the field has
        // to be assigned before any other statement can run, not merely before the first listener
        // happens to fire.
        var hostedActions = List.of(actions);
        super(hostedActions);
        this.actions = hostedActions;

        // updateUI() reads this field, and by the time this constructor body runs the popup is
        // already live — so the null-popup check that makes updateUI() safe during the superclass
        // constructor no longer applies. Anything that could re-trigger updateUI() must stay below
        // this line.
        toolbar = new Toolbar();

        for (var action : this.actions) {
            var button = new StickyToggleButton(action);

            // StickyToggleButton registers its own ActionListener in its constructor, so the
            // action performs first and the popup hides second.
            button.addActionListener(event -> requirePopup().setVisible(false));
            toolbar.add(button);
        }

        var popup = requirePopup();
        popup.putClientProperty(POPUP_BORDER_CORNER_RADIUS_KEY, POPUP_BORDER_CORNER_RADIUS_PX);
        popup.add(toolbar);
        updatePopupUI(popup);
        refreshFromActions();
    }

    /**
     * Makes the whole panel read as an extension of the toolbar it drops out of: the popup takes
     * the toolbar's background and gives up its own border, so the opaque toolbar paints the whole
     * panel and carries all the padding that spaces its buttons off the popup's edge.
     * <p>
     * The popup is not part of this button's component tree, so a theme change never reaches it on
     * its own — {@link #updateUI()} reapplies both, which also restores the border that
     * re-installing the toolbar's UI would otherwise put back.
     */
    private void updatePopupUI(JPopupMenu popup) {
        popup.setBackground(toolbar.getBackground());

        // A popup border inset is painted by the popup, not the toolbar, so it shows up as a band
        // in the menu background around the toolbar. Zeroing it lets the opaque toolbar paint the
        // whole panel; the rounded corners are native window chrome, so they survive.
        popup.setBorder(BorderFactory.createEmptyBorder());
        toolbar.setBorder(
            BorderFactory.createEmptyBorder(
                TOOLBAR_PADDING_Y_PX,
                TOOLBAR_PADDING_X_PX,
                TOOLBAR_PADDING_Y_PX,
                TOOLBAR_PADDING_X_PX
            )
        );
    }

    @Override
    public void updateUI() {
        super.updateUI();

        // Swing calls updateUI() from the JToggleButton constructor, before the popup exists; the
        // constructor applies the background itself once it does.
        var popup = getPopup();

        if (popup != null) {
            SwingUtilities.updateComponentTreeUI(popup);
            updatePopupUI(popup);
        }
    }

    /**
     * Adds the selected half of the derivation: the button is lit when any of its actions is.
     */
    @Override
    protected void refreshFromActions() {
        super.refreshFromActions();
        setSelected(actions.stream().anyMatch(SelectableUIAction::isSelected));
    }

    /**
     * Keeps the button lit when one of its actions is armed, rather than deselecting it.
     */
    @Override
    protected void popupDidClose() {
        refreshFromActions();
    }

    @Override
    protected int popupX() {
        return (getWidth() - requirePopup().getPreferredSize().width) / 2;
    }

    /**
     * The component the popup hangs below. The popup clears the entire toolbar panel rather than
     * just this button, so that it never overlaps the toolbars beside it. Null when the button has
     * no toolbar panel above it, which outside of tests means it is not yet in the window.
     */
    @Nullable Container popupAnchor() {
        return SwingUtilities.getAncestorOfClass(MainToolbarPanel.class, this);
    }

    @Override
    protected int popupY() {
        var panel = popupAnchor();

        if (panel == null) {
            return getHeight() + POPUP_GAP_PX;
        }

        return SwingUtilities.convertPoint(panel, 0, panel.getHeight(), this).y + POPUP_GAP_PX;
    }
}
