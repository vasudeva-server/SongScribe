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
import songscribe.ui.component.MainFrame;
import songscribe.util.UIUtils;

public final class PasteAction extends PasteboardAction {

    public static PasteAction createAction(MainFrame mainFrame) {
        return new PasteAction(mainFrame);
    }

    private PasteAction(MainFrame mainFrame) {
        super(
            mainFrame,
            Operation.PASTE,
            Strings.get(Strings.ACTION_EDIT_PASTE),
            "edit-paste",
            KeyEvent.VK_V,
            UIUtils.MENU_SHORTCUT_MASK,
            Flag.DISABLE_WHEN_PLAYING,
            Flag.DISABLE_IN_GRACE_MODE,
            Flag.DISABLE_WHEN_EDITING_TEXT
        );
        setUndoOpNameKey(Strings.ACTION_EDIT_OP_PASTE);
    }
}
