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

import songscribe.ui.Mode;
import songscribe.ui.message.MessageCenter;
import songscribe.ui.message.ModeChangedMessage;

public class ModeAction extends UIAction {

    private final Mode mode;

    public ModeAction(
        Mode mode,
        String name,
        String icon,
        int size,
        String actionCommand,
        String tooltip
    ) {
        this(mode, name, icon, size, actionCommand, tooltip, 0, 0);
    }

    public ModeAction(
        Mode mode,
        String name,
        String icon,
        int size,
        String actionCommand,
        String tooltip,
        int virtualKey,
        int modifiers
    ) {
        super(
            name,
            icon,
            size,
            actionCommand,
            tooltip,
            true,
            virtualKey,
            modifiers
        );
        this.mode = mode;
        setFlags(Flag.DISABLE_WHEN_PLAYING);
    }

    public Mode getMode() {
        return mode;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        super.actionPerformed(e);
        MessageCenter.post(new ModeChangedMessage(this));
    }
}
