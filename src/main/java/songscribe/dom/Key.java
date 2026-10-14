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

import org.jspecify.annotations.Nullable;

import songscribe.engraving.EngravingConstants;
import songscribe.engraving.StaffPosition;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.util.Copyable;

/**
 * A key, identified by its position on the circle of fifths: negative for a flat key,
 * positive for a sharp key, zero for no accidentals.
 *
 * <p>The constants run in fifths order, so {@link #values()} is the order a key list shows the
 * user and {@link #ordinal()} tracks {@link #fifths()}.
 *
 * <p>There is no mode: every key is major, because only MusicXML SongScribe wrote is read.
 */
public enum Key implements Copyable<Key> {
    SEVEN_FLATS(-7),
    SIX_FLATS(-6),
    FIVE_FLATS(-5),
    FOUR_FLATS(-4),
    THREE_FLATS(-3),
    TWO_FLATS(-2),
    ONE_FLAT(-1),

    /**
     * A real key, not an "unset" marker; a signature is understood to be drawn <em>from</em> it
     * when nothing states what precedes it.
     */
    NO_ACCIDENTALS(0),

    ONE_SHARP(1),
    TWO_SHARPS(2),
    THREE_SHARPS(3),
    FOUR_SHARPS(4),
    FIVE_SHARPS(5),
    SIX_SHARPS(6),
    SEVEN_SHARPS(7),
    ;

    public static final int MAX_ACCIDENTAL_COUNT = 7;

    /**
     * The key a new song's line 0 starts in, and the key assumed for a document that names none.
     * Named separately from the value it holds because it states a policy rather than a key.
     */
    public static final Key DEFAULT = FIVE_FLATS;

    // FLATS order: B E A D G C F; SHARPS order: F C G D A E B.
    private static final PitchClass[] FLAT_PITCH_CLASSES =
        {PitchClass.B, PitchClass.E, PitchClass.A, PitchClass.D, PitchClass.G, PitchClass.C, PitchClass.F};
    private static final PitchClass[] SHARP_PITCH_CLASSES =
        {PitchClass.F, PitchClass.C, PitchClass.G, PitchClass.D, PitchClass.A, PitchClass.E, PitchClass.B};

    // Staff positions for accidentals, relative to the middle line (0 = B4), in accidental
    // order. Flats: B E A D G C F. Sharps: F C G D A E B.
    private static final int[] FLAT_STAFF_POSITIONS = {0, -3, 1, -2, 2, -1, 3};
    private static final int[] SHARP_STAFF_POSITIONS = {-4, -1, -5, -2, 1, -3, 0};

    private static final List<Key> ALL_KEYS = List.of(values());

    private final int fifths;
    private final int accidentalCount;

    Key(int fifths) {
        this.fifths = fifths;
        accidentalCount = Math.abs(fifths);
    }

    /**
     * A caller reading untrusted input catches the range failure and converts it.
     */
    public static Key ofFifths(int fifths) {
        if (Math.abs(fifths) > MAX_ACCIDENTAL_COUNT) {
            throw new IllegalArgumentException(
                "fifths must be -" + MAX_ACCIDENTAL_COUNT + ".." + MAX_ACCIDENTAL_COUNT
                    + ", got " + fifths
            );
        }

        return ALL_KEYS.get(fifths + MAX_ACCIDENTAL_COUNT);
    }

    /**
     * The same immutable list is returned on every call, so a caller may keep the reference.
     */
    public static List<Key> allKeys() {
        return ALL_KEYS;
    }

    public int fifths() {
        return fifths;
    }

    @Override
    public Key copy() {
        return this;
    }

    public int accidentalCount() {
        return accidentalCount;
    }

    public boolean isFlatKey() {
        return fifths < 0;
    }

    private boolean altersPitchClass(PitchClass pitchClass) {
        // NO_ACCIDENTALS needs no guard: its count is zero, so this loop never runs.
        var pitchClasses = isFlatKey() ? FLAT_PITCH_CLASSES : SHARP_PITCH_CLASSES;

        for (var i = 0; i < accidentalCount; i++) {
            if (pitchClasses[i] == pitchClass) {
                return true;
            }
        }

        return false;
    }

    /**
     * The alteration this key applies to {@code pitchClass}: NATURAL where the key does not
     * alter it.
     */
    public Accidental alterationFor(PitchClass pitchClass) {
        return altersPitchClass(pitchClass) ? signatureAccidental() : Accidental.NATURAL;
    }

    public Accidental alterationAt(StaffPosition position) {
        return alterationFor(PitchClass.at(position.value()));
    }

    /**
     * One accidental of a key signature or of its cancellation, ready to be laid out.
     */
    public record DrawnAccidental(SMuFLGlyph glyph, int staffPositionSp, double leadingGapSs) {

        /**
         * Gap from a cancellation (the run of naturals) to the key signature that follows it,
         * after LilyPond's {@code KeyCancellation} {@code space-alist} {@code extra-space} of
         * {@value}.
         */
        public static final double CANCELLATION_TO_KEY_GAP_SS = 0.5;

        private static final double NATURAL_OVERLAP_KERNING_SS = 0.3;

        private static final double NATURAL_TOUCHING_KERNING_SS = 0.15;

        // LilyPond models each accidental's silhouette on a grid of half staff positions:
        // on its right side the glyph runs from its descender to its upper right corner,
        // and its left side is that same span shifted up.
        private static final int RIGHT_EDGE_DESCENDER = -6;
        private static final int RIGHT_EDGE_TOP_CORNER = 3;
        private static final int LEFT_EDGE_SHIFT = 3;

        /**
         * Returns how wide an accidental's ink is, which is how far one key signature accidental
         * carries the pen before the next one starts.
         * <p>
         * This is deliberately the ink extent, not the font's designed advance width that
         * {@link SMuFLMetadata#advanceWidthSs} reports and the rest of the program uses.
         * LilyPond stacks key signature accidentals ink edge to ink edge; swapping in the
         * advance width would change the spacing of every key signature.
         *
         * @param glyph the accidental glyph
         * @return the width of the glyph's bounding box, in staff spaces
         */
        public static double inkWidthSs(SMuFLGlyph glyph) {
            return SMuFLMetadata.bboxSs(glyph).widthSs();
        }

        /**
         * Returns how far the pen moves after drawing this accidental, given what follows it.
         * <p>
         * The answer is this glyph's {@linkplain #inkWidthSs ink width}, plus — in the one case
         * that needs it — the kerning a natural's vertical right edge needs to clear the left edge
         * of the accidental beside it.
         * <p>
         * Every other case answers the plain ink width, so this is total for any {@code next}:
         *
         * <ul>
         *   <li>this accidental is not a natural — a sharp and a flat have no vertical right edge
         *       to collide with anything;</li>
         *   <li>{@code next} is {@code null} — nothing follows, so there is nothing to clear;</li>
         *   <li>{@code next} has a non-zero {@link #leadingGapSs} — kerning stays within a group,
         *       because a leading gap means {@code next} opens the following group and already
         *       stands that far off, so the kerning run ends here.</li>
         * </ul>
         *
         * @param next the accidental drawn immediately to the right of this one, or {@code null}
         *             when this is the last one drawn
         * @return the advance in staff spaces, never less than this glyph's ink width
         */
        public double advanceSs(@Nullable DrawnAccidental next) {
            var inkWidthSs = inkWidthSs(glyph);

            if (next == null || glyph != SMuFLGlyph.ACCIDENTAL_NATURAL || next.leadingGapSs != 0) {
                return inkWidthSs;
            }

            return inkWidthSs + naturalKerningSs(staffPositionSp, next.staffPositionSp);
        }

        private static double naturalKerningSs(int staffPositionSp, int nextStaffPositionSp) {
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

            return overlapTop > overlapBottom
                ? NATURAL_OVERLAP_KERNING_SS
                : NATURAL_TOUCHING_KERNING_SS;
        }
    }

    /**
     * Returns the accidentals to draw for a change from {@code sourceKey} into this key, left to
     * right.
     *
     * <p><b>The cancellation policy</b>, this key being the one taking effect:
     *
     * <ul>
     *   <li><b>Same type, any change of count</b> — no naturals; this signature alone, which is
     *       understood to supersede the previous one.</li>
     *   <li><b>Different type</b>, including to or from {@link #NO_ACCIDENTALS} — cancellation
     *       naturals for the <em>entire</em> previous signature, then this signature.</li>
     * </ul>
     *
     * <p>Every consumer of the policy asks here rather than restating it: a mid-line key change's
     * width, the layout reservation for a cautionary at the end of a line, the renderer that draws
     * one, and the MusicXML writer deciding whether to emit a {@code <cancel>}. A second copy of
     * the rule in any of them would go on answering the old question after the policy changed, and
     * each subsystem's own tests would keep passing.
     *
     * <p>Cancellation naturals always come first, so a caller may rely on the result being a
     * (possibly empty) run of naturals followed by a (possibly empty) run of sharps or flats.
     * There is no case in which a cancellation follows the key it cancels, which is why only
     * {@link DrawnAccidental#CANCELLATION_TO_KEY_GAP_SS} ever separates the two groups, and it
     * is what lets the MusicXML writer decide whether a {@code <cancel>} is owed by asking whether
     * the list opens with a natural.
     *
     * @param sourceKey the key in effect before the change
     * @return the accidentals to draw, in drawing order; empty when {@code sourceKey} is this key,
     *         since a key that does not change draws nothing. The returned list is immutable.
     */
    public List<DrawnAccidental> accidentalsFrom(Key sourceKey) {
        if (sourceKey == this) {
            return List.of();
        }

        var accidentals = new ArrayList<DrawnAccidental>();

        // A type change is a change of sign. NO_ACCIDENTALS has nothing to cancel, so it is only
        // ever the target of a cancellation, never its source.
        if (sourceKey != NO_ACCIDENTALS && Integer.signum(sourceKey.fifths) != Integer.signum(fifths)) {
            accidentals.addAll(groupOf(sourceKey, SMuFLGlyph.ACCIDENTAL_NATURAL, accidentals.size()));
        }

        if (this != NO_ACCIDENTALS) {
            accidentals.addAll(groupOf(this, accidentalGlyph(), accidentals.size()));
        }

        return List.copyOf(accidentals);
    }

    public List<DrawnAccidental> signatureAccidentals() {
        return accidentalsFrom(NO_ACCIDENTALS);
    }

    /**
     * Excludes {@link EngravingConstants#KEY_SIGNATURE_PADDING_SS}, which is the caller's to add.
     */
    public double widthSsFrom(Key sourceKey) {
        var accidentals = accidentalsFrom(sourceKey);
        var widthSs = 0.0;

        for (var i = 0; i < accidentals.size(); i++) {
            var accidental = accidentals.get(i);
            var next = i + 1 < accidentals.size() ? accidentals.get(i + 1) : null;

            widthSs += accidental.leadingGapSs() + accidental.advanceSs(next);
        }

        return widthSs;
    }

    /**
     * {@link #widthSsFrom}'s answer for a change out of {@link #NO_ACCIDENTALS}, so a header
     * signature and the same signature drawn as a cautionary cannot drift apart in width.
     */
    public double signatureWidthSs() {
        return widthSsFrom(NO_ACCIDENTALS);
    }

    /**
     * Every accidental in a signature is the same glyph, so one glyph's height answers for the
     * run.
     */
    public double signatureHeightSs() {
        if (this == NO_ACCIDENTALS) {
            return 0;
        }

        return SMuFLMetadata.bboxSs(accidentalGlyph()).heightSs();
    }

    /**
     * Undefined for {@link #NO_ACCIDENTALS}, which draws no accidental.
     */
    private SMuFLGlyph accidentalGlyph() {
        return signatureAccidental().glyph();
    }

    private Accidental signatureAccidental() {
        return isFlatKey() ? Accidental.FLAT : Accidental.SHARP;
    }

    private static List<DrawnAccidental> groupOf(Key key, SMuFLGlyph glyph, int precedingCount) {
        var staffPositions = key.isFlatKey() ? FLAT_STAFF_POSITIONS : SHARP_STAFF_POSITIONS;
        var groupGapSs = precedingCount == 0 ? 0 : DrawnAccidental.CANCELLATION_TO_KEY_GAP_SS;
        var group = new ArrayList<DrawnAccidental>(key.accidentalCount);

        for (var i = 0; i < key.accidentalCount; i++) {
            group.add(new DrawnAccidental(glyph, staffPositions[i], i == 0 ? groupGapSs : 0));
        }

        return group;
    }
}
