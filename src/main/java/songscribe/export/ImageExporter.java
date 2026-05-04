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

import module java.desktop;


import songscribe.ui.component.MyBorder;
import songscribe.ui.component.Score;

import static songscribe.export.ExportOptions.ALL;

/**
 * Utility class for exporting music scores as images.
 */
public final class ImageExporter {

    private ImageExporter() {
        // Utility class - prevent instantiation
    }

    /**
     * Creates a BufferedImage for export with the specified parameters.
     *
     * @param score the score to export
     * @param background the background color
     * @param scale the scale factor
     * @param border the border settings
     * @param options controls which content sections to include
     * @return a new BufferedImage containing the exported score
     */
    public static BufferedImage createImageForExport(
        Score score,
        Color background,
        double scale,
        MyBorder border,
        ExportOptions options
    ) {
        var image = new BufferedImage(
            (int) ((score.getSheetWidthPx()) * scale) + border.getWidth(),
            (int) ((score.getSheetHeightPx(options)) * scale) + border.getHeight(),
            BufferedImage.TYPE_BYTE_GRAY
        );
        createImageForExport(image, background, scale, border, options);
        return image;
    }

    /**
     * Renders the score into an existing BufferedImage.
     *
     * @param image the image to render into
     * @param background the background color
     * @param scale the scale factor
     * @param border the border settings
     * @param options controls which content sections to include
     */
    public static void createImageForExport(
        BufferedImage image,
        Color background,
        double scale,
        MyBorder border,
        ExportOptions options
    ) {
        // TODO: When component-based rendering is implemented, use options to
        // skip excluded sections (lyrics, title, attribution) without mutating Song
        var g2 = image.createGraphics();
        g2.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON
        );
        g2.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        );
        g2.setColor(background);
        g2.fillRect(0, 0, image.getWidth(), image.getHeight());
        g2.setColor(Color.BLACK);
        g2.drawString("Image export not yet implemented", 50, 50);
        g2.dispose();
    }
}
