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
package songscribe.ui.playback;

import java.awt.event.ActionEvent;

import songscribe.Strings;
import songscribe.message.MessageCenter;
import songscribe.message.command.ToggleLoopPlaybackCommand;
import songscribe.prefs.PrefsKey;
import songscribe.ui.action.SelectableUIAction;
import songscribe.ui.component.MainFrame;

public final class LoopPlaybackAction extends SelectableUIAction {

    public static LoopPlaybackAction createAction(MainFrame mainFrame) {
        return new LoopPlaybackAction(mainFrame);
    }

    private LoopPlaybackAction(MainFrame mainFrame) {
        super(
            mainFrame,
            Strings.get(Strings.ACTION_PLAY_LOOP),
            "@\uF358",
            20,
            "loop-playback",
            Strings.get(Strings.ACTION_PLAY_LOOP_TOOLTIP),
            PrefsKey.LOOP_PLAYBACK,
            Flag.DISABLE_WHEN_PLAYING,
            Flag.DISABLE_WHEN_EDITING_TEXT,
            Flag.DISABLE_IN_GRACE_MODE,
            Flag.DISABLE_WHEN_MIDI_UNAVAILABLE
        );
    }

    @Override
    protected void performAction(ActionEvent e) {
        MessageCenter.post(new ToggleLoopPlaybackCommand(isSelected()));
    }
}
