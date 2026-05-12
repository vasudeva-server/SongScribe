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

import module java.desktop;

import java.io.File;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.export.PageLayoutData;
import songscribe.export.PDFExporter;
import songscribe.ui.dialog.ExportPDFDialog;
import songscribe.ui.dialog.PlatformFileDialog;

public final class ExportPDFAction extends UIAction {

    private @Nullable ExportPDFDialog exportPDFDialog = null;

    public static ExportPDFAction createAction() {
        return new ExportPDFAction();
    }

    private ExportPDFAction() {
        super(Strings.get(Strings.ACTION_EXPORT_PDF), "export-pdf");
        setFlags(Flag.OPENS_DIALOG);
    }

    public static void createPDF(
        PageLayoutData data,
        File outputFile
    ) {
        PDFExporter.createPDF(data, outputFile);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        var score = requireScore();
        var saveFile = PlatformFileDialog.showSaveDialog(
            getMainFrame(),
            "Export PDF",
            Strings.get(Strings.FILTER_PDF),
            score.getSuggestedFileName(),
            "pdf"
        );

        if (saveFile == null) {
            return;
        }

        if (exportPDFDialog == null) {
            exportPDFDialog = new ExportPDFDialog();
        }

        exportPDFDialog.setVisible(true);
        var data = exportPDFDialog.getPaperSizeData();

        if (data == null) {
            return;
        }

        createPDF(data, saveFile);
    }
}
