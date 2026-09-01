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

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.smufl.SMuFLGlyph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * {@link KeySignatureExtent}'s promise that what a key signature draws, and the room it takes, are
 * a function of <em>both</em> keys the change runs between — never of the new key alone.
 *
 * <p>The failure this guards is the one a caller who has only the new key to hand cannot see: a
 * change measured as though nothing preceded it omits the cancelling naturals, so the column
 * reserved for it is too narrow for the run actually drawn.
 *
 * <p>The metrics the extent is measured with are pinned here too — the run of accidentals a change
 * lays out, the kerning a natural needs to clear its neighbour, and the floor
 * {@link ElementType#KEY_CHANGE} declares for a key signature whose real width only the element
 * knows. They answer one question between them, and a caller reading any of them alone would get
 * an answer the others do not agree with.
 */
class KeySignatureExtentTest extends UnitTest {

    /** Staff spaces within which two computed widths count as the same. */
    private static final double TOLERANCE_SS = 1.0e-9;

    /** The sharp key every flat key here is reached from, so the policy calls for a cancellation. */
    private static final Key CANCELLED_SHARP_KEY = Key.TWO_SHARPS;

    /** The flat key every sharp key here is reached from, for the same reason. */
    private static final Key CANCELLED_FLAT_KEY = Key.THREE_FLATS;

    /** The natural whose neighbour each kerning band is read off. */
    private static final int REFERENCE_NATURAL_SP = 0;

    /** A neighbour far enough below that natural that its left edge clears the natural's right. */
    private static final int CLEAR_NEIGHBOUR_SP = 7;

    /** A neighbour one position nearer, where the two edges meet at a corner. */
    private static final int TOUCHING_NEIGHBOUR_SP = 6;

    @Test
    void testRestatingTheKeyAlreadyInEffectDrawsNothing() {
        var extent = new KeySignatureExtent(Key.TWO_SHARPS, Key.TWO_SHARPS);

        assertThat(extent.accidentals()).isEmpty();
        assertThat(extent.widthSs()).isZero();
    }

    /**
     * How much room a natural leaves after itself: none, the little a corner-to-corner touch
     * needs, or the more an overlap does. The three amounts are the engraver's, and are pinned
     * against each other rather than as numbers, because what could regress is which pair of staff
     * positions falls in which band.
     */
    private enum Kerning {
        NONE,
        TOUCHING,
        OVERLAPPING
    }

    private static double referenceKerningSs(Kerning band) {
        return switch (band) {
            case NONE -> kerningSs(REFERENCE_NATURAL_SP, CLEAR_NEIGHBOUR_SP);
            case TOUCHING -> kerningSs(REFERENCE_NATURAL_SP, TOUCHING_NEIGHBOUR_SP);
            case OVERLAPPING -> kerningSs(REFERENCE_NATURAL_SP, REFERENCE_NATURAL_SP);
        };
    }

    /**
     * The kerning a natural at {@code naturalSp} owes an accidental at {@code neighbourSp}, read
     * off {@link Key.DrawnAccidental#advanceSs} as the difference between its advance with that
     * neighbour and its advance with none — the only way to reach the kerning amount, since
     * {@code naturalKerningSs} is private to {@link Key.DrawnAccidental}.
     */
    private static double kerningSs(int naturalSp, int neighbourSp) {
        var natural = new Key.DrawnAccidental(SMuFLGlyph.ACCIDENTAL_NATURAL, naturalSp, 0.0);
        var neighbour = new Key.DrawnAccidental(SMuFLGlyph.ACCIDENTAL_SHARP, neighbourSp, 0.0);

        return natural.advanceSs(neighbour) - natural.advanceSs(null);
    }

    @Test
    void testTheThreeKerningBandsWidenAsTheNeighbourClosesIn() {
        assertThat(referenceKerningSs(Kerning.NONE))
            .as("a neighbour the natural's right edge clears is not pushed away at all")
            .isZero();

        assertThat(referenceKerningSs(Kerning.TOUCHING))
            .as("a neighbour touching at the corners is pushed away a little")
            .isPositive();

        assertThat(referenceKerningSs(Kerning.OVERLAPPING))
            .as("a neighbour overlapping the natural's right edge is pushed away further")
            .isGreaterThan(referenceKerningSs(Kerning.TOUCHING));
    }

    /**
     * One pair of neighbouring accidentals in a cancellation, the left one a natural.
     *
     * @param description  the case, as the test's display name
     * @param naturalSp    the natural's staff position
     * @param neighbourSp  the staff position of the accidental to its right
     * @param expected     the band the pair falls in
     */
    private record KerningCase(String description, int naturalSp, int neighbourSp, Kerning expected) {}

    /**
     * Three pairs a real cancellation produces, each in both orders. The reverse of a pair is a
     * different case rather than the same one: the natural's right edge and its neighbour's left
     * edge sit at different heights on the glyph, so swapping which of the two is the natural
     * moves the pair to another band.
     */
    static Stream<KerningCase> kerningCases() {
        return Stream.of(
            new KerningCase("a natural three positions above its neighbour touches it",
                0, -3, Kerning.TOUCHING),
            new KerningCase("the same two positions the other way round overlap",
                -3, 0, Kerning.OVERLAPPING),
            new KerningCase("a natural four positions below its neighbour clears it",
                -1, -5, Kerning.NONE),
            new KerningCase("the same two positions the other way round overlap",
                -5, -1, Kerning.OVERLAPPING),
            new KerningCase("a natural four positions above its neighbour overlaps it",
                -3, 1, Kerning.OVERLAPPING),
            new KerningCase("the same two positions the other way round clear",
                1, -3, Kerning.NONE));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("kerningCases")
    void testANaturalPushesItsNeighbourAwayByTheBandTheirEdgesFallIn(KerningCase testCase) {
        assertThat(kerningSs(testCase.naturalSp(), testCase.neighbourSp()))
            .isEqualTo(referenceKerningSs(testCase.expected()));
    }

    /**
     * One key signature, as the change into it.
     *
     * @param sourceKey the key in effect before the change
     * @param newKey    the key taking effect
     */
    private record AccidentalRunCase(Key sourceKey, Key newKey) {

        @Override
        public String toString() {
            return newKey + " from " + sourceKey;
        }
    }

    /**
     * Every key, twice: once out of {@link Key#NO_ACCIDENTALS}, which is the run a staff header
     * shows, and once out of a key of the opposite type, which is the run a cancellation makes of
     * it. Driven from {@link Key#values()} so that a key added later is measured rather than
     * skipped.
     */
    static Stream<AccidentalRunCase> accidentalRunCases() {
        return Stream.of(Key.values()).flatMap(key -> Stream.of(
            new AccidentalRunCase(Key.NO_ACCIDENTALS, key),
            new AccidentalRunCase(cancellingSourceFor(key), key)));
    }

    /** A key of the opposite type to {@code key}, so the policy calls for a cancellation of it. */
    private static Key cancellingSourceFor(Key key) {
        return key.isFlatKey() ? CANCELLED_SHARP_KEY : CANCELLED_FLAT_KEY;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("accidentalRunCases")
    void testEachAccidentalAdvancesByItsOwnInkAndTheKerningItsNeighbourNeeds(
        AccidentalRunCase testCase
    ) {
        var sourceKey = testCase.sourceKey();
        var newKey = testCase.newKey();
        var accidentals = newKey.accidentalsFrom(sourceKey);

        if (sourceKey == newKey) {
            assertThat(accidentals).as("restating the key in effect draws nothing").isEmpty();
            return;
        }

        var naturalCount = cancels(sourceKey, newKey) ? sourceKey.accidentalCount() : 0;
        var ownGlyph = newKey.isFlatKey() ? SMuFLGlyph.ACCIDENTAL_FLAT : SMuFLGlyph.ACCIDENTAL_SHARP;

        assertThat(accidentals)
            .as("the cancelling naturals, then the new key's own accidentals")
            .hasSize(naturalCount + newKey.accidentalCount());

        var widthSs = 0.0;

        for (var index = 0; index < accidentals.size(); index++) {
            var accidental = accidentals.get(index);
            var isNatural = index < naturalCount;
            var isFirstOfSecondGroup = naturalCount > 0 && index == naturalCount;

            assertThat(accidental.glyph())
                .as("accidental %d is drawn as %s", index, isNatural ? "a natural" : "the key's own")
                .isEqualTo(isNatural ? SMuFLGlyph.ACCIDENTAL_NATURAL : ownGlyph);

            assertThat(accidental.leadingGapSs())
                .as("only the first accidental past the cancellation is pushed off the group "
                    + "before it (accidental %d)", index)
                .isEqualTo(isFirstOfSecondGroup
                    ? Key.DrawnAccidental.CANCELLATION_TO_KEY_GAP_SS
                    : 0.0);

            // A natural is kerned only against a neighbour inside its own group: the gap that
            // separates the two groups already holds the last natural off what follows it.
            var kerningSs = index + 1 < naturalCount
                ? kerningSs(
                    accidental.staffPositionSp(), accidentals.get(index + 1).staffPositionSp())
                : 0.0;

            var next = index + 1 < accidentals.size() ? accidentals.get(index + 1) : null;

            assertThat(accidental.advanceSs(next))
                .as("accidental %d advances by its own ink and the kerning it owes", index)
                .isCloseTo(
                    Key.DrawnAccidental.inkWidthSs(accidental.glyph()) + kerningSs,
                    within(TOLERANCE_SS));

            widthSs += accidental.leadingGapSs() + accidental.advanceSs(next);
        }

        assertThat(new KeySignatureExtent(sourceKey, newKey).widthSs())
            .as("the column reserved is the run laid out")
            .isCloseTo(widthSs, within(TOLERANCE_SS));
    }

    /**
     * Whether the change from {@code sourceKey} into {@code newKey} draws cancelling naturals:
     * the policy calls for them exactly when the two keys are of different types, and
     * {@link Key#NO_ACCIDENTALS} has nothing to cancel.
     */
    private static boolean cancels(Key sourceKey, Key newKey) {
        return sourceKey != Key.NO_ACCIDENTALS
            && Integer.signum(sourceKey.fifths()) != Integer.signum(newKey.fifths());
    }

    /**
     * One key signature the {@link ElementType#KEY_CHANGE} floor has to stay under.
     *
     * @param description the case, as the test's display name
     * @param previousKey the key in effect before the change
     * @param newKey      the key taking effect
     */
    private record KeySignatureFloorCase(String description, Key previousKey, Key newKey) {}

    static Stream<KeySignatureFloorCase> keySignatureFloorCases() {
        return Stream.of(
            new KeySignatureFloorCase("a signature of sharps", Key.NO_ACCIDENTALS, Key.THREE_SHARPS),
            new KeySignatureFloorCase("a signature of flats", Key.NO_ACCIDENTALS, Key.FOUR_FLATS),
            new KeySignatureFloorCase("a signature that cancels the one before it",
                Key.TWO_SHARPS, Key.THREE_FLATS));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("keySignatureFloorCases")
    void testTheKeyChangeTypeReservesAFloorNoDrawnSignatureFallsUnder(
        KeySignatureFloorCase testCase
    ) {
        var narrowestAccidentalSs = Math.min(
            Key.DrawnAccidental.inkWidthSs(SMuFLGlyph.ACCIDENTAL_FLAT),
            Key.DrawnAccidental.inkWidthSs(SMuFLGlyph.ACCIDENTAL_SHARP));

        assertThat(ElementType.KEY_CHANGE.getFullElementWidthSs())
            .as("the type alone knows only the narrowest signature that draws anything")
            .isCloseTo(narrowestAccidentalSs, within(TOLERANCE_SS))
            .as("so it never over-reserves for a real one")
            .isLessThanOrEqualTo(
                new KeySignatureExtent(testCase.previousKey(), testCase.newKey()).widthSs());
    }
}
