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

import songscribe.Strings;
import songscribe.ui.component.MainFrame;
import songscribe.ui.dialog.PreferencesDialog;

public class PreferencesOpenAction
    extends DialogOpenAction<PreferencesDialog>
    implements UIAction.AppMenuAction {

    private static final String NATIVE_MENU_TITLE = "Settings";

    public PreferencesOpenAction(MainFrame mainFrame) {
        super(mainFrame, Strings.get(Strings.ACTION_SETTINGS), PreferencesDialog::new);
    }

    @Override
    public String getNativeMenuTitle() {
        return NATIVE_MENU_TITLE;
    }
}
