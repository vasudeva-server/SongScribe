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

import songscribe.ui.layout.ScaleContext;
import songscribe.util.GraphicUtils;

/**
 * Component that renders Bengali (Bangla) lyrics.
 * <p>
 * Displays multi-line Bangla text centered horizontally below the main lyrics.
 * Uses the composition's Bangla font with appropriate spacing.
 */
public class BanglaLyricsComponent extends ScoreComponent {

    /** Vertical spacing for Bangla lyrics (2 staff lines). */
    private static final int BANGLA_LYRICS_TOP_MARGIN = ScaleContext.getInstance().toRoundedPixels(2.0);

    /** X position for content (used for union width centering). */
    private float contentX = -1;

    /**
     * Creates a new BanglaLyricsComponent.
     */
    public BanglaLyricsComponent() {
        super();
        setMarginTop(BANGLA_LYRICS_TOP_MARGIN);
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

        var banglaLyrics = composition.getBanglaLyrics();

        if (banglaLyrics.isEmpty()) {
            return 0;
        }

        try (var ignored = songscribe.ui.renderer.GraphicsState.save(
            g2,
            songscribe.ui.renderer.GraphicsState.Property.FONT
        )) {
            g2.setFont(composition.getBanglaFont());
            return GraphicUtils.getTextBlockWidth(banglaLyrics, g2);
        }
    }

    @Override
    protected void render(Graphics2D g2) {
        if (composition == null) {
            return;
        }

        var banglaLyrics = composition.getBanglaLyrics();

        if (banglaLyrics.isEmpty()) {
            return;
        }

        try (var ignored = songscribe.ui.renderer.GraphicsState.save(
            g2,
            songscribe.ui.renderer.GraphicsState.Property.FONT,
            songscribe.ui.renderer.GraphicsState.Property.COLOR
        )) {
            var font = composition.getBanglaFont();
            g2.setFont(font);
            g2.setColor(Color.BLACK);

            var metrics = g2.getFontMetrics();
            var lineHeight = metrics.getHeight();

            // Use contentX if set (for union width centering), otherwise center based on own width
            float x;

            if (contentX >= 0) {
                x = contentX;
            } else {
                var textWidth = GraphicUtils.getTextBlockWidth(banglaLyrics, g2);
                x = (float) ((composition.getLineWidthPx() - textWidth) / 2);
            }

            var y = (float) (marginTop + metrics.getAscent());

            // Draw each line
            var lines = banglaLyrics.split("\n");

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

        var banglaLyrics = composition.getBanglaLyrics();

        if (banglaLyrics.isEmpty()) {
            return new Dimension(0, 0);
        }

        var font = composition.getBanglaFont();
        var metrics = getFontMetrics(font);

        var lines = banglaLyrics.split("\n");
        var lineHeight = metrics.getHeight();
        var height = lineHeight * lines.length;

        return new Dimension(composition.getLineWidthPx(), height + marginTop);
    }
}
