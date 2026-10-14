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

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Objects;
import javax.swing.AbstractButton;
import javax.swing.JPopupMenu;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import org.jspecify.annotations.Nullable;

import songscribe.error.RuntimeError;
import songscribe.message.MessageCenter;
import songscribe.message.notification.MenuWillOpenNotification;
import songscribe.ui.action.UIAction;
import songscribe.util.UIUtils;

/**
 * A button that when clicked is selected and displays a popup. If the button is clicked while the
 * popup is visible, the popup is hidden. The button is deselected whenever the popup is hidden.
 */
public class BasePopupButton
    extends ToolbarToggleButton
    implements ActionListener, PopupMenuListener {

    private final @Nullable JPopupMenu popup;

    private final List<UIAction> actions;

    // We need to know if the popup menu was canceled by clicking the button
    private boolean popupWasCanceledByButton = false;

    protected BasePopupButton(List<? extends UIAction> actions) {
        super(null);
        this.actions = List.copyOf(actions);
        popup = new JPopupMenu();
        addActionListener(this);
        popup.addPopupMenuListener(this);

        // AbstractAction fires an "enabled" property change on every change, so this catches
        // every reason an action can go dead, not just a selection change. The property name is
        // deliberately not filtered: the derivation is a pure recomputation, so a stray extra
        // call is harmless.
        for (var action : this.actions) {
            action.addPropertyChangeListener(event -> refreshFromActions());
        }

        setEnabledFromActions();
    }

    /**
     * Null only while {@link AbstractButton}'s constructor runs, which calls {@code updateUI()}
     * before the popup exists. Only an {@code updateUI()} override calls this; everything else
     * wants {@link #requirePopup()}.
     */
    protected @Nullable JPopupMenu getPopup() {
        return popup;
    }

    protected JPopupMenu requirePopup() {
        var result = popup;

        if (result == null) {
            throw RuntimeError.exit("popup read before it was created");
        }

        return result;
    }

    protected int popupX() {
        return getParent().getInsets().left;
    }

    protected int popupY() {
        return getHeight();
    }

    protected void popupDidClose() {
        setSelected(false);
    }

    /**
     * Private so the constructor can call it: an overridable method would run against a subclass
     * whose fields are not yet assigned.
     */
    private void setEnabledFromActions() {
        setEnabled(actions.stream().anyMatch(UIAction::isEnabled));
    }

    /**
     * Subclasses override this to derive further state, calling {@code super} first. It must stay
     * a pure recomputation: a single selection change fires a burst of property events.
     */
    protected void refreshFromActions() {
        setEnabledFromActions();
    }

    public boolean hostsAction(UIAction action) {
        return containsAction(requirePopup(), action);
    }

    private static boolean containsAction(Container container, UIAction action) {
        for (var component : container.getComponents()) {
            if (component instanceof AbstractButton button &&
                (button.getAction() == action ||
                    action.getActionCommand().equals(button.getName()))) {
                return true;
            }

            if (component instanceof Container childContainer &&
                containsAction(childContainer, action)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        var popupMenu = requirePopup();

        // If the popup was canceled by clicking the button, we don't want to show it again
        if (popupWasCanceledByButton) {
            popupWasCanceledByButton = false;
            setSelected(false);
        } else if (popupMenu.isVisible()) {
            popupMenu.setVisible(false);
        } else {
            popupMenu.show(this, popupX(), popupY());
        }
    }

    @Override
    public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
        MessageCenter.post(new MenuWillOpenNotification(requirePopup()));
    }

    // This is called before popupMenuWillBecomeInvisible
    @Override
    public void popupMenuCanceled(PopupMenuEvent e) {
        var component = UIUtils.getComponentUnderMouse();
        popupWasCanceledByButton = Objects.equals(component, this);
    }

    @Override
    public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
        popupDidClose();
    }
}
