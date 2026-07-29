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

import module java.desktop;

import songscribe.Strings;
import songscribe.ui.component.MainFrame;
import songscribe.util.UIUtils;

public final class RewindAction extends SequencerAction {

    public static RewindAction createAction(MainFrame mainFrame) {
        return new RewindAction(mainFrame);
    }

    private RewindAction(MainFrame mainFrame) {
        super(
            mainFrame,
            Strings.get(Strings.ACTION_PLAY_REWIND),
            "@\uF448",
            20,
            "rewind",
            Strings.get(Strings.ACTION_PLAY_REWIND_TOOLTIP),
            KeyEvent.VK_PERIOD,
            UIUtils.MENU_SHORTCUT_MASK,
            Flag.DISABLE_WHEN_EDITING_TEXT,
            Flag.DISABLE_WHEN_SONG_EMPTY,
            Flag.DISABLE_WHEN_MIDI_UNAVAILABLE
        );
    }

    @Override
    protected void performAction(ActionEvent e) {
        PlaybackController.rewindToBeginning();
    }
}
