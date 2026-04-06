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

import net.engio.mbassy.listener.Handler;

import songscribe.Strings;
import songscribe.message.MessageCenter;
import songscribe.ui.action.Actions;
import songscribe.ui.action.UIAction;
import songscribe.message.notification.MusicSelectionDidChangeNotification;

public class TupletPopupButton extends PopupButton {

    public TupletPopupButton() {
        super(
            Actions.TOGGLE_TUPLET_ACTIONS,
            Actions.TOGGLE_TUPLET_ACTIONS.get(1)
        );
        addSeparator();
        addItem(new JMenuItem(Actions.REMOVE_TUPLET_ACTION));
        MessageCenter.subscribe(this);
    }

    @Override
    protected void configureButtonFromAction(UIAction action) {
        super.configureButtonFromAction(action);

        // Used a fixed tooltip for the button
        setToolTipText(Strings.get(Strings.TOOLTIP_TUPLET));
    }

    @Handler
    public void musicSelectionDidChange(MusicSelectionDidChangeNotification message) {
        // Disable button if none of its actions are enabled
        setEnabled(
            Actions.TOGGLE_TUPLET_ACTIONS.stream().anyMatch(UIAction::isEnabled)
        );
    }
}
