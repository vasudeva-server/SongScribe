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

import songscribe.data.MyFileFilter;
import songscribe.ui.component.MainFrame;
import songscribe.ui.dialog.PlatformFileDialog;
import songscribe.util.FileUtils;

public class ExportSVGAction extends UIAction {

    private final PlatformFileDialog fileDialog;

    public ExportSVGAction() {
        super("Export as SVG...", "export-svg");
        fileDialog = new PlatformFileDialog(
            MainFrame.getInstance(),
            NAME,
            false,
            new MyFileFilter("Portable Document Format", "svg")
        );
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        var mainFrame = MainFrame.getInstance();

        fileDialog.setFile(
            FileUtils.getSongFileNameForFileChooser(mainFrame.getScore())
        );

        if (fileDialog.showDialog()) {
            var saveFile = fileDialog.getFile();

            if (!saveFile.getName().toLowerCase().endsWith(".svg")) {
                saveFile = new File(saveFile.getAbsolutePath() + ".svg");
            }

            if (saveFile.exists()) {
                var response = JOptionPane.showConfirmDialog(
                    mainFrame,
                    "The file “" +
                    saveFile.getName() +
                    "” already exists. Do you want to overwrite it?",
                    mainFrame.appName,
                    JOptionPane.YES_NO_OPTION
                );

                if (response == JOptionPane.NO_OPTION) {
                    return;
                }
            }

            mainFrame.getScore().createSVG(saveFile, true);
        }
    }
}
