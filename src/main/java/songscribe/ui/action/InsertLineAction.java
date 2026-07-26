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
import songscribe.message.command.InsertLineCommand;
import songscribe.ui.component.MainFrame;
import songscribe.util.UIUtils;

public final class InsertLineAction extends UIAction {

    // If the index is this value, add a line to the end
    public static final int ADD = -1;

    private final int shift;

    public static InsertLineAction createAddLineAction(MainFrame mainFrame) {
        return new InsertLineAction(mainFrame, Strings.get(Strings.MENU_SONG_LINE_AT_END), ADD);
    }

    public static InsertLineAction createInsertLineBeforeAction(MainFrame mainFrame) {
        return new InsertLineAction(mainFrame, Strings.get(Strings.MENU_SONG_LINE_BEFORE), 0);
    }

    public static InsertLineAction createInsertLineAfterAction(MainFrame mainFrame) {
        return new InsertLineAction(mainFrame, Strings.get(Strings.MENU_SONG_LINE_AFTER), 1);
    }

    private InsertLineAction(MainFrame mainFrame, String name, int shift) {
        super(
            mainFrame,
            name,
            getActionCommand(shift),
            (shift == ADD) ? KeyEvent.VK_ENTER : 0,
            (shift == ADD) ? UIUtils.MENU_SHORTCUT_MASK : 0,
            Flag.DISABLE_WHEN_PLAYING,
            Flag.DISABLE_WHEN_EDITING_TEXT,
            Flag.DISABLE_IN_GRACE_MODE
        );
        this.shift = shift;
        setUndoOpNameKey(Strings.ACTION_EDIT_OP_INSERT_LINE);
    }

    private static String getActionCommand(int shift) {
        if (shift == ADD) {
            return "add-line";
        }

        return (shift == 0) ? "insert-line-before" : "insert-line-after";
    }

    @Override
    protected void performAction(ActionEvent e) {
        MessageCenter.post(new InsertLineCommand(shift));
    }

    @Override
    public boolean updateEnabledState() {
        if (!super.updateEnabledState()) {
            return false;
        }

        if (shift == ADD) {
            return true;
        }

        var isEnabled = requireScoreView().getSelectionCoordinator().hasLineSelection();
        setEnabled(isEnabled);
        return isEnabled;
    }
}
