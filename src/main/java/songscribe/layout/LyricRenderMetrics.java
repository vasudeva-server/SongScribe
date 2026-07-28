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

import module java.desktop;

import java.text.BreakIterator;
import java.util.HashMap;
import java.util.Map;

import songscribe.dom.ScaleContext;
import songscribe.util.GraphicUtils;

/**
 * Lyrics-font-derived values hoisted to song scope so they are computed once
 * per relayout rather than per element. Used by both the layout engine (for measuring
 * syllable widths and reserving inter-syllable gaps) and the renderers (for drawing).
 *
 * @param lyricsFont       the lyrics font in unscaled (pixel-size) units; used by the
 *                         layout engine with {@link ScaleContext#textWidthSs} to measure
 *                         syllable advances in staff-space units
 * @param scaledLyricsFont the lyrics font scaled to staff-space units; used by renderers
 *                         that draw inside the staff-space coordinate transform
 * @param hyphenWidthSs    width of a hyphen glyph in staff-space units
 * @param spaceWidthSs     width of one space character in staff-space units; used as the
 *                         gap between non-hyphenated syllables
 * @param staffToLyricsGapSs distance in staff-space units from a line's below-staff content
 *                         to the baseline of its lyric row. Equals
 *                         {@link LineSpacing#LYRICS_ROW_MARGIN_SS} plus
 *                         {@link #fontAboveBaselineSs}, so a baseline placed at this distance
 *                         puts the text <em>ink top</em> one visual gap below the content.
 */
public record LyricRenderMetrics(
    Font lyricsFont,
    Font scaledLyricsFont,
    double hyphenWidthSs,
    double spaceWidthSs,
    double staffToLyricsGapSs
) {

    /** Preferred hyphen cell width as a multiple of the "-" glyph width. */
    public static final double HYPHEN_WIDENING_FACTOR = 1.75;

    /**
     * Derives the full set of metrics from the lyrics font alone — every component is a
     * function of it, so this is the only way they should be built outside tests that
     * deliberately inject degenerate values.
     * <p>
     * In particular {@code staffToLyricsGapSs} is a <em>baseline</em> offset, not the visual
     * gap: it is {@link LineSpacing#LYRICS_ROW_MARGIN_SS} plus the font's above-baseline ink,
     * so a baseline placed at this distance puts the text's ink top one visual margin below
     * the line's content. Getting that composition wrong shifts every verse in the song, which
     * is why it lives here rather than at the call site.
     */
    public static LyricRenderMetrics forFont(Font lyricsFont) {
        return new LyricRenderMetrics(
            lyricsFont,
            ScaleContext.scaleFont(lyricsFont),
            ScaleContext.textWidthSs(lyricsFont, "-").value(),
            ScaleContext.textWidthSs(lyricsFont, " ").value(),
            LineSpacing.LYRICS_ROW_MARGIN_SS + fontAboveBaselineSs(lyricsFont));
    }

    /** Returns the preferred cell width for a hyphen connector (glyph width × widening factor). */
    public double preferredHyphenCellWidthSs() {
        return HYPHEN_WIDENING_FACTOR * hyphenWidthSs;
    }

    /**
     * Returns the full lyric box width for {@code text} in staff spaces.
     */
    public double lyricBoxWidthSs(String text) {
        if (text.isEmpty()) {
            return 0.0;
        }

        return ScaleContext.textWidthSs(lyricsFont, text).value();
    }

    /**
     * Returns the width in staff spaces of {@code text}'s first grapheme cluster — one
     * user-perceived character, so a base letter plus its combining marks measures as a unit.
     * A grace note anchors this first cluster on its notehead rather than the whole syllable,
     * so the syllable reads as beginning at the grace note.
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
     * Visual layout metrics for a lyric box, all in staff-space units.
     *
     * @param advanceSs     cursor-advance width (the layout width used for column placement
     *                      and centering math)
     * @param leftBearingSs offset from the advance origin to the leftmost painted pixel;
     *                      typically 0 or slightly negative for glyphs that overhang to
     *                      the left of their advance origin
     * @param rightExtentSs offset from the advance origin to the rightmost painted pixel;
     *                      may exceed {@code advanceSs} for glyphs that overhang past their
     *                      advance width
     */
    public record LyricBoxMetrics(double advanceSs, double leftBearingSs, double rightExtentSs) {
        public static final LyricBoxMetrics EMPTY = new LyricBoxMetrics(0.0, 0.0, 0.0);
    }

    /**
     * Returns advance plus visual left/right extents for {@code text}. Use this when sizing
     * a container that must include glyph overhang (e.g. an in-place editor) so leftmost or
     * rightmost ink pixels are not clipped. Plain layout callers should keep using
     * {@link #lyricBoxWidthSs} since they want advance-only.
     */
    public LyricBoxMetrics lyricBoxMetricsSs(String text) {
        if (text.isEmpty()) {
            return LyricBoxMetrics.EMPTY;
        }

        var layout = new TextLayout(text, lyricsFont, GraphicUtils.SCREEN_FRC);
        var bounds = layout.getBounds();
        return new LyricBoxMetrics(
            ScaleContext.pxToSs(layout.getAdvance()),
            ScaleContext.pxToSs(bounds.getX()),
            ScaleContext.pxToSs(bounds.getX() + bounds.getWidth())
        );
    }

    /**
     * Returns a stable lyric-box height in staff spaces: the ink height of
     * {@link #LYRIC_EXTENT_REFERENCE} in the lyrics font. Independent of the currently typed
     * text, so the editor box does not change height as characters with descenders are typed
     * or deleted.
     */
    public double lyricBoxHeightSs() {
        return fontHeightSs(lyricsFont);
    }

    /**
     * Returns the height of the in-place lyric editor's text box in staff spaces — the lyrics
     * font's ascent + descent.
     * <p>
     * Deliberately not {@link #lyricBoxHeightSs}. The reserved lyric row hugs the ink of
     * {@link #LYRIC_EXTENT_REFERENCE}, but a live text field has to fit whatever the user
     * types, and {@link JTextField} lays its own painting out from the font-wide ascent and
     * descent — its baseline lands at {@code insets.top + ascent}, with the descender below.
     * Sizing the editor from ink would clip glyphs mid-edit.
     */
    public double editorBoxHeightSs() {
        return ScaleContext.fontAscentSs(lyricsFont).value() + ScaleContext.fontDescentSs(lyricsFont).value();
    }

    // ==========================================================================
    // Lyric font extents
    // ==========================================================================

    /**
     * The reference string measured to size a lyrics row. {@code Ā} contributes the tallest
     * ink a lyric can carry above the baseline — a capital plus a combining macron — and
     * {@code j} the deepest below it.
     * <p>
     * Real ink is measured rather than the font's own ascent/descent because those are
     * font-wide worst cases: ascent reserves height for glyphs lyrics never contain, and
     * descent reserves room for under-character marks. That slack sits inside every line's
     * lyrics band and reads as excess space between staff lines.
     */
    private static final String LYRIC_EXTENT_REFERENCE = "Ājyg";

    /** Identifies a font for caching purposes; the extents depend on face and size only. */
    private record FontInfo(String psName, float size) {}

    /** Baseline-relative ink bounds of {@link #LYRIC_EXTENT_REFERENCE}, in pixels. */
    private static final Map<FontInfo, Rectangle2D> lyricExtentCache = new HashMap<>();

    private static Rectangle2D lyricExtentPx(Font font) {
        var fontInfo = new FontInfo(font.getPSName(), font.getSize2D());
        var cachedExtent = lyricExtentCache.get(fontInfo);

        if (cachedExtent != null) {
            return cachedExtent;
        }

        var extent = font
            .createGlyphVector(GraphicUtils.SCREEN_FRC, LYRIC_EXTENT_REFERENCE)
            .getVisualBounds();
        lyricExtentCache.put(fontInfo, extent);
        return extent;
    }

    /**
     * Returns the full ink height of a lyrics row in {@code font}, in staff spaces — the
     * distance from the top of {@link #LYRIC_EXTENT_REFERENCE}'s ink to its bottom. This is
     * the height a line's lyric row reserves.
     */
    public static double fontHeightSs(Font font) {
        return ScaleContext.pxToSs(lyricExtentPx(font).getHeight());
    }

    /**
     * Returns the ink height above the baseline of a lyrics row in {@code font}, in staff
     * spaces. Use this to place a first baseline a known distance below other content:
     * offsetting by this puts the row's <em>ink top</em> at that distance, with no descender
     * included.
     */
    public static double fontAboveBaselineSs(Font font) {
        // Visual bounds are baseline-relative with Y growing downward, so the ink top edge is
        // a negative offset from the baseline.
        return ScaleContext.pxToSs(-lyricExtentPx(font).getY());
    }
}
