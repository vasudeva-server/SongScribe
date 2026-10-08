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

import java.awt.event.KeyEvent;

import songscribe.Strings;
import songscribe.message.command.PasteboardOpCommand;
import songscribe.ui.component.MainFrame;
import songscribe.util.UIUtils;

public final class CutAction extends PasteboardAction {

    public static CutAction createAction(MainFrame mainFrame) {
        return new CutAction(mainFrame);
    }

    private CutAction(MainFrame mainFrame) {
        super(
            mainFrame,
            PasteboardOpCommand.Operation.CUT,
            Strings.get(Strings.ACTION_EDIT_CUT),
            "edit-cut",
            KeyEvent.VK_X,
            UIUtils.MENU_SHORTCUT_MASK,
            Flag.DISABLE_WHEN_PLAYING,
            // Acts only on a range selection, and none can exist in grace mode: it runs
            // inside EDIT mode, where a lyric is the only selectable thing.
            Flag.ENABLED_IN_GRACE_MODE
        );
        setUndoOpNameKey(Strings.ACTION_EDIT_OP_CUT);
    }
}
