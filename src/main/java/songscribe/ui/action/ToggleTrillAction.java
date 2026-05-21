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

package songscribe.ui.action;

import module java.desktop;


import net.engio.mbassy.listener.Handler;

import songscribe.Strings;
import songscribe.message.MessageCenter;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.message.command.ToggleTrillCommand;
import songscribe.ui.component.MainFrame;

public final class ToggleTrillAction extends UIAction {

    public static ToggleTrillAction createAction(MainFrame mainFrame) {
        return new ToggleTrillAction(mainFrame);
    }

    private ToggleTrillAction(MainFrame mainFrame) {
        super(
            mainFrame,
            Strings.get(Strings.ACTION_TRILL_TOGGLE),
            "toggle-trill",
            Flag.REQUIRES_SELECTION,
            Flag.DISABLE_WHEN_PLAYING,
            Flag.DISABLE_WHEN_EDITING_TEXT,
            Flag.DISABLE_IN_GRACE_MODE
        );
    }

    @Override
    @Handler
    public void musicSelectionDidChange(
        MusicSelectionDidChangeNotification message
    ) {
        var ctrl = message.getScoreViewController();

        if (ctrl != null && updateEnabledState()) {
            setEnabled(ctrl.canToggleTrill());
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        MessageCenter.post(new ToggleTrillCommand());
    }
}
