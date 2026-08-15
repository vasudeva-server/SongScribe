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

import java.awt.event.ActionEvent;
import java.nio.file.Files;
import java.nio.file.Path;

import songscribe.Strings;
import songscribe.message.MessageCenter;
import songscribe.message.command.OpenFileCommand;
import songscribe.prefs.RecentDocumentsManager;
import songscribe.ui.OptionDialogs;
import songscribe.ui.component.MainFrame;

public class OpenRecentAction extends UIAction {

    private final Path path;

    @SuppressWarnings("ParameterNameDiffersFromOverriddenParameter")
    public OpenRecentAction(MainFrame mainFrame, String label, Path path) {
        super(mainFrame, label, label, Flag.DISABLE_WHEN_PLAYING, Flag.DISABLE_IN_GRACE_MODE);
        this.path = path;
    }

    @Override
    protected void performAction(ActionEvent e) {
        if (!Files.exists(path)) {
            OptionDialogs.showErrorMessage(
                getMainFrame(),
                Strings.ALERT_TITLE_FILE_ERROR,
                Strings.ERROR_FILE_NOT_FOUND,
                path.getFileName()
            );
            RecentDocumentsManager.remove(path);
            return;
        }

        MessageCenter.post(new OpenFileCommand(path.toFile()));
    }
}
