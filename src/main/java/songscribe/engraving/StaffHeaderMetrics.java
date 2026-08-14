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

import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/**
 * Horizontal metrics for the staff header — the clef, the key signature and the space
 * they leave before the music starts — ported from LilyPond. All values are in staff spaces.
 * <p>
 * LilyPond's {@code Key_signature_interface::print} stacks the accidental glyphs with
 * a {@code padding} that the {@code KeySignature} grob leaves at its default of zero,
 * so consecutive accidentals advance by nothing more than their own bounding-box width
 * — they nest ink edge to ink edge. The one exception is the natural, whose vertical
 * edges on both sides would otherwise collide with its neighbour; see
 * {@link #naturalKerningSs}.
 * <p>
 * The gaps between the header's parts come from the {@code space-alist} entries of
 * LilyPond's break-aligned grobs, read by {@code Break_alignment_interface} and
 * {@code Staff_spacing::get_spacing}. The spacing type decides which edge the distance is
 * measured from: {@code extra-space} and {@code shrink-space} measure from the left item's
 * right edge, {@code minimum-fixed-space} from its left edge.
 * <p>
 * The tables are directional: LilyPond reads the {@code space-alist} of the <em>left</em>
 * item and looks the gap up under the <em>right</em> item's name, so the entry to port is the
 * one for the order the two parts actually appear in. Only one order arises here — a
 * cancellation always precedes the key signature it makes way for, never follows it, per the
 * policy on {@code songscribe.dom.KeyChange} — so only that direction is ported.
 */
public final class StaffHeaderMetrics {

    /**
     * Gap from the clef's right edge to the key signature's left edge.
     * LilyPond {@code Clef} {@code space-alist}: the {@code key-signature} entry,
     * an {@code extra-space} of {@value}.
     */
    public static final double CLEF_GAP_SS = 0.82;

    /**
     * Gap from a cancellation (the run of naturals) to the key signature that follows it —
     * the order LilyPond itself always uses, and the only order this program draws. LilyPond
     * {@code KeyCancellation} {@code space-alist}: the {@code key-signature} entry, an
     * {@code extra-space} of {@value}.
     */
    public static final double CANCELLATION_TO_KEY_GAP_SS = 0.5;

    /**
     * Gap from the key signature's right edge to the first note.
     * LilyPond {@code KeySignature} {@code space-alist}: the {@code first-note} entry, a
     * {@code shrink-space} of {@value}. {@code shrink-space}
     * makes this the distance at natural spacing, which a crowded line may compress but
     * never stretches.
     */
    public static final double KEY_SIGNATURE_FIRST_NOTE_GAP_SS = 2.5;

    /**
     * Span from the clef's <em>left</em> edge to the first note when no key signature
     * intervenes. LilyPond {@code Clef} {@code space-alist}: the {@code first-note} entry, a
     * {@code minimum-fixed-space} of {@value} — a floor on the whole
     * span rather than a gap added past the clef, so a clef narrower than this leaves the
     * remainder as open space.
     */
    public static final double CLEF_FIRST_NOTE_SPAN_SS = 5.0;

    /** Extra space before a natural whose neighbour's vertical extent it overlaps. */
    private static final double NATURAL_OVERLAP_KERNING_SS = 0.3;

    /** Extra space before a natural that merely touches its neighbour at the corners. */
    private static final double NATURAL_TOUCHING_KERNING_SS = 0.15;

    // LilyPond models each accidental's silhouette on a grid of half staff positions:
    // on its right side the glyph runs from its descender to its upper right corner,
    // and its left side is that same span shifted up.
    private static final int RIGHT_EDGE_DESCENDER = -6;
    private static final int RIGHT_EDGE_TOP_CORNER = 3;
    private static final int LEFT_EDGE_SHIFT = 3;

    private StaffHeaderMetrics() {}

    /**
     * Returns how far the pen advances after drawing a key signature accidental —
     * the glyph's bounding-box width, since LilyPond adds no padding of its own.
     * <p>
     * This is deliberately the ink extent, not the font's designed advance width that
     * {@code SMuFLMetadata.requireAdvanceWidth} reports and the rest of the program uses.
     * LilyPond stacks key signature accidentals ink edge to ink edge; swapping in the
     * advance width would change the spacing of every key signature.
     *
     * @param glyph the accidental glyph
     * @return the bounding-box width in staff spaces
     */
    public static double accidentalInkBboxSs(SMuFLGlyph glyph) {
        return SMuFLMetadata.requireBBox(glyph).width();
    }

    /**
     * Returns the extra space LilyPond inserts to the right of a natural so that its
     * vertical right edge clears the left edge of the accidental beside it.
     * <p>
     * Only call this when the left glyph of the pair is a natural; the sharp and flat
     * have no vertical right edge and need no kerning.
     *
     * @param staffPositionSp     staff position of the natural
     * @param nextStaffPositionSp staff position of the accidental to its right
     * @return the extra space in staff spaces, zero when the two extents clear each other
     */
    public static double naturalKerningSs(int staffPositionSp, int nextStaffPositionSp) {
        // SongScribe staff positions grow downward, LilyPond's grow upward.
        var upwardSp = -staffPositionSp;
        var nextUpwardSp = -nextStaffPositionSp;

        var rightEdgeBottom = 2 * upwardSp + RIGHT_EDGE_DESCENDER;
        var rightEdgeTop = 2 * upwardSp + RIGHT_EDGE_TOP_CORNER;
        var nextLeftEdgeBottom = 2 * nextUpwardSp + RIGHT_EDGE_DESCENDER + LEFT_EDGE_SHIFT;
        var nextLeftEdgeTop = 2 * nextUpwardSp + RIGHT_EDGE_TOP_CORNER + LEFT_EDGE_SHIFT;

        var overlapBottom = Math.max(rightEdgeBottom, nextLeftEdgeBottom);
        var overlapTop = Math.min(rightEdgeTop, nextLeftEdgeTop);

        if (overlapTop < overlapBottom) {
            return 0;
        }

        return overlapTop > overlapBottom ? NATURAL_OVERLAP_KERNING_SS : NATURAL_TOUCHING_KERNING_SS;
    }
}
