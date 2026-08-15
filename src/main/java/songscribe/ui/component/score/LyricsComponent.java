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
import java.awt.Font;
import java.awt.Graphics2D;

import songscribe.dom.Ss;
import songscribe.util.GraphicUtils;
import songscribe.util.GraphicsState;

/**
 * Abstract base for lyrics components that render centered multi-line text.
 * <p>
 * Subclasses supply the text and font via {@link #getLyrics()} and {@link #getFont()}.
 */
public abstract class LyricsComponent extends ScoreComponent {

    /**
     * Returns the lyrics text to render.
     */
    protected abstract String getLyrics();

    /**
     * Returns the font to use when rendering.
     */
    protected abstract Font getLyricsFont();

    /**
     * Calculates the width of the text content.
     *
     * @param g2 Graphics context
     * @return Text width in pixels
     */
    public double getTextWidth(Graphics2D g2) {
        if (song == null) {
            return 0;
        }

        var lyrics = getLyrics();

        if (lyrics.isEmpty()) {
            return 0;
        }

        try (var _ = GraphicsState.save(g2, GraphicsState.Property.FONT)) {
            g2.setFont(zoomedFont(getLyricsFont()));
            return GraphicUtils.getTextBlockWidth(lyrics, g2);
        }
    }

    @Override
    protected void render(Graphics2D g2) {
        if (song == null) {
            return;
        }

        var lyrics = getLyrics();

        if (lyrics.isEmpty()) {
            return;
        }

        try (var _ = GraphicsState.save(
            g2,
            GraphicsState.Property.FONT,
            GraphicsState.Property.COLOR
        )) {
            g2.setFont(zoomedFont(getLyricsFont()));
            g2.setColor(Color.BLACK);

            var metrics = g2.getFontMetrics();
            var lineHeight = metrics.getHeight();

            var x = resolveContentX(lyrics, g2);

            var y = (float) (getMarginTop() + metrics.getAscent());

            var lines = lyrics.split("\n");

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

        var lyrics = getLyrics();

        if (lyrics.isEmpty()) {
            return new Dimension(0, 0);
        }

        var font = zoomedFont(getLyricsFont());
        var metrics = getFontMetrics(font);

        var lines = lyrics.split("\n");
        var lineHeight = metrics.getHeight();
        var height = lineHeight * lines.length;

        return new Dimension(
            toViewPx(new Ss(song.getLineWidthSs())).roundedPx(), height + getMarginTop());
    }
}
