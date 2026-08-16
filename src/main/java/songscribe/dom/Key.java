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
import songscribe.smufl.SMuFLMetadata;
import songscribe.util.Copyable;

/**
 * A key signature, identified by its position on the circle of fifths: negative for a flat key,
 * positive for a sharp key, zero for no accidentals.
 *
 * <p>The signed count <em>is</em> the key. It carries the accidental type and how many there are
 * in one value, so there is no pair to hold consistent and no combination outside the fifteen
 * declared here — an eight-sharp key is unwriteable rather than rejected at run time. The
 * constants run in fifths order, so {@link #values()} is the order a key list shows the user and
 * {@link #ordinal()} tracks {@link #fifths()}.
 *
 * <p>Two independent formats already speak this encoding: MusicXML's {@code <fifths>} and MIDI's
 * {@code FF 59} {@code sf} byte. It is the circle-of-fifths position, a fact about keys rather
 * than about either file format, which is why it lives here and both writers read it off a key
 * instead of deriving it.
 *
 * <p>There is no mode. Every key this program represents is major: MusicXML's {@code <key>}
 * carries an optional {@code <mode>} child, and the writer emits {@code major} for it, but nothing
 * here stores one, because this program reads only MusicXML it wrote itself (see "Only SongScribe
 * documents are read" in {@code docs/musicxml-object-model.md}), so no minor or modal key can ever
 * enter the model.
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
     * C major. A real key, not an "unset" marker, and the key a signature is understood to be
     * drawn <em>from</em> when nothing states what precedes it.
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

    /** Maximum number of accidentals in any standard key signature. */
    public static final int MAX_ACCIDENTAL_COUNT = 7;

    /**
     * The key a new song's line 0 starts in, and the key assumed for a document that names none.
     *
     * <p>Named separately from the value it holds because it states a policy rather than a
     * signature: a call site that means "whatever a song starts in" must not read as one that
     * means five flats specifically.
     */
    public static final Key DEFAULT = FIVE_FLATS;

    // FLATS order: B E A D G C F; SHARPS order: F C G D A E B.
    private static final int[] FLAT_PITCH_INDICES = {0, 3, 6, 2, 5, 1, 4};
    private static final int[] SHARP_PITCH_INDICES = {4, 1, 5, 2, 6, 3, 0};

    // Staff positions for accidentals, relative to the middle line (0 = B4), in accidental
    // order. Flats: B E A D G C F. Sharps: F C G D A E B.
    private static final int[] FLAT_STAFF_POSITIONS = {0, -3, 1, -2, 2, -1, 3};
    private static final int[] SHARP_STAFF_POSITIONS = {-4, -1, -5, -2, 1, -3, 0};

    private static final List<Key> ALL_SIGNATURES = List.of(values());

    private final int fifths;
    private final int accidentalCount;

    Key(int fifths) {
        this.fifths = fifths;
        accidentalCount = Math.abs(fifths);
    }

    /**
     * Returns the key at the given position on the circle of fifths.
     *
     * <p>The range is this type's to state, so it is enforced here rather than at each reader
     * that decodes a document. A caller reading untrusted input catches and converts, which is
     * what a document boundary is for: see {@code MeasureMapper.applyFifths} for MusicXML's
     * {@code <fifths>} and {@code LegacyKeyType.keyFor} for {@code .mssw}'s tag pair.
     *
     * @param fifths the position: flats negative, sharps positive, zero for no accidentals, with
     *               a magnitude of at most {@value #MAX_ACCIDENTAL_COUNT}
     * @return the key at that position; never null
     * @throws IllegalArgumentException if {@code |fifths|} exceeds
     *                                  {@value #MAX_ACCIDENTAL_COUNT}, which no key signature
     *                                  this program can represent ever does
     */
    public static Key ofFifths(int fifths) {
        if (Math.abs(fifths) > MAX_ACCIDENTAL_COUNT) {
            throw new IllegalArgumentException(
                "fifths must be -" + MAX_ACCIDENTAL_COUNT + ".." + MAX_ACCIDENTAL_COUNT
                    + ", got " + fifths
            );
        }

        return ALL_SIGNATURES.get(fifths + MAX_ACCIDENTAL_COUNT);
    }

    /**
     * Returns every key signature, exactly once, in fifths order.
     *
     * <p>The same immutable list is returned on every call, so a caller that holds it — a combo
     * model or a cell renderer's entry list — pays for it once and may keep the reference rather
     * than copying it. Preferred over {@link #values()}, which allocates a fresh array per call.
     *
     * @return every {@link Key}, from {@link #SEVEN_FLATS} to {@link #SEVEN_SHARPS}; immutable
     */
    public static List<Key> allSignatures() {
        return ALL_SIGNATURES;
    }

    /**
     * Returns this key's position on the circle of fifths.
     *
     * @return the signed count: negative for a flat key, positive for a sharp key, zero for
     *         {@link #NO_ACCIDENTALS}, with a magnitude of at most
     *         {@value #MAX_ACCIDENTAL_COUNT}
     */
    public int fifths() {
        return fifths;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@code this}. A key is one of fifteen enum constants and has no state to separate,
     *         so identity is what a copy of it means.
     */
    @Override
    public Key copy() {
        return this;
    }

    /**
     * Returns how many accidentals this key's signature draws.
     *
     * @return {@link #fifths()}'s magnitude: {@code 0..}{@value #MAX_ACCIDENTAL_COUNT}
     */
    public int accidentalCount() {
        return accidentalCount;
    }

    /**
     * Returns whether this key's accidentals are flats.
     *
     * @return {@code true} for a flat key; {@code false} for a sharp key and for
     *         {@link #NO_ACCIDENTALS}, which draws neither
     */
    public boolean isFlatKey() {
        return fifths < 0;
    }

    /**
     * Returns whether this key places an accidental on the given pitch class.
     *
     * @param pitchIndex the pitch class: 0 for B, 1 for C, 2 for D, 3 for E,
     *                   4 for F, 5 for G, 6 for A
     * @return {@code true} when this key's accidentals include {@code pitchIndex};
     *         always {@code false} for {@link #NO_ACCIDENTALS}
     */
    public boolean altersPitchClass(int pitchIndex) {
        // NO_ACCIDENTALS needs no guard: its count is zero, so this loop never runs.
        var pitchIndices = isFlatKey() ? FLAT_PITCH_INDICES : SHARP_PITCH_INDICES;

        for (var i = 0; i < accidentalCount; i++) {
            if (pitchIndices[i] == pitchIndex) {
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
     * {@link StaffHeaderMetrics#CANCELLATION_TO_KEY_GAP_SS} ever separates the two groups, and it
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

        return withAdvances(accidentals);
    }

    /**
     * Returns the accidentals this key draws for itself, with no cancellation — what a staff
     * header shows. Equivalent to {@link #accidentalsFrom} with {@link #NO_ACCIDENTALS} as the
     * source.
     *
     * @return its accidentals in fifths order, each with a zero leading gap; empty for
     *         {@link #NO_ACCIDENTALS}. The returned list is immutable.
     */
    public List<DrawnAccidental> signatureAccidentals() {
        return accidentalsFrom(NO_ACCIDENTALS);
    }

    /**
     * Returns how wide the drawn change from {@code sourceKey} into this key is: the sum of every
     * accidental's advance and leading gap. It excludes
     * {@link StaffHeaderMetrics#CAUTIONARY_RIGHT_MARGIN_SS}, which is the caller's to add.
     *
     * @param sourceKey the key in effect before the change
     * @return the laid-out width in staff spaces; zero when {@code sourceKey} is this key, and
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
     * <p>The answer is {@link #widthSsFrom}'s for a change out of {@link #NO_ACCIDENTALS}, so a
     * signature in the header and the same signature drawn as a cautionary at the end of the
     * previous line cannot drift apart in width.
     *
     * @return the width in staff spaces; zero for {@link #NO_ACCIDENTALS}, and never negative
     */
    public double signatureWidthSs() {
        return widthSsFrom(NO_ACCIDENTALS);
    }

    /**
     * Returns how tall this key's own signature is — the ink height of the accidental it draws.
     *
     * <p>Kerning and inter-glyph vertical variation are out of scope: every accidental in a
     * signature is the same glyph, so one glyph's height answers for the run. It is here rather
     * than on the header element for the reason {@link #signatureWidthSs()} is, so that a
     * signature drawn anywhere measures the same.
     *
     * @return the height in staff spaces; zero for {@link #NO_ACCIDENTALS}, which draws nothing
     */
    public double signatureHeightSs() {
        if (this == NO_ACCIDENTALS) {
            return 0;
        }

        return SMuFLMetadata.requireBBox(accidentalGlyph()).height();
    }

    /**
     * Returns the glyph this key's signature is drawn with.
     *
     * <p>Private and undefined for {@link #NO_ACCIDENTALS}, which draws no accidental; every
     * caller here has already excluded it.
     */
    private SMuFLGlyph accidentalGlyph() {
        return isFlatKey() ? SMuFLGlyph.ACCIDENTAL_FLAT : SMuFLGlyph.ACCIDENTAL_SHARP;
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
        var staffPositions = key.isFlatKey() ? FLAT_STAFF_POSITIONS : SHARP_STAFF_POSITIONS;
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
