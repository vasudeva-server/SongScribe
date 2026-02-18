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
import java.awt.geom.Rectangle2D;
import java.io.File;

import javax.swing.*;

import org.jfree.pdf.PDFDocument;

import songscribe.data.MyFileFilter;
import songscribe.data.PageLayoutData;
import songscribe.ui.Constants;
import songscribe.ui.component.MainFrame;
import songscribe.ui.dialog.ExportPDFDialog;
import songscribe.ui.dialog.PlatformFileDialog;
import songscribe.util.FileUtils;
import songscribe.util.GraphicUtils;

public class ExportPDFAction extends UIAction {

    private final PlatformFileDialog fileDialog;
    private ExportPDFDialog exportPDFDialog = null;

    public ExportPDFAction() {
        super("Export as PDF...", "export-pdf");
        fileDialog = new PlatformFileDialog(
            MainFrame.getInstance(),
            "Export PDF",
            false,
            new MyFileFilter("Portable Document Format", "pdf")
        );
    }

    public static void createPDF(
        PageLayoutData data,
        File outputFile,
        Boolean isGUI
    ) {
        var resolution = 72f / GraphicUtils.getDpi();
        var paperWidth = data.paperWidth * resolution;
        var paperHeight = data.paperHeight * resolution;
        var mainFrame = data.mainFrame;
        var score = mainFrame.getScore();
        var composition = score.getComposition();

        // Scale to fit
        var sheetWidth = score.getSheetWidth();
        var sheetHeight = score.getSheetHeight();
        var horizontalMargin =
            (data.leftInnerMargin + data.rightOuterMargin) * resolution;
        var horizontalScale = (paperWidth - horizontalMargin) / sheetWidth;
        var verticalMargin = (data.topMargin + data.bottomMargin) * resolution;
        var verticalScale = (paperHeight - verticalMargin) / sheetHeight;
        double scale;
        double leftMargin = data.leftInnerMargin * resolution;

        if (horizontalScale < verticalScale) {
            scale = horizontalScale;
        } else {
            // If scaling vertically, the horizontal margin will be larger than
            // what is specified in Data. So we calculate the total margin available,
            // then give the left margin the same fraction of the total margin
            // it would have had before scaling.
            scale = verticalScale;
            var scaledMargin = paperWidth - (sheetWidth * scale);
            var leftMarginFactor =
                (double) data.leftInnerMargin /
                (double) (data.leftInnerMargin + data.rightOuterMargin);
            leftMargin = scaledMargin * leftMarginFactor;
        }

        var pdfDoc = new PDFDocument();
        pdfDoc.setTitle(composition.getTitle());
        pdfDoc.setAuthor(Constants.PACKAGE_NAME);

        var page = pdfDoc.createPage(
            new Rectangle2D.Double(0, 0, paperWidth, paperHeight)
        );
        var g2 = page.getGraphics2D();
        g2.translate(leftMargin, data.topMargin * resolution);
        score.getScoreRenderer().render(g2, false, scale);

        try {
            pdfDoc.writeToFile(outputFile);

            if (isGUI) {
                FileUtils.openExportFile(mainFrame, outputFile);
            }
        } catch (RuntimeException e) {
            if (isGUI) {
                mainFrame.showErrorMessage(MainFrame.COULD_NOT_SAVE_MESSAGE);
            }
        }
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

        if (exportPDFDialog == null) {
            exportPDFDialog = new ExportPDFDialog(mainFrame);
        }

        exportPDFDialog.setVisible(true);
        var data = exportPDFDialog.getPaperSizeData();

        if (data == null) {
            return;
        }

        createPDF(data, saveFile, true);
    }
}
