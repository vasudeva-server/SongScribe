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

package songscribe.layout;

import java.awt.Font;
import java.awt.geom.Rectangle2D;
import java.text.BreakIterator;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JTextField;

import songscribe.dom.DocumentScale;
import songscribe.dom.Ss;
import songscribe.font.LocalFonts;
import songscribe.font.LyricBoxMetrics;
import songscribe.font.SourceSans3Font;
import songscribe.font.TextMeasurement;

/**
 * Lyrics-font-derived values computed once per relayout rather than per element.
 *
 * @param lyricsFont       the lyrics font in unscaled (pixel-size) units
 * @param scaledLyricsFont the lyrics font scaled to staff-space units
 * @param placeholderFont  layout measures and the renderer draws the "?" for a missing lyric in
 *                         it, so the box matches the drawn glyph
 * @param staffToLyricsGapSs a <em>baseline</em> offset from a line's below-staff content to the
 *                         lyric row's baseline, not the visual gap
 */
public record LyricRenderMetrics(
    Font lyricsFont,
    Font scaledLyricsFont,
    Font placeholderFont,
    double hyphenWidthSs,
    double spaceWidthSs,
    double placeholderWidthSs,
    double staffToLyricsGapSs
) {

    public static final double HYPHEN_WIDENING_FACTOR = 1.75;

    /**
     * Every component is a function of the lyrics font, so this is the only way to build them
     * outside tests that deliberately inject degenerate values.
     */
    public static LyricRenderMetrics forFont(Font lyricsFont) {
        // SemiBold so the mark stands out from the lyrics; the face ships with the
        // application, so every document draws the mark at the same weight.
        var placeholderFont = LocalFonts.load(SourceSans3Font.STYLE_SEMIBOLD, lyricsFont.getSize2D());

        return new LyricRenderMetrics(
            lyricsFont,
            DocumentScale.fontSizedInSs(lyricsFont),
            placeholderFont,
            TextMeasurement.textWidthSs(lyricsFont, "-").value(),
            TextMeasurement.textWidthSs(lyricsFont, " ").value(),
            TextMeasurement.textWidthSs(placeholderFont, LyricLayoutBuilder.PLACEHOLDER_TEXT).value(),
            LineSpacing.LYRICS_ROW_MARGIN_SS + fontAboveBaselineSs(lyricsFont));
    }

    public double preferredHyphenCellWidthSs() {
        return HYPHEN_WIDENING_FACTOR * hyphenWidthSs;
    }

    public double lyricBoxWidthSs(String text) {
        if (text.isEmpty()) {
            return 0.0;
        }

        return TextMeasurement.textWidthSs(lyricsFont, text).value();
    }

    /**
     * A grace note anchors only the first grapheme cluster on its notehead.
     */
    public double firstGraphemeWidthSs(String text) {
        if (text.isEmpty()) {
            return 0.0;
        }

        var graphemes = BreakIterator.getCharacterInstance();
        graphemes.setText(text);
        var firstBoundary = graphemes.next();

        if (firstBoundary == BreakIterator.DONE) {
            return lyricBoxWidthSs(text);
        }

        return lyricBoxWidthSs(text.substring(0, firstBoundary));
    }

    /**
     * Includes glyph overhang; plain layout callers want the advance-only {@link #lyricBoxWidthSs}.
     *
     * @param text the lyric text; empty yields {@link LyricBoxMetrics#EMPTY}
     */
    public LyricBoxMetrics lyricBoxMetricsSs(String text) {
        return TextMeasurement.lyricBoxMetricsSs(lyricsFont, text);
    }

    /**
     * Independent of the typed text, so the editor box does not change height as descenders are
     * typed or deleted.
     */
    public double lyricBoxHeightSs() {
        return fontHeightSs(lyricsFont);
    }

    /**
     * Deliberately not {@link #lyricBoxHeightSs}: {@link JTextField} lays out from the font-wide
     * ascent and descent, so sizing the editor from ink would clip glyphs mid-edit.
     */
    public Ss editorBoxHeightSs() {
        return new Ss(
            TextMeasurement.fontAscentSs(lyricsFont).value()
                + TextMeasurement.fontDescentSs(lyricsFont).value()
        );
    }

    // ==========================================================================
    // Lyric font extents
    // ==========================================================================

    /**
     * Real ink is measured rather than the font's ascent/descent, whose font-wide slack would
     * read as excess space in every line's lyrics band.
     */
    private static final String LYRIC_EXTENT_REFERENCE = "Ājyg";

    private record FontInfo(String psName, float size) {}

    private static final Map<FontInfo, Rectangle2D> lyricExtentCache = new HashMap<>();

    private static Rectangle2D lyricExtentPx(Font font) {
        var fontInfo = new FontInfo(font.getPSName(), font.getSize2D());
        var cachedExtent = lyricExtentCache.get(fontInfo);

        if (cachedExtent != null) {
            return cachedExtent;
        }

        var extent = TextMeasurement.requireVisualBounds(LYRIC_EXTENT_REFERENCE, font);
        lyricExtentCache.put(fontInfo, extent);
        return extent;
    }

    public static double fontHeightSs(Font font) {
        return DocumentScale.pxToSs(lyricExtentPx(font).getHeight());
    }

    public static double fontAboveBaselineSs(Font font) {
        return DocumentScale.pxToSs(TextMeasurement.inkHeight(lyricExtentPx(font)));
    }
}
