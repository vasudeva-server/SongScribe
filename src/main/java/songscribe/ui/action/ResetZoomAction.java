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
import songscribe.ui.ZoomController;
import songscribe.ui.component.MainFrame;
import songscribe.util.UIUtils;

public final class ResetZoomAction extends UIAction {

    public static ResetZoomAction createAction(MainFrame mainFrame) {
        return new ResetZoomAction(mainFrame);
    }

    private ResetZoomAction(MainFrame mainFrame) {
        super(
            mainFrame,
            Strings.get(Strings.ACTION_ZOOM_RESET),
            "reset-zoom",
            KeyEvent.VK_0,
            UIUtils.MENU_SHORTCUT_MASK,
            Flag.DISABLE_WHEN_EDITING_TEXT
        );
    }

    @Override
    protected void performAction(ActionEvent e) {
        ZoomController.resetZoom();
    }
}
