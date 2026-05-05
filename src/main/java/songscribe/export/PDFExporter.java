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

import songscribe.Strings;
import songscribe.ui.OptionDialogs;
import songscribe.util.GraphicUtils;

/**
 * Utility class for exporting music scores as PDF files.
 */
public final class PDFExporter {

    private PDFExporter() {
        // Utility class - prevent instantiation
    }

    /**
     * Exports the score to a PDF file.
     *
     * @param data the page layout data containing margins and paper size
     * @param outputFile the file to write the PDF to
     */
    public static void createPDF(
        PageLayoutData data,
        File outputFile
    ) {
        var resolution = 72f / GraphicUtils.getDpi();
        var paperWidth = data.paperWidthPx * resolution;
        var paperHeight = data.paperHeightPx * resolution;
        var score = data.score;

        if (score == null) {
            return;
        }

        var song = score.getSong();

        // Scale to fit
        var sheetWidth = score.getSheetWidthPx();
        var sheetHeight = score.getSheetHeightPx();
        var horizontalMargin =
            (data.leftInnerMarginPx + data.rightOuterMarginPx) * resolution;
        var horizontalScale = (paperWidth - horizontalMargin) / sheetWidth;
        var verticalMargin = (data.topMarginPx + data.bottomMarginPx) * resolution;
        var verticalScale = (paperHeight - verticalMargin) / sheetHeight;
        var scale = (horizontalScale < verticalScale) ? horizontalScale : verticalScale;
        var leftMargin = (double) data.leftInnerMarginPx * resolution;

        if (horizontalScale >= verticalScale) {
            // If scaling vertically, the horizontal margin will be larger than
            // what is specified in Data. So we calculate the total margin available,
            // then give the left margin the same fraction of the total margin
            // it would have had before scaling.
            var scaledMargin = paperWidth - (sheetWidth * scale);
            var leftMarginFactor =
                (double) data.leftInnerMarginPx /
                    (double) (data.leftInnerMarginPx + data.rightOuterMarginPx);
            leftMargin = scaledMargin * leftMarginFactor;
        }

        // PDF export not yet implemented with component-based rendering
        OptionDialogs.showErrorMessage(
            null,
            Strings.ALERT_TITLE_EXPORT_ERROR,
            Strings.ERROR_EXPORT_NOT_IMPLEMENTED, "PDF"
        );
    }
}
