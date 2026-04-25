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

import songscribe.ui.layout.LayoutStylesheet;
import songscribe.ui.layout.ScaleContext;
import songscribe.util.GraphicUtils;

/**
 * Component that renders footnotes.
 * <p>
 * Displays footnote text centered horizontally at a maximum width of 2/3 the line width.
 * Uses the song's footnote font with appropriate top margin.
 */
public class FootnotesComponent extends ScoreComponent {

    /** Maximum width as fraction of line width. */
    private static final double MAX_WIDTH_PERCENTAGE = 2.0 / 3.0;

    /**
     * Creates a new FootnotesComponent.
     */
    public FootnotesComponent() {
        super();
        setMarginTop(ScaleContext.getInstance().toRoundedPixels(LayoutStylesheet.FOOTNOTES_MIN_MARGIN_TOP_SS));
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

        try (var ignored = songscribe.ui.renderer.GraphicsState.save(
            g2,
            songscribe.ui.renderer.GraphicsState.Property.FONT,
            songscribe.ui.renderer.GraphicsState.Property.COLOR
        )) {
            var font = song.getFootnoteFont();
            g2.setFont(font);
            g2.setColor(Color.BLACK);

            var metrics = g2.getFontMetrics();
            var lineHeight = metrics.getHeight();

            // Calculate text width (capped at max width)
            var textWidth = GraphicUtils.getTextBlockWidth(footnotes, g2);
            var maxWidth = song.getLineWidthPx() * MAX_WIDTH_PERCENTAGE;
            var actualWidth = Math.min(textWidth, maxWidth);

            // Center horizontally
            var x = (float) ((song.getLineWidthPx() - actualWidth) / 2);
            var y = (float) (marginTop + metrics.getAscent());

            // Draw each line
            var lines = footnotes.split("\n");

            for (var line : lines) {
                g2.drawString(line, x, y);
                y += lineHeight;
            }
        }
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

        var font = song.getFootnoteFont();
        var metrics = getFontMetrics(font);

        var lines = footnotes.split("\n");
        var lineHeight = metrics.getHeight();
        var height = marginTop + (lineHeight * lines.length);

        return new Dimension(song.getLineWidthPx(), height);
    }
}
