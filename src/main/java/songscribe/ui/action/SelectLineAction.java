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

import songscribe.Strings;
import songscribe.message.MessageCenter;
import songscribe.command.SelectLineCommand;
import songscribe.util.UIUtils;

public class SelectLineAction extends UIAction {

    public static SelectLineAction createAction() {
        return new SelectLineAction();
    }

    private SelectLineAction() {
        super(
            Strings.get(Strings.ACTION_EDIT_SELECT_LINE),
            "select-line",
            KeyEvent.VK_L,
            InputEvent.SHIFT_DOWN_MASK + UIUtils.MENU_SHORTCUT_MASK,
            Flag.REQUIRES_SELECTION,
            Flag.DISABLE_WHEN_PLAYING,
            Flag.DISABLE_IN_GRACE_MODE
        );
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        MessageCenter.post(new SelectLineCommand());
    }
}
