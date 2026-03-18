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
import songscribe.notification.MusicSelectionDidChangeNotification;
import songscribe.command.ToggleBeamCommand;

public class ToggleBeamAction extends UIAction {

    public static ToggleBeamAction createAction() {
        return new ToggleBeamAction();
    }

    private ToggleBeamAction() {
        super(
            Strings.get(Strings.ACTION_BEAM_TOGGLE),
            "beam.svg",
            28,
            "toggle-beam",
            Strings.get(Strings.ACTION_BEAM_TOGGLE_TOOLTIP),
            KeyEvent.VK_B,
            0,
            Flag.REQUIRES_MULTIPLE_SELECTION,
            Flag.DISABLE_WHEN_BAR_SELECTED,
            Flag.DISABLE_WHEN_PLAYING,
            Flag.DISABLE_WHEN_EDITING_TEXT,
            Flag.DISABLE_IN_ADJUSTMENT_MODE,
            Flag.DISABLE_IN_GRACE_MODE
        );
    }

    @Override
    @Handler
    public void musicSelectionDidChange(
        MusicSelectionDidChangeNotification message
    ) {
        if (updateEnabledState()) {
            setEnabled(message.getScore().canToggleBeaming());
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        MessageCenter.post(new ToggleBeamCommand());
    }
}
