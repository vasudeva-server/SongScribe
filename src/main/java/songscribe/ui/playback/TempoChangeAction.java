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

import java.awt.event.*;

import javax.swing.*;

import songscribe.ui.message.MessageCenter;

public class TempoChangeAction extends AbstractAction {

    static final String TIP = "Set playback tempo";
    private final int ratio;

    TempoChangeAction(int ratio) {
        super(ratio + "%");
        this.ratio = ratio;
    }

    public int getRatio() {
        return ratio;
    }

    // This is so we can add this action to a combo box using addItem()
    public String toString() {
        return (String) getValue(Action.NAME);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        MessageCenter.post(new PlaybackTempoChangedMessage(ratio));
    }
}
