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

import java.awt.*;
import java.awt.font.TextLayout;

import org.jetbrains.annotations.NotNull;

import songscribe.util.GraphicUtils;

/**
 * Component that renders the main under-lyrics section.
 * <p>
 * Displays multi-line lyrics text centered horizontally below the staff lines.
 * Uses the composition's lyrics font.
 */
public class UnderLyricsComponent extends ScoreComponent {

    /** X position for content (used for union width centering). */
    private float contentX = -1;

    /**
     * Creates a new UnderLyricsComponent.
     */
    public UnderLyricsComponent() {
        super();
    }

    /**
     * Sets the X position for content rendering.
     * <p>
     * Used by TextPanel to achieve union width centering across
     * all text components.
     *
     * @param contentX X position, or -1 to center based on own width
     */
    public void setContentX(float contentX) {
        this.contentX = contentX;
    }

    /**
     * Returns the X position for content rendering.
     */
    public float getContentX() {
        return contentX;
    }

    /**
     * Calculates the width of the text content.
     *
     * @param g2 Graphics context
     * @return Text width in pixels
     */
    public double getTextWidth(Graphics2D g2) {
        if (composition == null) {
            return 0;
        }

        var lyrics = composition.getUnderLyrics();

        if (lyrics.isEmpty()) {
            return 0;
        }

        try (var ignored = songscribe.ui.renderer.GraphicsState.save(
            g2,
            songscribe.ui.renderer.GraphicsState.Property.FONT
        )) {
            g2.setFont(composition.getLyricsFont());
            return GraphicUtils.getTextBlockWidth(lyrics, g2);
        }
    }

    @Override
    protected void render(Graphics2D g2) {
        if (composition == null) {
            return;
        }

        var lyrics = composition.getUnderLyrics();

        if (lyrics.isEmpty()) {
            return;
        }

        try (var ignored = songscribe.ui.renderer.GraphicsState.save(
            g2,
            songscribe.ui.renderer.GraphicsState.Property.FONT,
            songscribe.ui.renderer.GraphicsState.Property.COLOR
        )) {
            var font = composition.getLyricsFont();
            g2.setFont(font);
            g2.setColor(Color.BLACK);

            var metrics = g2.getFontMetrics();
            var lineHeight = metrics.getHeight();

            // Use contentX if set (for union width centering), otherwise center based on own width
            float x;

            if (contentX >= 0) {
                x = contentX;
            } else {
                var textWidth = GraphicUtils.getTextBlockWidth(lyrics, g2);
                x = (float) ((composition.getLineWidth() - textWidth) / 2);
            }

            var y = (float) metrics.getAscent();

            // Draw each line
            var lines = lyrics.split("\n");

            for (var line : lines) {
                g2.drawString(line, x, y);
                y += lineHeight;
            }
        }
    }

    @Override
    public Dimension getPreferredSize() {
        if (composition == null) {
            return new Dimension(0, 0);
        }

        var lyrics = composition.getUnderLyrics();

        if (lyrics.isEmpty()) {
            return new Dimension(0, 0);
        }

        var font = composition.getLyricsFont();
        var metrics = getFontMetrics(font);

        var lines = lyrics.split("\n");
        var lineHeight = metrics.getHeight();
        var height = lineHeight * lines.length;

        return new Dimension((int) composition.getLineWidth(), height);
    }
}
