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
 *                         to the baseline of its first verse row. Equals
 *                         {@link LineSpacing#LYRICS_ROW_MARGIN_SS} plus the lyrics font
 *                         ascent, so a baseline placed at this distance puts the text
 *                         <em>top</em> one visual gap below the content.
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
     * Returns a stable lyric-box height in staff spaces — ascent + descent of the lyrics
     * font. Matches what {@link JTextField} actually paints (its baseline
     * lands at {@code insets.top + ascent} from {@link LineMetrics}, and
     * the descender extends {@code descent} below), so the box hugs the rendered glyphs
     * with no slack. Independent of the currently typed text so the editor box does not
     * change height as characters with descenders are typed or deleted.
     */
    public double lyricBoxHeightSs() {
        return ScaleContext.fontAscentSs(lyricsFont).value() + ScaleContext.fontDescentSs(lyricsFont).value();
    }
}
