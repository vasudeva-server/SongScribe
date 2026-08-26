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
import songscribe.font.TextMeasurement;
import songscribe.util.GraphicsState;

/**
 * Component that renders translated lyrics with header.
 * <p>
 * Displays a header ("Sri Chinmoy's translation:" or "Unofficial translation:")
 * followed by multi-line translation text, both centered horizontally.
 */
public class TranslationComponent extends ScoreComponent {

    /** Vertical spacing for translation block (2 staff lines). */
    private static final double TRANSLATION_TOP_MARGIN_SS = 2.0;

    /** Translation header for official translations. */
    private static final String TRANSLATION_HEADER_OFFICIAL = "Sri Chinmoy's translation:";

    /** Translation header for unofficial translations. */
    private static final String TRANSLATION_HEADER_UNOFFICIAL = "Unofficial translation:";

    /**
     * The top margin in view pixels, recomputed per layout so it tracks the current zoom.
     */
    @Override
    public int getMarginTop() {
        return toViewPx(new Ss(TRANSLATION_TOP_MARGIN_SS)).roundedPx();
    }

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

        var translation = song.getTranslatedLyrics();

        if (translation.isEmpty()) {
            return 0;
        }

        var lyricsFont = zoomedFont(getFont());
        var headerFont = lyricsFont.deriveFont(Font.BOLD, lyricsFont.getSize2D());

        double maxWidth = 0;

        try (var _ = GraphicsState.save(
            g2,
            GraphicsState.Property.FONT
        )) {
            // Header width
            g2.setFont(headerFont);
            var headerText = song.isUnofficialTranslation()
                ? TRANSLATION_HEADER_UNOFFICIAL
                : TRANSLATION_HEADER_OFFICIAL;
            maxWidth = Math.max(maxWidth, TextMeasurement.textBlockWidth(headerText, g2));

            // Translation text width
            g2.setFont(lyricsFont);
            maxWidth = Math.max(maxWidth, TextMeasurement.textBlockWidth(translation, g2));

            return maxWidth;
        }
    }

    @Override
    protected void render(Graphics2D g2) {
        if (song == null) {
            return;
        }

        var translation = song.getTranslatedLyrics();

        if (translation.isEmpty()) {
            return;
        }

        try (var _ = GraphicsState.save(
            g2,
            GraphicsState.Property.FONT,
            GraphicsState.Property.COLOR
        )) {
            var lyricsFont = zoomedFont(getFont());
            var headerFont = lyricsFont.deriveFont(Font.BOLD, lyricsFont.getSize2D());

            // Draw header
            g2.setFont(headerFont);
            g2.setColor(Color.BLACK);

            var headerText = song.isUnofficialTranslation()
                ? TRANSLATION_HEADER_UNOFFICIAL
                : TRANSLATION_HEADER_OFFICIAL;

            var headerMetrics = g2.getFontMetrics();

            var x = resolveContentX(headerText, g2);

            var y = (float) (getMarginTop() + headerMetrics.getAscent());

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
        if (song == null) {
            return new Dimension(0, 0);
        }

        var translation = song.getTranslatedLyrics();

        if (translation.isEmpty()) {
            return new Dimension(0, 0);
        }

        var lyricsFont = zoomedFont(getFont());
        var headerFont = lyricsFont.deriveFont(Font.BOLD, lyricsFont.getSize2D());

        // Calculate header height
        var headerMetrics = getFontMetrics(headerFont);
        var height = (float) getMarginTop() + headerMetrics.getHeight();

        // Margin below header
        height += lyricsFont.getSize2D() / 4f;

        // Calculate translation text height
        var textMetrics = getFontMetrics(lyricsFont);
        var lines = translation.split("\n");
        height += textMetrics.getHeight() * lines.length;

        return new Dimension(toViewPx(new Ss(song.getLineWidthSs())).roundedPx(), (int) height);
    }
}
