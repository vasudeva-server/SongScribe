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

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.message.MessageCenter;
import songscribe.message.command.SaveCommand;
import songscribe.ui.component.MainFrame;
import songscribe.util.UIUtils;

public final class SaveAction extends UIAction {

    public static SaveAction createAction(MainFrame mainFrame) {
        return new SaveAction(mainFrame);
    }

    private SaveAction(MainFrame mainFrame) {
        super(mainFrame, Strings.get(Strings.ACTION_FILE_SAVE), "save", KeyEvent.VK_S, UIUtils.MENU_SHORTCUT_MASK);
    }

    // Bypasses the message bus so the result is available synchronously.
    @Override
    public boolean perform(@Nullable Object source) {
        return getMainFrame().save();
    }

    @Override
    protected void performAction(ActionEvent e) {
        MessageCenter.post(new SaveCommand());
    }
}
