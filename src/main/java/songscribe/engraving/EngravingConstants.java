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
 * The base stroke thickness every LilyPond-derived width in the program is a fixed
 * multiple of, together with the widths that belong to no single engraved thing — each
 * read either by more than one class, or by several classes all about one thing.
 * {@link #KEY_SIGNATURE_PADDING_SS} is the one member that is not a thickness at all; its
 * own doc says where it comes from.
 *
 * <p>A width read by exactly one class lives in that class instead, as a private
 * multiplier beside the resolved value, so the thing measured and the number measuring it
 * stay together.
 *
 * <p>LilyPond derives all element thicknesses from a single base staff-line thickness
 * using fixed multiplier ratios. The same ratios are used for both screen and print
 * output — screen pixel crispness is handled separately by device-pixel snapping (the
 * equivalent of LilyPond's {@code setstrokeadjust}).
 *
 * <p>At screen resolution (~72 dpi effective), raw SMuFL values collapse to the same 1–2
 * device pixels, destroying the intended distinctions in visual weight. The multiplier
 * ratios keep the elements distinguishable at any resolution.
 */
public final class EngravingConstants {
    // LilyPond multipliers relative to base staff-line thickness
    private static final double STAFF_LINE_MULTIPLIER = 1.0;
    private static final double VOLTA_BRACKET_MULTIPLIER = 1.61;

    /**
     * LilyPond base staff-line thickness in staff spaces. SMuFL Bravura uses 0.13,
     * but LilyPond uses 0.1 — applying LilyPond multipliers to a LilyPond base
     * produces the correct absolute thicknesses.
     */
    public static final double LILYPOND_BASE_THICKNESS_SS = 0.1;

    public static final double STAFF_LINE_THICKNESS_SS = LILYPOND_BASE_THICKNESS_SS * STAFF_LINE_MULTIPLIER;
    public static final double VOLTA_BRACKET_SS = LILYPOND_BASE_THICKNESS_SS * VOLTA_BRACKET_MULTIPLIER;

    /** Half the staff line width — the distance from a staff line's center to its edge. */
    public static final double STAFF_LINE_HALF_THICKNESS_SS = STAFF_LINE_THICKNESS_SS / 2.0;

    /**
     * Padding around a key signature that stands behind a barline: between that barline and the
     * signature's first accidental, and — for a cautionary, which also ends the staff line — between
     * its last accidental and the staff's right edge. Both a mid-line key change and a cautionary
     * are a barline followed by a run of accidentals, so both clear their barline by this distance
     * and a key change reads the same wherever it falls.
     * <p>
     * The staff header's own signature is not one of these: nothing stands behind it but the clef,
     * which is {@link StaffHeaderMetrics#CLEF_GAP_SS} away.
     * <p>
     * Not a LilyPond port — this program's own padding, {@value} staff spaces, and the same on both
     * sides of a cautionary so the signature reads as one unit rather than as a run that leans
     * toward one of its neighbours.
     */
    public static final double KEY_SIGNATURE_PADDING_SS = 0.75;

    private EngravingConstants() {}
}
