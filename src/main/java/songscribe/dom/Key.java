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
 * A key signature: a {@link KeyType} and how many accidentals it carries.
 *
 * <p>The domain is exactly {@code (NONE, 0)}, {@code (FLATS, 1..}
 * {@value #MAX_ACCIDENTAL_COUNT}{@code )}, and {@code (SHARPS, 1..}
 * {@value #MAX_ACCIDENTAL_COUNT}{@code )} — see {@link #allSignatures()}.
 * {@code keyType} is {@link KeyType#NONE} if and only if {@code accidentalCount}
 * is 0; {@code (NONE, 0)} is C major, a real key rather than an "unset" marker,
 * and {@code keyType} is never null.
 *
 * <p>There is no mode. Every key this program represents is major: MusicXML's
 * {@code <key>} carries an optional {@code <mode>} child, and the writer emits
 * {@code major} for it, but nothing in this record stores one, because this
 * program reads only MusicXML it wrote itself (see "Only SongScribe documents
 * are read" in {@code docs/musicxml-object-model.md}), so no minor or modal key
 * can ever enter the model.
 *
 * @param keyType         the kind of accidental this key carries; never null
 * @param accidentalCount the number of accidentals, {@code 0..}
 *                        {@value #MAX_ACCIDENTAL_COUNT}
 */
public record Key(KeyType keyType, int accidentalCount) {

    /** Maximum number of accidentals in any standard key signature. */
    public static final int MAX_ACCIDENTAL_COUNT = 7;

    /**
     * The key a new song's line 0 starts in: 5 flats.
     */
    public static final Key DEFAULT = new Key(KeyType.FLATS, 5);

    /**
     * C major — no accidentals. A real key, not an "unset" marker, and the key a signature is
     * understood to be drawn <em>from</em> when nothing states what precedes it.
     */
    public static final Key C_MAJOR = new Key(KeyType.NONE, 0);

    // FLATS order: B E A D G C F; SHARPS order: F C G D A E B.
    // Indexed as [keyType.ordinal() - 1][i]: valid only because KeyType.NONE
    // is ordinal 0, so FLATS (1) and SHARPS (2) land at rows 0 and 1. Reordering
    // KeyType silently corrupts this table.
    private static final int[][] FLAT_SHARP_ORDINAL = {
        {0, 3, 6, 2, 5, 1, 4},
        {4, 1, 5, 2, 6, 3, 0},
    };

    // Staff positions for accidentals, relative to the middle line (0 = B4), in accidental
    // order. Flats: B E A D G C F. Sharps: F C G D A E B.
    private static final int[] FLAT_STAFF_POSITIONS = {0, -3, 1, -2, 2, -1, 3};
    private static final int[] SHARP_STAFF_POSITIONS = {-4, -1, -5, -2, 1, -3, 0};

    /**
     * @param keyType         the kind of accidental this key carries; never null
     * @param accidentalCount the number of accidentals, {@code 0..}
     *                        {@value #MAX_ACCIDENTAL_COUNT}
     * @throws IllegalArgumentException if {@code keyType == KeyType.NONE} and
     *                                  {@code accidentalCount != 0}, if
     *                                  {@code keyType != KeyType.NONE} and
     *                                  {@code accidentalCount == 0}, or if
     *                                  {@code accidentalCount} is outside
     *                                  {@code 0..}{@value #MAX_ACCIDENTAL_COUNT}
     */
    public Key {
        if (accidentalCount < 0 || accidentalCount > MAX_ACCIDENTAL_COUNT) {
            throw new IllegalArgumentException(
                "accidentalCount must be 0.." + MAX_ACCIDENTAL_COUNT + ", got " + accidentalCount
            );
        }

        if ((keyType == KeyType.NONE) != (accidentalCount == 0)) {
            throw new IllegalArgumentException(
                "keyType == NONE iff accidentalCount == 0, got " + keyType + " with count " + accidentalCount
            );
        }
    }

    private static final List<Key> ALL_SIGNATURES = buildAllSignatures();

    /**
     * Returns every valid key signature, exactly once.
     *
     * <p>The same immutable list is returned on every call, so a caller that holds
     * it — a combo model or a cell renderer's entry list — pays for it once and may
     * keep the reference rather than copying it. Attempting to modify it throws
     * {@link UnsupportedOperationException}.
     *
     * @return every valid {@link Key}, in a stable order: {@code (NONE, 0)} first,
     *         then {@code FLATS} with 1..{@value #MAX_ACCIDENTAL_COUNT}
     *         accidentals, then {@code SHARPS} with 1..
     *         {@value #MAX_ACCIDENTAL_COUNT} accidentals
     */
    public static List<Key> allSignatures() {
        return ALL_SIGNATURES;
    }

    private static List<Key> buildAllSignatures() {
        var keys = new ArrayList<Key>(2 * MAX_ACCIDENTAL_COUNT + 1);
        keys.add(new Key(KeyType.NONE, 0));

        for (var count = 1; count <= MAX_ACCIDENTAL_COUNT; count++) {
            keys.add(new Key(KeyType.FLATS, count));
        }

        for (var count = 1; count <= MAX_ACCIDENTAL_COUNT; count++) {
            keys.add(new Key(KeyType.SHARPS, count));
        }

        return List.copyOf(keys);
    }

    /**
     * Returns whether this key places an accidental on the given pitch class.
     *
     * @param pitchIndex the pitch class: 0 for B, 1 for C, 2 for D, 3 for E,
     *                   4 for F, 5 for G, 6 for A
     * @return {@code true} when this key's accidentals include {@code pitchIndex};
     *         always {@code false} for {@code (NONE, 0)}
     */
    public boolean altersPitchClass(int pitchIndex) {
        if (keyType == KeyType.NONE) {
            return false;
        }

        var ordinals = FLAT_SHARP_ORDINAL[keyType.ordinal() - 1];

        for (var i = 0; i < accidentalCount; i++) {
            if (ordinals[i] == pitchIndex) {
                return true;
            }
        }

        return false;
    }

    /**
     * One accidental of a key signature or of its cancellation, ready to be laid out.
     *
     * @param glyph           the glyph to draw
     * @param staffPositionSp staff position relative to the middle line
     * @param leadingGapSs    extra space in front of this accidental, separating it from the group
     *                        to its left; zero for every accidental except the first of a group
     *                        that follows another group
     * @param advanceSs       how far the pen moves after drawing this accidental, including the
     *                        kerning a natural needs to clear the accidental to its right
     */
    public record DrawnAccidental(
        SMuFLGlyph glyph, int staffPositionSp, double leadingGapSs, double advanceSs) {}

    /**
     * Returns the accidentals to draw for a change from {@code sourceKey} into this key, left to
     * right.
     *
     * <p><b>The cancellation policy</b>, this key being the one taking effect:
     *
     * <ul>
     *   <li><b>Same type, any change of count</b> — no naturals; this signature alone, which is
     *       understood to supersede the previous one.</li>
     *   <li><b>Different type</b>, including to or from {@link KeyType#NONE} — cancellation
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
     * {@link StaffHeaderMetrics#CANCELLATION_TO_KEY_GAP_SS} ever separates the two groups, and it
     * is what lets the MusicXML writer decide whether a {@code <cancel>} is owed by asking whether
     * the list opens with a natural.
     *
     * @param sourceKey the key in effect before the change
     * @return the accidentals to draw, in drawing order; empty when {@code sourceKey} equals this
     *         key, since a key that does not change draws nothing. The returned list is immutable.
     */
    public List<DrawnAccidental> accidentalsFrom(Key sourceKey) {
        if (equals(sourceKey)) {
            return List.of();
        }

        var accidentals = new ArrayList<DrawnAccidental>();

        if (sourceKey.keyType != keyType && sourceKey.keyType != KeyType.NONE) {
            accidentals.addAll(groupOf(sourceKey, SMuFLGlyph.ACCIDENTAL_NATURAL, accidentals.size()));
        }

        if (keyType != KeyType.NONE) {
            accidentals.addAll(groupOf(this, keyType.glyph(), accidentals.size()));
        }

        return withAdvances(accidentals);
    }

    /**
     * Returns the accidentals this key draws for itself, with no cancellation — what a staff
     * header shows. Equivalent to {@link #accidentalsFrom} with {@link #C_MAJOR} as the source.
     *
     * @return its accidentals in fifths order, each with a zero leading gap; empty for
     *         {@link KeyType#NONE}. The returned list is immutable.
     */
    public List<DrawnAccidental> signatureAccidentals() {
        return accidentalsFrom(C_MAJOR);
    }

    /**
     * Returns how wide the drawn change from {@code sourceKey} into this key is: the sum of every
     * accidental's advance and leading gap. It excludes
     * {@link StaffHeaderMetrics#CAUTIONARY_RIGHT_MARGIN_SS}, which is the caller's to add.
     *
     * @param sourceKey the key in effect before the change
     * @return the laid-out width in staff spaces; zero when {@code sourceKey} equals this key, and
     *         never negative
     */
    public double widthSsFrom(Key sourceKey) {
        var widthSs = 0.0;

        for (var accidental : accidentalsFrom(sourceKey)) {
            widthSs += accidental.leadingGapSs() + accidental.advanceSs();
        }

        return widthSs;
    }

    /**
     * Returns how wide this key's own signature is — what a staff header reserves for it.
     *
     * <p>The answer is {@link #widthSsFrom}'s for a change out of {@link #C_MAJOR}, so a signature
     * in the header and the same signature drawn as a cautionary at the end of the previous line
     * cannot drift apart in width.
     *
     * @return the width in staff spaces; zero for {@link KeyType#NONE}, and never negative
     */
    public double signatureWidthSs() {
        return widthSsFrom(C_MAJOR);
    }

    /**
     * Builds one group of accidentals — a cancellation run or a signature run — all drawn with the
     * same glyph, at the staff positions {@code key}'s accidental order gives them.
     *
     * <p>{@code precedingCount} is how many accidentals already stand to the left. Only the first
     * accidental of a group that follows another group is pushed away; within a group the glyphs
     * nest with no gap.
     *
     * <p>Advances are left at zero here and filled in by {@link #withAdvances}, because a natural's
     * advance depends on the accidental after it, which the following group has not appended yet.
     */
    private static List<DrawnAccidental> groupOf(Key key, SMuFLGlyph glyph, int precedingCount) {
        var staffPositions = key.keyType == KeyType.FLATS ? FLAT_STAFF_POSITIONS : SHARP_STAFF_POSITIONS;
        var groupGapSs = precedingCount == 0 ? 0 : StaffHeaderMetrics.CANCELLATION_TO_KEY_GAP_SS;
        var group = new ArrayList<DrawnAccidental>(key.accidentalCount);

        for (var i = 0; i < key.accidentalCount; i++) {
            group.add(new DrawnAccidental(glyph, staffPositions[i], i == 0 ? groupGapSs : 0, 0));
        }

        return group;
    }

    /**
     * Returns {@code accidentals} with each entry's advance resolved against the entry that
     * follows it.
     *
     * <p>Kerning stays within a group: LilyPond computes it per key signature object, and the gap
     * separating two groups already holds them apart, so a non-zero leading gap on the following
     * accidental ends the run.
     */
    private static List<DrawnAccidental> withAdvances(List<DrawnAccidental> accidentals) {
        var resolved = new ArrayList<DrawnAccidental>(accidentals.size());

        for (var i = 0; i < accidentals.size(); i++) {
            var accidental = accidentals.get(i);
            var next = i + 1 < accidentals.size() ? accidentals.get(i + 1) : null;
            var advanceSs = StaffHeaderMetrics.accidentalInkBboxSs(accidental.glyph());

            if (next != null
                && accidental.glyph() == SMuFLGlyph.ACCIDENTAL_NATURAL
                && next.leadingGapSs() == 0) {

                advanceSs += StaffHeaderMetrics.naturalKerningSs(
                    accidental.staffPositionSp(), next.staffPositionSp());
            }

            resolved.add(new DrawnAccidental(
                accidental.glyph(), accidental.staffPositionSp(), accidental.leadingGapSs(), advanceSs));
        }

        return List.copyOf(resolved);
    }
}
