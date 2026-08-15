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
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import com.formdev.flatlaf.util.SystemInfo;

import songscribe.Strings;
import songscribe.lifecycle.Shutdown;
import songscribe.ui.component.MainFrame;

public class QuitAction extends UIAction {

    public static final String NAME = SystemInfo.isMacOS ? Strings.get(Strings.ACTION_FILE_QUIT) : Strings.get(Strings.ACTION_FILE_EXIT);

    public static QuitAction createAction(MainFrame mainFrame) {
        return new QuitAction(mainFrame);
    }

    protected QuitAction(MainFrame mainFrame) {
        // On macOS, the system sets the accelerator for the Quit menu item
        super(
            mainFrame,
            NAME,
            "quit",
            SystemInfo.isMacOS ? 0 : KeyEvent.VK_F4,
            SystemInfo.isMacOS ? 0 : InputEvent.ALT_DOWN_MASK
        );
    }

    @Override
    protected void performAction(ActionEvent e) {
        Shutdown.now();
    }
}
