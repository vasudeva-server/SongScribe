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

package songscribe.engraving;

/**
 * The base stroke thickness every LilyPond-derived width is a fixed multiple of, and the widths
 * shared by more than one class. A width read by exactly one class lives in that class.
 */
public final class EngravingConstants {
    // LilyPond multipliers relative to base staff-line thickness
    private static final double STAFF_LINE_MULTIPLIER = 1.0;
    private static final double VOLTA_BRACKET_MULTIPLIER = 1.61;

    /**
     * LilyPond's base staff-line thickness. Holds for strokes this program draws on its own; a
     * stroke that registers against font glyphs takes the font's width: see
     * {@link StemMetrics#thicknessSs()}.
     */
    public static final double LILYPOND_BASE_THICKNESS_SS = 0.1;

    public static final double STAFF_LINE_THICKNESS_SS = LILYPOND_BASE_THICKNESS_SS * STAFF_LINE_MULTIPLIER;
    public static final double VOLTA_BRACKET_SS = LILYPOND_BASE_THICKNESS_SS * VOLTA_BRACKET_MULTIPLIER;

    public static final double STAFF_LINE_HALF_THICKNESS_SS = STAFF_LINE_THICKNESS_SS / 2.0;

    /**
     * Padding between a barline and the first accidental of a key signature behind it, and for a
     * cautionary also between its last accidental and the staff's right edge. Not a LilyPond port.
     * The staff header's own signature uses {@link StaffHeaderMetrics#CLEF_GAP_SS} instead.
     */
    public static final double KEY_SIGNATURE_PADDING_SS = 0.75;

    private EngravingConstants() {}
}
