/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package songscribe.ui.action;

import module java.desktop;

import songscribe.Strings;
import songscribe.prefs.RecentDocumentsManager;
import songscribe.ui.component.MainFrame;

public final class ClearRecentsAction extends UIAction {

    public static ClearRecentsAction createAction(MainFrame mainFrame) {
        return new ClearRecentsAction(mainFrame);
    }

    private ClearRecentsAction(MainFrame mainFrame) {
        super(
            mainFrame,
            Strings.get(Strings.ACTION_EDIT_CLEAR_RECENTS),
            "clear-recents",
            Flag.DISABLE_WHEN_PLAYING,
            Flag.DISABLE_IN_GRACE_MODE
        );
    }

    @Override
    protected void performAction(ActionEvent e) {
        RecentDocumentsManager.clear();
    }
}
