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

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;

import songscribe.Strings;
import songscribe.ui.dialog.PlatformFileDialog;
import songscribe.util.ExtensionFileFilter;

public class ChooseDirectoryAction extends AbstractAction {

    public static final String DIRECTORY_CHANGE_PROPERTY = "directorychange";

    private final PlatformFileDialog pfd;

    public ChooseDirectoryAction(UIConverter uiConverter) {
        putValue(NAME, Strings.get(Strings.ACTION_CONVERTER_CHOOSE));
        pfd = new PlatformFileDialog(
            uiConverter,
            Strings.get(Strings.DIALOG_OPEN_FOLDER_TITLE),
            true,
            new ExtensionFileFilter("Folders"),
            true
        );
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (pfd.showDialog()) {
            firePropertyChange(DIRECTORY_CHANGE_PROPERTY, null, pfd.getFile());
        }
    }
}
