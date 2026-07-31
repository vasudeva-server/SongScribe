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

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.message.MessageCenter;
import songscribe.ui.action.Actions;
import songscribe.ui.action.UIAction;
import songscribe.ui.menu.TupletMenuItems;
import songscribe.message.notification.MusicSelectionDidChangeNotification;

public class TupletPopupButton extends PopupButton {

    public TupletPopupButton() {
        super(
            Actions.TOGGLE_TUPLET_ACTIONS,
            Actions.TOGGLE_TUPLET_ACTIONS.get(1),
            ItemStyle.RADIO
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

    /**
     * The popup only offers the grades the selection could actually become, so its items
     * are rebuilt from the current selection every time it opens.
     */
    @Override
    public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
        TupletMenuItems.rebuild(getPopup());
        super.popupMenuWillBecomeVisible(e);
    }

    @Handler
    public void musicSelectionDidChange(MusicSelectionDidChangeNotification message) {
        // Disable button if none of its actions are enabled
        setEnabled(
            Actions.TOGGLE_TUPLET_ACTIONS.stream().anyMatch(UIAction::isEnabled)
        );

        updateDefaultAction(message.getScoreViewController());
    }

    /**
     * Points the button's direct click at whatever the selection most likely wants:
     * removal when the selection is exactly an existing tuplet, otherwise the lowest
     * grade the span could actually become. Press-and-hold still opens the popup.
     * <p>
     * When neither applies the previous default is kept, because the whole button is
     * disabled in that case and swapping its action would only be noise.
     */
    private void updateDefaultAction(@Nullable ScoreViewController ctrl) {
        if (ctrl == null) {
            return;
        }

        var info = ctrl.canToggleTuplet();

        if (info.coversExisting()) {
            setCurrentAction(Actions.REMOVE_TUPLET_ACTION);
            return;
        }

        for (var action : Actions.TOGGLE_TUPLET_ACTIONS) {
            if (info.validGrades().contains(action.getTuplet().getSize())) {
                setCurrentAction(action);
                return;
            }
        }
    }
}
