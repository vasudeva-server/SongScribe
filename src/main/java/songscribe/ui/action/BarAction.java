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

import java.awt.event.*;

import songscribe.music.NoteType;
import songscribe.ui.message.BarSelectedMessage;
import songscribe.ui.message.MessageCenter;

public class BarAction extends StickyUIAction {

    private final NoteType type;

    public BarAction(
        NoteType type,
        String name,
        String icon,
        int size,
        String actionCommand,
        String tooltip,
        int virtualKey,
        int modifiers
    ) {
        super(name, icon, size, actionCommand, tooltip, virtualKey, modifiers);
        this.type = type;
        setFlags(
            Flag.DISABLE_IN_REST_MODE,
            Flag.DISABLE_WHEN_PLAYING,
            Flag.DISABLE_IN_ADJUSTMENT_MODE
        );
    }

    public NoteType getType() {
        return type;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (doActionPerformed(e)) {
            MessageCenter.post(new BarSelectedMessage(type));
        }
    }
}
