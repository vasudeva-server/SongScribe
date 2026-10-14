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

import javax.swing.event.PopupMenuEvent;

import net.engio.mbassy.listener.Handler;
import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.message.MessageSubscription;
import songscribe.message.notification.DocumentDidLoadNotification;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.ui.action.Actions;
import songscribe.ui.action.UIAction;
import songscribe.ui.menu.TupletMenuItems;
import songscribe.util.UIUtils;

public class TupletPopupButton extends PopupMenuButton {

    public TupletPopupButton() {
        super(
            Actions.TOGGLE_TUPLET_ACTIONS,
            Actions.TOGGLE_TUPLET_ACTIONS.get(1),
            ItemStyle.RADIO
        );
        MessageSubscription.addProcessListener(this);
    }

    @Override
    protected void configureButtonFromAction(UIAction action) {
        super.configureButtonFromAction(action);

        // Used a fixed tooltip for the button
        UIUtils.setToolTipText(
            this,
            new UIUtils.ToolTipSection(Strings.get(Strings.TOOLTIP_TUPLET_TITLE), null, Strings.get(Strings.TOOLTIP_TUPLET))
        );
    }

    @Override
    public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
        TupletMenuItems.rebuild(requirePopup());
        super.popupMenuWillBecomeVisible(e);
    }

    @Handler
    public void musicSelectionDidChange(MusicSelectionDidChangeNotification message) {
        updateDefaultAction(MainFrame.getInstance().getScoreViewController());
    }

    /**
     * Which grades are valid depends on the notes and the beat, so an edit that changes neither
     * the selection nor the caret can still change the answer; otherwise clicking a stale grade
     * would fail the validator's own precondition.
     */
    @Handler
    public void songDidChange(SongDidChangeNotification message) {
        updateDefaultAction(MainFrame.getInstance().getScoreViewController());
    }

    @Handler
    public void documentDidLoad(DocumentDidLoadNotification message) {
        updateDefaultAction(MainFrame.getInstance().getScoreViewController());
    }

    /**
     * When no grade applies the previous default is kept: the whole button is disabled.
     */
    private void updateDefaultAction(@Nullable ScoreViewController ctrl) {
        if (ctrl == null) {
            return;
        }

        var info = ctrl.canToggleTuplet();

        for (var action : Actions.TOGGLE_TUPLET_ACTIONS) {
            if (info.validGrades().contains(action.getGrade())) {
                setCurrentAction(action);
                return;
            }
        }
    }
}
