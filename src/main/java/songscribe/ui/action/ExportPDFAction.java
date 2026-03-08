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
import songscribe.data.PageLayoutData;
import songscribe.export.PDFExporter;
import songscribe.ui.component.MainFrame;
import songscribe.ui.dialog.ExportPDFDialog;
import songscribe.ui.dialog.PlatformFileDialog;
import songscribe.util.FileUtils;

public class ExportPDFAction extends UIAction {

    private final PlatformFileDialog fileDialog;
    private ExportPDFDialog exportPDFDialog = null;

    public ExportPDFAction() {
        super(Strings.get(Strings.ACTION_EXPORT_PDF), "export-pdf");
        fileDialog = new PlatformFileDialog(
            MainFrame.getInstance(),
            "Export PDF",
            false,
            new MyFileFilter(Strings.get(Strings.FILTER_PDF), "pdf")
        );
    }

    public static void createPDF(
        PageLayoutData data,
        File outputFile,
        Boolean isGUI
    ) {
        PDFExporter.createPDF(data, outputFile, isGUI);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // TODO: This code can be shared with other export actions.
        var mainFrame = MainFrame.getInstance();
        fileDialog.setFile(
            FileUtils.getSongFileNameForFileChooser(mainFrame.getScore())
        );

        if (!fileDialog.showDialog()) {
            return;
        }

        var saveFile = fileDialog.getFile();

        if (!saveFile.getName().toLowerCase().endsWith(".pdf")) {
            saveFile = new File(saveFile.getAbsolutePath() + ".pdf");
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

        if (exportPDFDialog == null) {
            exportPDFDialog = new ExportPDFDialog();
        }

        exportPDFDialog.setVisible(true);
        var data = exportPDFDialog.getPaperSizeData();

        if (data == null) {
            return;
        }

        createPDF(data, saveFile, true);
    }
}
