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
        setToolTipText(Strings.get(Strings.TOOLTIP_TUPLET));
    }

    /**
     * The popup only offers the grades the selection could actually become, so its items
     * are rebuilt from the current selection every time it opens.
     */
    @Override
    public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
        TupletMenuItems.rebuild(requirePopup());
        super.popupMenuWillBecomeVisible(e);
    }

    @Handler
    public void musicSelectionDidChange(MusicSelectionDidChangeNotification message) {
        updateDefaultAction(message.getScoreViewController());
    }

    /**
     * Which grades are valid depends on the notes and the beat, not only on where the
     * selection sits, so an edit that changes neither the selection nor the caret can still
     * change the answer. Without this the button would keep pointing at a grade the span can
     * no longer become, and clicking it would fail the validator's own precondition.
     */
    @Handler
    public void songDidChange(SongDidChangeNotification message) {
        updateDefaultAction(getScoreViewController());
    }

    @Handler
    public void documentDidLoad(DocumentDidLoadNotification message) {
        updateDefaultAction(getScoreViewController());
    }

    /**
     * The controller for the document on screen, or null before one exists. Unlike the
     * selection notification, the song and load notifications carry no controller of their
     * own, so it is looked up the same way the tuplet menu looks it up.
     */
    private static @Nullable ScoreViewController getScoreViewController() {
        var scoreView = MainFrame.getInstance().getScoreView();

        return (scoreView != null) ? scoreView.getController() : null;
    }

    /**
     * Points the button's direct click at the lowest grade the span could actually become.
     * Press-and-hold still opens the popup.
     * <p>
     * When no grade applies the previous default is kept, because the whole button is
     * disabled in that case and swapping its action would only be noise. Removing a tuplet
     * is not offered here at all — the user selects the tuplet's number or bracket and
     * deletes it directly.
     */
    private void updateDefaultAction(@Nullable ScoreViewController ctrl) {
        if (ctrl == null) {
            return;
        }

        var info = ctrl.canToggleTuplet();

        for (var action : Actions.TOGGLE_TUPLET_ACTIONS) {
            if (info.validGrades().contains(action.getTuplet().getSize())) {
                setCurrentAction(action);
                return;
            }
        }
    }
}
