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
import java.io.File;
import javax.swing.*;
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

    // TODO: This method can probably be refactored among all of the save/export actions
    @Override
    public void actionPerformed(ActionEvent e) {
        var mainFrame = MainFrame.getInstance();

        fileDialog.setFile(
            FileUtils.getSongFileNameForFileChooser(mainFrame.getScore())
        );

        if (fileDialog.showDialog()) {
            var saveFile = fileDialog.getFile();

            if (!saveFile.getName().toLowerCase().endsWith(".mid")) {
                saveFile = new File(saveFile.getAbsolutePath() + ".mid");
            }

            if (saveFile.exists()) {
                var response = JOptionPane.showConfirmDialog(
                    mainFrame,
                    Strings.get(Strings.CONFIRM_FILE_OVERWRITE, saveFile.getName()),
                    mainFrame.appName,
                    JOptionPane.YES_NO_OPTION
                );

                if (response == JOptionPane.NO_OPTION) {
                    return;
                }
            }

            if (exportMidiDialog == null) {
                exportMidiDialog = new ExportMidiDialog();
            }

            exportMidiDialog.setSaveFile(saveFile);
            exportMidiDialog.setVisible(true);
        }
    }
}
