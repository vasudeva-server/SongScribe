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
package songscribe.uiconverter;

import module java.desktop;

import songscribe.Strings;
import songscribe.file.MyFileFilter;
import songscribe.ui.dialog.PlatformFileDialog;

public class ChooseDirectoryAction extends AbstractAction {

    private final PlatformFileDialog pfd;

    public ChooseDirectoryAction(UIConverter uiConverter) {
        putValue(NAME, Strings.get(Strings.ACTION_CONVERTER_CHOOSE));
        pfd = new PlatformFileDialog(
            uiConverter,
            Strings.get(Strings.DIALOG_OPEN_FOLDER_TITLE),
            true,
            new MyFileFilter("Folders"),
            true
        );
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (pfd.showDialog()) {
            firePropertyChange("directorychange", null, pfd.getFile());
        }
    }
}
