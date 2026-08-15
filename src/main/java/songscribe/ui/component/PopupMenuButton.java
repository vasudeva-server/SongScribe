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

import java.util.List;
import javax.swing.Action;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;

import org.jspecify.annotations.Nullable;

import songscribe.ui.action.UIAction;
import songscribe.util.UIUtils;

/**
 * A {@link BasePopupButton} whose popup is a menu built from a list of actions.
 */
public class PopupMenuButton extends BasePopupButton {

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

    // The action that is currently selected in the popup menu and is used
    // to configure the button's appearance.
    private @Nullable UIAction currentAction;

    public PopupMenuButton(
        List<? extends UIAction> actions,
        @Nullable UIAction defaultAction
    ) {
        this(actions.toArray(new UIAction[0]), defaultAction, ItemStyle.AUTO);
    }

    public PopupMenuButton(
        List<? extends UIAction> actions,
        @Nullable UIAction defaultAction,
        ItemStyle itemStyle
    ) {
        this(actions.toArray(new UIAction[0]), defaultAction, itemStyle);
    }

    public PopupMenuButton(UIAction[] actions, @Nullable UIAction defaultAction) {
        this(actions, defaultAction, ItemStyle.AUTO);
    }

    public PopupMenuButton(UIAction[] actions, @Nullable UIAction defaultAction, ItemStyle itemStyle) {
        super(List.of(actions));

        var popup = requirePopup();

        for (var action : actions) {
            popup.add(makeItem(action, itemStyle));
        }

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

    public void addSeparator() {
        requirePopup().addSeparator();
    }

    public void addItem(JMenuItem item) {
        requirePopup().add(item);
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
}
