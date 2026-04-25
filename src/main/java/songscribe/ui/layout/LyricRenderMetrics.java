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

package songscribe.ui.layout;

import module java.desktop;

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
 * @param spaceWidthSs     width of two space characters in staff-space units; used as the
 *                         gap between non-hyphenated syllables
 */
public record LyricRenderMetrics(
    Font lyricsFont,
    Font scaledLyricsFont,
    double hyphenWidthSs,
    double spaceWidthSs
) {

    /**
     * Minimum horizontal gap between syllables.
     * Note: This value will be tuned empirically during implementation.
     */
    public static final double MIN_SYLLABLE_GAP_SS = 0.25;  // 2px (TBD)

    /** Absolute minimum syllable gap during line-justification compression. */
    public static final double COMPRESSED_MIN_SYLLABLE_GAP_SS = 0.125;  // 1px

    /** Preferred hyphen cell width as a multiple of the "-" glyph width. */
    public static final double HYPHEN_WIDENING_FACTOR = 1.75;

    /** Returns the preferred cell width for a hyphen connector (glyph width × widening factor). */
    public double preferredHyphenCellWidthSs() {
        return HYPHEN_WIDENING_FACTOR * hyphenWidthSs;
    }
}
