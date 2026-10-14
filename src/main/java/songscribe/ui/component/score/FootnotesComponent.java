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
 */
public class FootnotesComponent extends ScoreComponent {

    public static final Ss FOOTNOTES_MIN_MARGIN_TOP_SS = new Ss(5.0);
    private static final double MAX_WIDTH_PERCENTAGE = 2.0 / 3.0;

    /** Recomputed per layout so it tracks the current zoom. */
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

            var textWidth = textBlockWidth(footnotes, g2);
            var x = calculateRenderX(textWidth);
            var y = (float) (getMarginTop() + metrics.getAscent());

            var lines = footnotes.split("\n");

            for (var line : lines) {
                g2.drawString(line, x, y);
                y += lineHeight;
            }
        }
    }

    /**
     * The text width is capped at {@code MAX_WIDTH_PERCENTAGE} of the line width before
     * centering, so the result is never negative.
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
