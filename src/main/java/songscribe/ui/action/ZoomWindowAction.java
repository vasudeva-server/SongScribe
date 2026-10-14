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

import songscribe.Strings;
import songscribe.ui.component.MainFrame;
import songscribe.util.UIUtils;

/**
 * The window zoom, as a double click on the title bar does; {@link ZoomAction} is the score zoom.
 */
public final class ZoomWindowAction extends UIAction {

    public static ZoomWindowAction createAction(MainFrame mainFrame) {
        return new ZoomWindowAction(mainFrame);
    }

    private ZoomWindowAction(MainFrame mainFrame) {
        super(
            mainFrame,
            Strings.get(Strings.ACTION_WINDOW_ZOOM),
            "window-zoom",
            KeyEvent.VK_Z, UIUtils.MENU_SHORTCUT_MASK | InputEvent.ALT_DOWN_MASK,
            // Changes the window only; the document is untouched.
            Flag.ENABLED_IN_GRACE_MODE
        );
    }

    @Override
    protected void performAction(ActionEvent e) {
        getMainFrame().toggleZoom();
    }
}
