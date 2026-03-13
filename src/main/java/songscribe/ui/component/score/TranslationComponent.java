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

import songscribe.ui.component.Score;
import songscribe.ui.layout2.ScaleContext;
import songscribe.util.GraphicUtils;

/**
 * Component that renders translated lyrics with header.
 * <p>
 * Displays a header ("Sri Chinmoy's translation:" or "Unofficial translation:")
 * followed by multi-line translation text, both centered horizontally.
 */
public class TranslationComponent extends ScoreComponent {

    /** Vertical spacing for translation block (2 staff lines). */
    private static final int TRANSLATION_TOP_MARGIN = ScaleContext.getInstance().toRoundedPixels(2.0);

    /** Translation header for official translations. */
    private static final String TRANSLATION_HEADER_OFFICIAL = "Sri Chinmoy's translation:";

    /** Translation header for unofficial translations. */
    private static final String TRANSLATION_HEADER_UNOFFICIAL = "Unofficial translation:";

    /** X position for content (used for union width centering). */
    private float contentX = -1;

    /**
     * Creates a new TranslationComponent.
     */
    public TranslationComponent() {
        super();
        setMarginTop(TRANSLATION_TOP_MARGIN);
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

        var translation = composition.getTranslatedLyrics();

        if (translation.isEmpty()) {
            return 0;
        }

        var lyricsFont = composition.getLyricsFont();
        var headerFont = lyricsFont.deriveFont(Font.BOLD, lyricsFont.getSize2D());

        double maxWidth = 0;

        try (var ignored = songscribe.ui.renderer.GraphicsState.save(
            g2,
            songscribe.ui.renderer.GraphicsState.Property.FONT
        )) {
            // Header width
            g2.setFont(headerFont);
            var headerText = composition.isUnofficialTranslation()
                ? TRANSLATION_HEADER_UNOFFICIAL
                : TRANSLATION_HEADER_OFFICIAL;
            maxWidth = Math.max(maxWidth, GraphicUtils.getTextBlockWidth(headerText, g2));

            // Translation text width
            g2.setFont(lyricsFont);
            maxWidth = Math.max(maxWidth, GraphicUtils.getTextBlockWidth(translation, g2));

            return maxWidth;
        }
    }

    @Override
    protected void render(Graphics2D g2) {
        if (composition == null) {
            return;
        }

        var translation = composition.getTranslatedLyrics();

        if (translation.isEmpty()) {
            return;
        }

        try (var ignored = songscribe.ui.renderer.GraphicsState.save(
            g2,
            songscribe.ui.renderer.GraphicsState.Property.FONT,
            songscribe.ui.renderer.GraphicsState.Property.COLOR
        )) {
            var lyricsFont = composition.getLyricsFont();
            var headerFont = lyricsFont.deriveFont(Font.BOLD, lyricsFont.getSize2D());

            // Draw header
            g2.setFont(headerFont);
            g2.setColor(Color.BLACK);

            var headerText = composition.isUnofficialTranslation()
                ? TRANSLATION_HEADER_UNOFFICIAL
                : TRANSLATION_HEADER_OFFICIAL;

            var headerMetrics = g2.getFontMetrics();

            // Use contentX if set (for union width centering), otherwise center based on own width
            float x;

            if (contentX >= 0) {
                x = contentX;
            } else {
                var headerWidth = GraphicUtils.getTextBlockWidth(headerText, g2);
                x = (float) ((composition.getLineWidth() - headerWidth) / 2);
            }

            var y = (float) (marginTop + headerMetrics.getAscent());

            g2.drawString(headerText, x, y);
            y += headerMetrics.getHeight();

            // Margin below header
            y += lyricsFont.getSize2D() / 4f;

            // Draw translation text
            g2.setFont(lyricsFont);

            var lineHeight = g2.getFontMetrics().getHeight();
            var lines = translation.split("\n");

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

        var translation = composition.getTranslatedLyrics();

        if (translation.isEmpty()) {
            return new Dimension(0, 0);
        }

        var lyricsFont = composition.getLyricsFont();
        var headerFont = lyricsFont.deriveFont(Font.BOLD, lyricsFont.getSize2D());

        // Calculate header height
        var headerMetrics = getFontMetrics(headerFont);
        var height = marginTop + headerMetrics.getHeight();

        // Margin below header
        height += lyricsFont.getSize2D() / 4f;

        // Calculate translation text height
        var textMetrics = getFontMetrics(lyricsFont);
        var lines = translation.split("\n");
        height += textMetrics.getHeight() * lines.length;

        return new Dimension((int) composition.getLineWidth(), (int) height);
    }
}
