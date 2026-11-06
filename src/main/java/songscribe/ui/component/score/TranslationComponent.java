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
import java.awt.geom.Point2D;

import songscribe.dom.Song;
import songscribe.dom.Ss;
import songscribe.dom.ViewPx;
import songscribe.font.TextMeasurement;
import songscribe.ui.component.ScoreView;
import songscribe.util.GraphicsState;

/**
 * Component that renders translated lyrics with header.
 */
public class TranslationComponent extends ScoreComponent {

    private static final Ss TRANSLATION_TOP_MARGIN_SS = new Ss(2.0);

    private static final String TRANSLATION_HEADER_OFFICIAL = "Sri Chinmoy's translation:";

    private static final String TRANSLATION_HEADER_UNOFFICIAL = "Unofficial translation:";

    private static final float HEADER_GAP_FONT_SIZE_DIVISOR = 4f;

    public TranslationComponent(ScoreView scoreView) {
        super(scoreView);
    }

    /** Recomputed per layout so it tracks the current zoom. */
    @Override
    public int getMarginTop() {
        return toViewPx(TRANSLATION_TOP_MARGIN_SS).positionPx();
    }

    public double getTextWidth(Graphics2D g2) {
        var song = getSong();
        var translation = song.getTranslatedLyrics();

        if (translation.isEmpty()) {
            return 0;
        }

        var lyricsFont = zoomedFont(getFont());

        double maxWidth = 0;

        try (var _ = GraphicsState.save(
            g2,
            GraphicsState.Property.FONT
        )) {
            g2.setFont(headerFont(lyricsFont));
            maxWidth = Math.max(maxWidth, textBlockWidth(headerText(song), g2));

            g2.setFont(lyricsFont);
            maxWidth = Math.max(maxWidth, textBlockWidth(translation, g2));

            return maxWidth;
        }
    }

    @Override
    protected void render(Graphics2D g2) {
        var song = getSong();
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

            var headerFont = headerFont(lyricsFont);
            g2.setFont(headerFont);
            g2.setColor(Color.BLACK);

            var headerText = headerText(song);
            var headerMetrics = TextMeasurement.fontMetrics(headerFont);

            var x = resolveContentX(headerText, g2);
            var y = (float) (getMarginTop() + headerMetrics.getAscent());

            g2.drawString(headerText, x, y);
            y += headerMetrics.getHeight();
            y += headerGap(lyricsFont);

            g2.setFont(lyricsFont);

            drawTextLines(translation, new Point2D.Float(x, y), g2);
        }
    }

    @Override
    public Dimension getPreferredSize() {
        var song = getSong();
        var translation = song.getTranslatedLyrics();

        if (translation.isEmpty()) {
            return new Dimension(0, 0);
        }

        var lyricsFont = zoomedFont(getFont());

        var headerMetrics = TextMeasurement.fontMetrics(headerFont(lyricsFont));
        var height = (float) getMarginTop() + headerMetrics.getHeight();
        height += headerGap(lyricsFont);
        height += textLinesHeight(translation, lyricsFont);

        return new Dimension(
            toViewPx(song.getLineWidthSs()).sizePx(),
            new ViewPx(height).sizePx());
    }

    private static Font headerFont(Font lyricsFont) {
        return lyricsFont.deriveFont(Font.BOLD, lyricsFont.getSize2D());
    }

    private static String headerText(Song song) {
        return song.isUnofficialTranslation()
            ? TRANSLATION_HEADER_UNOFFICIAL
            : TRANSLATION_HEADER_OFFICIAL;
    }

    private static float headerGap(Font lyricsFont) {
        return lyricsFont.getSize2D() / HEADER_GAP_FONT_SIZE_DIVISOR;
    }
}
