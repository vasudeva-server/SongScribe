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

import java.awt.event.KeyEvent;

import songscribe.Strings;
import songscribe.ui.component.MainFrame;
import songscribe.ui.dialog.SongSettingsController;
import songscribe.ui.dialog.SongSettingsDialog;

import static songscribe.util.UIUtils.MENU_SHORTCUT_MASK;

/**
 * Opens the Song Settings dialog.
 *
 * <p>Whoever opens the dialog supplies its operations, so the dialog itself never reaches for the
 * score. The dialog is built per opening, so a fresh {@link SongSettingsController} is constructed
 * each time; it still resolves the open document on every reading rather than holding it, which is
 * what keeps a dialog left showing across a document change correct.
 */
public final class SongSettingsOpenAction extends DialogOpenAction<SongSettingsDialog> {

    public static SongSettingsOpenAction createAction(MainFrame mainFrame) {
        return new SongSettingsOpenAction(mainFrame);
    }

    private SongSettingsOpenAction(MainFrame mainFrame) {
        super(
            mainFrame,
            Strings.get(Strings.ACTION_SONG_SETTINGS),
            KeyEvent.VK_G,
            MENU_SHORTCUT_MASK,
            frame -> new SongSettingsDialog(frame, new SongSettingsController(frame).ops()),
            Flag.DISABLE_WHEN_PLAYING
        );
    }
}
