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
package songscribe.export;

import java.io.File;

import songscribe.data.PageLayoutData;
import songscribe.util.GraphicUtils;

/**
 * Utility class for exporting music scores as PDF files.
 */
public class PDFExporter {

    private PDFExporter() {
        // Utility class - prevent instantiation
    }

    /**
     * Exports the score to a PDF file.
     *
     * @param data the page layout data containing margins and paper size
     * @param outputFile the file to write the PDF to
     * @param isGUI whether this export is being run from the GUI (affects error reporting)
     */
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
        var sheetWidth = score.getSheetWidthPx();
        var sheetHeight = score.getSheetHeightPx();
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

        // PDF export not yet implemented with component-based rendering
        if (isGUI) {
            mainFrame.showErrorMessage(
                "PDF export is not yet implemented. " +
                    "Export functionality will be restored in a future update."
            );
        } else {
            System.err.println("ERROR: PDF export is not yet implemented");
        }
    }
}
