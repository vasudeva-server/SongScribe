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

import songscribe.ui.message.MessageCenter;
import songscribe.ui.message.RestModeChangedMessage;

public class RestModeAction extends UIAction {

    public RestModeAction() {
        super(
            "Rest Mode",
            "@\uF371",
            22,
            "rest-mode",
            "Insert rest of selected duration",
            true,
            KeyEvent.VK_R,
            InputEvent.SHIFT_DOWN_MASK
        );
        setFlags(
            Flag.DISABLE_WHEN_PLAYING,
            Flag.DISABLE_IN_ADJUSTMENT_MODE,
            Flag.DISABLE_WHEN_BAR_SELECTED,
            Flag.ENABLE_WHEN_DURATION_SELECTED
        );
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        super.actionPerformed(e);
        MessageCenter.post(new RestModeChangedMessage());
    }
}
