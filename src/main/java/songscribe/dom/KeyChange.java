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

package songscribe.dom;

import java.util.ArrayList;
import java.util.List;

import songscribe.engraving.StaffHeaderMetrics;
import songscribe.smufl.SMuFLGlyph;

/**
 * The cancellation policy: which accidentals are drawn when the key changes from one
 * {@link Key} to another, and how wide that run of accidentals is.
 *
 * <p><b>The policy</b>, given a previous key and a new key:
 *
 * <ul>
 *   <li><b>Same type, more accidentals</b> — no naturals; the new signature only.</li>
 *   <li><b>Same type, fewer accidentals</b> — no naturals; the new signature only. The new
 *       signature is understood to supersede the old one.</li>
 *   <li><b>Different type</b>, including to or from {@link KeyType#NONE} — cancellation
 *       naturals for the <em>entire</em> previous signature, then the new signature.</li>
 * </ul>
 *
 * <p>Every consumer of the policy asks this class rather than restating it: a mid-line key
 * signature's width, the layout reservation for a cautionary at the end of a line, the
 * renderer that draws one, and the MusicXML writer deciding whether to emit a
 * {@code <cancel>}. A second copy of the rule in any of them would go on answering the old
 * question after the policy changed, and each subsystem's own tests would keep passing.
 *
 * <p>Naturals therefore <em>always</em> precede the new signature's accidentals — there is
 * no case in which a cancellation follows the key it cancels — which is why only
 * {@link StaffHeaderMetrics#CANCELLATION_TO_KEY_GAP_SS} is ever used to separate the two
 * groups.
 *
 * <p>All measurements are in staff spaces. This class computes; it does not draw.
 */
public final class KeyChange {

    /**
     * Gap between the last drawn accidental of a cautionary key change and the right edge of
     * the staff line it sits at the end of.
     */
    public static final double RIGHT_MARGIN_SS = 0.625;  // 5px / 8 px/ss

    private static final SMuFLGlyph FLAT_GLYPH = SMuFLGlyph.ACCIDENTAL_FLAT;
    private static final SMuFLGlyph SHARP_GLYPH = SMuFLGlyph.ACCIDENTAL_SHARP;
    private static final SMuFLGlyph NATURAL_GLYPH = SMuFLGlyph.ACCIDENTAL_NATURAL;

    // Staff positions for accidentals, relative to the middle line (0 = B4), in accidental
    // order. Flats: B E A D G C F. Sharps: F C G D A E B.
    private static final int[] FLAT_STAFF_POSITIONS = {0, -3, 1, -2, 2, -1, 3};
    private static final int[] SHARP_STAFF_POSITIONS = {-4, -1, -5, -2, 1, -3, 0};

    private KeyChange() {}

    /**
     * One accidental of a key signature or of its cancellation, ready to be laid out.
     *
     * @param glyph           the glyph to draw
     * @param staffPositionSp staff position relative to the middle line
     * @param leadingGapSs    extra space in front of this accidental, separating it from the
     *                        group to its left; zero for every accidental except the first of
     *                        a group that follows another group
     */
    public record DrawnAccidental(SMuFLGlyph glyph, int staffPositionSp, double leadingGapSs) {}

    /**
     * Returns the accidentals a key signature draws for itself, with no cancellation —
     * what a staff header shows.
     *
     * @param key the key signature
     * @return its accidentals in fifths order, each with a zero leading gap; empty for
     *         {@link KeyType#NONE}. The returned list is immutable.
     */
    public static List<DrawnAccidental> signatureAccidentals(Key key) {
        if (key.keyType() == KeyType.NONE) {
            return List.of();
        }

        var accidentals = new ArrayList<DrawnAccidental>(key.accidentalCount());
        appendGroup(accidentals, key, accidentalGlyph(key.keyType()));

        return List.copyOf(accidentals);
    }

    /**
     * Returns the accidentals to draw for a change from {@code previous} to {@code next},
     * left to right, according to the policy stated on this class.
     *
     * <p>Cancellation naturals, where the policy calls for any, always come first, so a
     * caller may rely on the result being a (possibly empty) run of naturals followed by a
     * (possibly empty) run of sharps or flats. That ordering is what lets the MusicXML
     * writer decide whether a {@code <cancel>} is owed by asking whether the list opens with
     * a natural, rather than by restating the policy.
     *
     * @param previous the key in effect before the change
     * @param next     the key taking effect
     * @return the accidentals to draw, in drawing order; empty when
     *         {@code previous.equals(next)}, since a key that does not change draws nothing.
     *         The returned list is immutable.
     */
    public static List<DrawnAccidental> accidentals(Key previous, Key next) {
        if (previous.equals(next)) {
            return List.of();
        }

        var accidentals = new ArrayList<DrawnAccidental>();

        if (previous.keyType() != next.keyType() && previous.keyType() != KeyType.NONE) {
            appendGroup(accidentals, previous, NATURAL_GLYPH);
        }

        if (next.keyType() != KeyType.NONE) {
            appendGroup(accidentals, next, accidentalGlyph(next.keyType()));
        }

        return List.copyOf(accidentals);
    }

    /**
     * Returns how wide the drawn change from {@code previous} to {@code next} is: the sum of
     * every accidental's advance, the kerning between adjacent naturals, and the gap
     * separating the cancellation from the new signature. It excludes
     * {@link #RIGHT_MARGIN_SS}, which is the caller's to add.
     *
     * @param previous the key in effect before the change
     * @param next     the key taking effect
     * @return the laid-out width in staff spaces; zero when {@code previous.equals(next)},
     *         and never negative
     */
    public static double widthSs(Key previous, Key next) {
        return totalWidthSs(accidentals(previous, next));
    }

    /**
     * Returns the laid-out width of an already-collected run of accidentals.
     *
     * @param accidentals a run from {@link #accidentals} or {@link #signatureAccidentals}
     * @return the width in staff spaces; zero for an empty run
     */
    public static double totalWidthSs(List<DrawnAccidental> accidentals) {
        var widthSs = 0.0;

        for (var i = 0; i < accidentals.size(); i++) {
            widthSs += accidentals.get(i).leadingGapSs() + advanceSs(accidentals, i);
        }

        return widthSs;
    }

    /**
     * Returns how far the pen advances after drawing the accidental at {@code index},
     * including the kerning a natural needs to clear the accidental to its right.
     *
     * <p>Kerning stays within a group: LilyPond computes it per key signature object, and the
     * gap that separates two groups already holds them apart. A non-zero leading gap on the
     * following accidental is what marks that boundary.
     *
     * @param accidentals the run being laid out
     * @param index       the position within it
     * @return the advance in staff spaces; always at least the glyph's own ink width
     */
    public static double advanceSs(List<DrawnAccidental> accidentals, int index) {
        var accidental = accidentals.get(index);
        var advanceSs = StaffHeaderMetrics.accidentalInkBboxSs(accidental.glyph());
        var isLast = index == accidentals.size() - 1;

        if (isLast || accidental.glyph() != NATURAL_GLYPH) {
            return advanceSs;
        }

        var next = accidentals.get(index + 1);

        if (next.leadingGapSs() != 0) {
            return advanceSs;
        }

        return advanceSs
            + StaffHeaderMetrics.naturalKerningSs(accidental.staffPositionSp(), next.staffPositionSp());
    }

    /**
     * Returns the glyph a key signature of the given type is drawn with.
     *
     * @param keyType the key type; {@link KeyType#NONE} draws no accidental and has no glyph
     * @return the flat or sharp glyph
     * @throws IllegalArgumentException if {@code keyType} is {@link KeyType#NONE}
     */
    public static SMuFLGlyph accidentalGlyph(KeyType keyType) {
        return switch (keyType) {
            case FLATS -> FLAT_GLYPH;
            case SHARPS -> SHARP_GLYPH;
            case NONE -> throw new IllegalArgumentException("NONE draws no accidental");
        };
    }

    private static void appendGroup(List<DrawnAccidental> accidentals, Key key, SMuFLGlyph glyph) {
        var staffPositions = key.keyType() == KeyType.FLATS ? FLAT_STAFF_POSITIONS : SHARP_STAFF_POSITIONS;
        // Only the first accidental of a group that follows another group is pushed away;
        // within a group the glyphs nest with no gap.
        var groupGapSs = accidentals.isEmpty() ? 0 : StaffHeaderMetrics.CANCELLATION_TO_KEY_GAP_SS;

        for (var i = 0; i < key.accidentalCount(); i++) {
            accidentals.add(new DrawnAccidental(glyph, staffPositions[i], i == 0 ? groupGapSs : 0));
        }
    }
}
