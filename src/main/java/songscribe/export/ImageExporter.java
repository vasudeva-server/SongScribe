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

import org.jetbrains.annotations.NotNull;

import songscribe.ui.component.MyBorder;
import songscribe.ui.component.Score;

/**
 * Utility class for exporting music scores as images.
 */
public class ImageExporter {

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
     * @return a new BufferedImage containing the exported score
     */
    @NotNull
    public static BufferedImage createImageForExport(
        @NotNull Score score,
        Color background,
        double scale,
        @NotNull MyBorder border
    ) {
        var image = new BufferedImage(
            (int) ((score.getSheetWidthPx()) * scale) + border.getWidth(),
            (int) ((score.getSheetHeightPx()) * scale) + border.getHeight(),
            BufferedImage.TYPE_BYTE_GRAY
        );
        createImageForExport(image, background, scale, border);
        return image;
    }

    /**
     * Renders the score into an existing BufferedImage.
     *
     * @param image the image to render into
     * @param background the background color
     * @param scale the scale factor
     * @param border the border settings
     */
    public static void createImageForExport(
        @NotNull BufferedImage image,
        Color background,
        double scale,
        @NotNull MyBorder border
    ) {
        // Image export not yet implemented with component-based rendering
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
