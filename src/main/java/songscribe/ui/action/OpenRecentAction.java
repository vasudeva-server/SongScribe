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

import java.nio.file.Files;
import java.nio.file.Path;

import songscribe.Strings;
import songscribe.prefs.RecentDocumentsManager;
import songscribe.ui.Dialogs;
import songscribe.message.MessageCenter;
import songscribe.message.OpenFileMessage;

public class OpenRecentAction extends UIAction {

    private final Path path;

    public OpenRecentAction(String label, Path path) {
        super(label, label);
        setFlags(
            Flag.DISABLE_WHEN_PLAYING,
            Flag.DISABLE_IN_GRACE_MODE
        );
        this.path = path;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!Files.exists(path)) {
            Dialogs.showErrorMessage(
                getMainFrame(),
                Strings.get(Strings.DIALOG_TITLE_FILE_ERROR),
                "The file \u201c" + path.getFileName() + "\u201d could not be opened because it no longer exists."
            );
            RecentDocumentsManager.getInstance().remove(path);
            return;
        }

        MessageCenter.post(new OpenFileMessage(path.toFile()));
    }
}
