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
package songscribe.export;

import module java.desktop;

import java.io.File;

import songscribe.Strings;
import songscribe.ui.OptionDialogs;
import songscribe.util.DesktopUtils;
import songscribe.util.Utils;

public final class ExportUtils {

    private ExportUtils() {}

    public static void openExportedFile(File file) {
        if (!DesktopUtils.isDesktopSupported()) {
            return;
        }

        var answer = OptionDialogs.showConfirmDialog(
            null,
            Strings.ALERT_TITLE_FILE_ERROR,
            Strings.CONFIRM_FILE_OPEN,
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );

        if (answer == JOptionPane.YES_OPTION) {
            Utils.withDesktop(
                desktop -> desktop.open(file),
                Strings.ALERT_TITLE_FILE_ERROR,
                Strings.ERROR_FILE_OPEN
            );
        }
    }
}
