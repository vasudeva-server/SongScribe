/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.ui.component.score;

import module java.desktop;

import songscribe.dom.ScaleContext;
import songscribe.util.GraphicsState;
import songscribe.util.GraphicUtils;

/**
 * Component that renders footnotes.
 * <p>
 * Displays footnote text centered horizontally at a maximum width of 2/3 the line width.
 * Uses the song's footnote font with appropriate top margin.
 */
public class FootnotesComponent extends ScoreComponent {

    /**
     * Minimum margin above footnotes section
     */
    public static final double FOOTNOTES_MIN_MARGIN_TOP_SS = 5.0;  // 40px
    /** Maximum width as fraction of line width. */
    private static final double MAX_WIDTH_PERCENTAGE = 2.0 / 3.0;

    /**
     * Creates a new FootnotesComponent.
     */
    public FootnotesComponent() {
        setMarginTop(ScaleContext.ssToRoundedPx(FOOTNOTES_MIN_MARGIN_TOP_SS));
    }

    @Override
    protected void render(Graphics2D g2) {
        if (song == null) {
            return;
        }

        var footnotes = song.getFootnotes();

        if (footnotes.isEmpty()) {
            return;
        }

        try (var ignored = GraphicsState.save(
            g2,
            GraphicsState.Property.FONT,
            GraphicsState.Property.COLOR
        )) {
            var font = getFont();
            g2.setFont(font);
            g2.setColor(Color.BLACK);

            var metrics = g2.getFontMetrics();
            var lineHeight = metrics.getHeight();

            // Calculate text width (capped at max width) and derive the centered x position
            var textWidth = GraphicUtils.getTextBlockWidth(footnotes, g2);
            var x = calculateRenderX(textWidth);
            var y = (float) (marginTop + metrics.getAscent());

            // Draw each line
            var lines = footnotes.split("\n");

            for (var line : lines) {
                g2.drawString(line, x, y);
                y += lineHeight;
            }
        }
    }

    /**
     * Computes the horizontal render origin for footnote text given its measured pixel width.
     * <p>
     * The text width is capped at {@code MAX_WIDTH_PERCENTAGE} of the line width before
     * centering, so the returned value is always non-negative.
     * <p>
     * Package-private for testing.
     *
     * @param textWidth measured pixel width of the footnote block
     * @return x coordinate (in pixels) of the left edge of the first drawn character
     */
    float calculateRenderX(double textWidth) {
        if (song == null) {
            return 0;
        }

        var lineWidthPx = song.getLineWidthPx();
        var maxWidth = lineWidthPx * MAX_WIDTH_PERCENTAGE;
        var actualWidth = Math.min(textWidth, maxWidth);
        return (float) ((lineWidthPx - actualWidth) / 2);
    }

    @Override
    public Dimension getPreferredSize() {
        if (song == null) {
            return new Dimension(0, 0);
        }

        var footnotes = song.getFootnotes();

        if (footnotes.isEmpty()) {
            return new Dimension(0, 0);
        }

        var font = getFont();
        var metrics = getFontMetrics(font);

        var lines = footnotes.split("\n");
        var lineHeight = metrics.getHeight();
        var height = marginTop + (lineHeight * lines.length);

        return new Dimension(song.getLineWidthPx(), height);
    }
}
