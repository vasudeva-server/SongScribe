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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

import songscribe.dom.Ss;
import songscribe.font.TextMeasurement;
import songscribe.util.GraphicsState;

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
    public static final Ss FOOTNOTES_MIN_MARGIN_TOP_SS = new Ss(5.0);
    /** Maximum width as fraction of line width. */
    private static final double MAX_WIDTH_PERCENTAGE = 2.0 / 3.0;

    /**
     * The top margin in view pixels, recomputed per layout so it tracks the current
     * zoom instead of caching a value frozen at construction.
     */
    @Override
    public int getMarginTop() {
        return toViewPx(FOOTNOTES_MIN_MARGIN_TOP_SS).positionPx();
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

        try (var _ = GraphicsState.save(
            g2,
            GraphicsState.Property.FONT,
            GraphicsState.Property.COLOR
        )) {
            var font = zoomedFont(getFont());
            g2.setFont(font);
            g2.setColor(Color.BLACK);

            var metrics = TextMeasurement.fontMetrics(font);
            var lineHeight = metrics.getHeight();

            // Calculate text width (capped at max width) and derive the centered x position
            var textWidth = textBlockWidth(footnotes, g2);
            var x = calculateRenderX(textWidth);
            var y = (float) (getMarginTop() + metrics.getAscent());

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
     *
     * @param textWidth the footnote block's widest-line advance, in view pixels
     * @return the pen origin in view pixels, to be handed straight to
     *         {@link Graphics2D#drawString(String, float, float)}; the ink of the first
     *         glyph begins at its own left side bearing from there
     */
    private float calculateRenderX(double textWidth) {
        if (song == null) {
            return 0;
        }

        // The text width is measured with a zoom-scaled font, so center against the
        // view-scaled (zoomed) line width.
        // A centering bound, not a size: rounding it to a whole pixel would throw away
        // half a pixel of centering, and drawString takes a float anyway.
        var lineWidthPx = toViewPx(song.getLineWidthSs()).value();
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

        var font = zoomedFont(getFont());
        var metrics = TextMeasurement.fontMetrics(font);

        var lines = footnotes.split("\n");
        var lineHeight = metrics.getHeight();
        var height = getMarginTop() + (lineHeight * lines.length);

        return new Dimension(toViewPx(song.getLineWidthSs()).sizePx(), height);
    }
}
