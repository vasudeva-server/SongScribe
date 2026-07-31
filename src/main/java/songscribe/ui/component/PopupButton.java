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

import songscribe.message.MessageCenter;
import songscribe.ui.action.UIAction;
import songscribe.message.notification.MenuWillOpenNotification;
import songscribe.util.UIUtils;

/**
 * A button that when clicked is selected and displays a popup menu. If the button is clicked
 * while the popup menu is visible, the popup menu is hidden. The button is deselected whenever
 * the popup menu is hidden.
 */
public class PopupButton
    extends ToolbarToggleButton
    implements ActionListener, PopupMenuListener {

    /**
     * How the actions handed to the constructor are rendered in the popup.
     */
    public enum ItemStyle {
        /** A check box for a {@link UIAction.Selectable} action, a plain item otherwise. */
        AUTO,

        /**
         * A radio button for every action, driven by {@link Action#SELECTED_KEY}. No
         * {@link ButtonGroup} is installed, so the group keeps a none-selected state.
         */
        RADIO
    }

    private final JPopupMenu popup;

    // We need to know if the popup menu was canceled by clicking the button
    private boolean popupWasCanceledByButton = false;

    // The action that is currently selected in the popup menu and is used
    // to configure the button's appearance.
    private @Nullable UIAction currentAction;

    public PopupButton(
        List<? extends UIAction> actions,
        @Nullable UIAction defaultAction
    ) {
        this(actions.toArray(new UIAction[0]), defaultAction, ItemStyle.AUTO);
    }

    public PopupButton(
        List<? extends UIAction> actions,
        @Nullable UIAction defaultAction,
        ItemStyle itemStyle
    ) {
        this(actions.toArray(new UIAction[0]), defaultAction, itemStyle);
    }

    public PopupButton(UIAction[] actions, @Nullable UIAction defaultAction) {
        this(actions, defaultAction, ItemStyle.AUTO);
    }

    public PopupButton(UIAction[] actions, @Nullable UIAction defaultAction, ItemStyle itemStyle) {
        super(null);
        popup = new JPopupMenu();

        for (var action : actions) {
            popup.add(makeItem(action, itemStyle));
        }

        currentAction = defaultAction;
        addActionListener(this);
        popup.addPopupMenuListener(this);
        setCurrentAction(defaultAction);
    }

    private static JMenuItem makeItem(UIAction action, ItemStyle itemStyle) {
        if (itemStyle == ItemStyle.RADIO) {
            return new JRadioButtonMenuItem(action);
        }

        if (action instanceof UIAction.Selectable) {
            return new JCheckBoxMenuItem(action);
        }

        return new JMenuItem(action);
    }

    protected JPopupMenu getPopup() {
        return popup;
    }

    public void addSeparator() {
        popup.addSeparator();
    }

    public void addItem(JMenuItem item) {
        popup.add(item);
    }

    public @Nullable Action getCurrentAction() {
        return currentAction;
    }

    public void setCurrentAction(@Nullable UIAction action) {
        currentAction = action;

        if (action == null) {
            return;
        }

        configureButtonFromAction(action);

        if (action instanceof UIAction.Selectable selectable) {
            selectable.setSelected(true);
        }

        // This is called when an item is selected from the popup,
        // in which case this button is selected. We want to
        // deselect the button when the popup is hidden.
        setSelected(false);
    }

    protected void configureButtonFromAction(UIAction action) {
        UIUtils.configureButtonFromAction(this, action);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // If the popup was canceled by clicking the button, we don't want to show it again
        if (popupWasCanceledByButton) {
            popupWasCanceledByButton = false;
            setSelected(false);
        } else if (popup.isVisible()) {
            popup.setVisible(false);
        } else {
            var parentInsets = getParent().getInsets();
            popup.show(this, parentInsets.left, getHeight());
        }
    }

    @Override
    public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
        MessageCenter.post(new MenuWillOpenNotification(popup));
    }

    // This is called before popupMenuWillBecomeInvisible
    @Override
    public void popupMenuCanceled(PopupMenuEvent e) {
        var component = UIUtils.getComponentUnderMouse();
        popupWasCanceledByButton = Objects.equals(component, this);
    }

    @Override
    public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
        setSelected(false);
    }
}
