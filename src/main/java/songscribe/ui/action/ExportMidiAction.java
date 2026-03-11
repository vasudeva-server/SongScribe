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

import java.awt.event.*;

import songscribe.Strings;
import songscribe.data.MyFileFilter;
import songscribe.ui.component.MainFrame;
import songscribe.ui.dialog.ExportMidiDialog;
import songscribe.ui.dialog.PlatformFileDialog;
import songscribe.util.FileUtils;

public class ExportMidiAction extends UIAction {

    private final PlatformFileDialog fileDialog;
    private ExportMidiDialog exportMidiDialog = null;

    public ExportMidiAction() {
        super(Strings.get(Strings.ACTION_EXPORT_MIDI), "export-midi");
        fileDialog = new PlatformFileDialog(
            MainFrame.getInstance(),
            "Export MIDI",
            false,
            new MyFileFilter(Strings.get(Strings.FILTER_MIDI), "mid")
        );
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        var saveFile = FileUtils.showExportDialog(fileDialog, "mid");

        if (saveFile == null) {
            return;
        }

        if (exportMidiDialog == null) {
            exportMidiDialog = new ExportMidiDialog();
        }

        exportMidiDialog.setSaveFile(saveFile);
        exportMidiDialog.setVisible(true);
    }
}
