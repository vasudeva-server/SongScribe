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
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import songscribe.error.RuntimeError;
import songscribe.message.MessageCenter;
import songscribe.message.notification.MenuWillOpenNotification;
import songscribe.ui.action.UIAction;
import songscribe.util.UIUtils;

/**
 * A button that when clicked is selected and displays a popup. If the button is clicked while the
 * popup is visible, the popup is hidden. The button is deselected whenever the popup is hidden.
 * <p>
 * Subclasses decide what the popup contains: {@link PopupMenuButton} fills it with menu items,
 * while other flavors may host an arbitrary component.
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
     * The popup, which is null only while {@link AbstractButton}'s constructor runs —
     * it calls {@code updateUI()} before this class's constructor has created the popup. Only an
     * {@code updateUI()} override has any reason to call this; everything else wants
     * {@link #requirePopup()}.
     */
    protected @Nullable JPopupMenu getPopup() {
        return popup;
    }

    /**
     * The popup, for the callers that run after construction and so can never see it null.
     */
    protected JPopupMenu requirePopup() {
        var result = popup;

        if (result == null) {
            throw RuntimeError.exit("popup read before it was created");
        }

        return result;
    }

    /**
     * The x offset, relative to this button, at which the popup is shown.
     */
    protected int popupX() {
        return getParent().getInsets().left;
    }

    /**
     * The y offset, relative to this button, at which the popup is shown.
     */
    protected int popupY() {
        return getHeight();
    }

    /**
     * Called when the popup has been hidden, whatever the reason.
     */
    protected void popupDidClose() {
        setSelected(false);
    }

    /**
     * Enables the button when at least one of the actions behind it can be performed. A button
     * that stays live while every one of its actions is dead opens a popup the user cannot use.
     * <p>
     * This is private precisely so the constructor can call it: an overridable method would run
     * against a subclass whose fields have not been assigned yet.
     */
    private void setEnabledFromActions() {
        setEnabled(actions.stream().anyMatch(UIAction::isEnabled));
    }

    /**
     * Recomputes this button's state from its actions, called whenever any of them fires a
     * property change. Subclasses override this to derive further state, calling {@code super}
     * first.
     * <p>
     * This must remain a pure recomputation: a single selection change fires a burst of property
     * events and the result is last-write-wins.
     */
    protected void refreshFromActions() {
        setEnabledFromActions();
    }

    /**
     * Whether the given action is represented by a button somewhere in the popup, either because
     * the button is bound to the action or because the button's name is the action's command.
     */
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
