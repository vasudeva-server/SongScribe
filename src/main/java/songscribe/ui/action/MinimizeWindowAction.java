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

import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import songscribe.Strings;
import songscribe.ui.component.MainFrame;
import songscribe.util.UIUtils;

/** Minimizes the main window to the Dock. */
public final class MinimizeWindowAction extends UIAction {

    public static MinimizeWindowAction createAction(MainFrame mainFrame) {
        return new MinimizeWindowAction(mainFrame);
    }

    private MinimizeWindowAction(MainFrame mainFrame) {
        super(
            mainFrame,
            Strings.get(Strings.ACTION_WINDOW_MINIMIZE),
            "window-minimize",
            KeyEvent.VK_M, UIUtils.MENU_SHORTCUT_MASK,
            // Changes the window only; the document is untouched.
            Flag.ENABLED_IN_GRACE_MODE
        );
    }

    @Override
    protected void performAction(ActionEvent e) {
        var mainFrame = getMainFrame();
        mainFrame.setExtendedState(mainFrame.getExtendedState() | Frame.ICONIFIED);
    }
}
