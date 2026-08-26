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

import java.util.List;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.doubleBarline;
import static songscribe.dom.StaffElementFactory.keyChange;
import static songscribe.dom.StaffElementFactory.repeatLeft;
import static songscribe.dom.StaffElementFactory.singleBarline;

/**
 * Which key changes restate the key already in effect where they stand, and what goes with one
 * when it is removed.
 *
 * <p>Two questions, asked one after the other and tested together because neither answer means
 * anything alone. {@link StaffElementRun#strandedKeyChangeIndices} names the key changes;
 * {@link StaffElementRun#redundantKeyChangeRanges(Key)} turns each index into the range a deletion
 * covers, which is the element it is paired with as well as the key change itself.
 *
 * <p>Both are asked here of a {@link DetachedLyricRun} as well as of a {@link Line}, because a
 * clipboard fragment has to answer them too and the rule is one rule.
 *
 * <p>What rides on the answer is that a stranded key change draws nothing: it cancels no
 * accidentals and reserves no width, so it is invisible on screen while still refusing the two
 * insertion slots flanking it and still reaching MusicXML on the next save. Nothing on screen
 * reports the miss, which is why the query rather than the rendering is what is asserted here.
 */
class StrandedKeyChangeTest extends UnitTest {

    /** The key every fixture here starts in, and the one a restatement restates. */
    private static final Key RUNNING_KEY = Key.NO_ACCIDENTALS;

    /** A key {@link #RUNNING_KEY} is not, so a signature establishing it is never stranded. */
    private static final Key OTHER_KEY = Key.TWO_SHARPS;

    /** A third key, so a case needing two changes has two that differ from each other. */
    private static final Key THIRD_KEY = Key.THREE_FLATS;

    /** The index of the sole mid-line signature in the two-element-preamble fixtures. */
    private static final int FIRST_SIGNATURE_INDEX = 2;

    /** The index of a second mid-line signature, three elements past the first. */
    private static final int SECOND_SIGNATURE_INDEX = 5;

    /**
     * One list of elements to scan, and what
     * {@link StaffElementRun#strandedKeyChangeIndices} must answer.
     *
     * @param description the case, which doubles as the parameterized display name
     * @param elements    the elements to scan
     * @param fromIndex   the lowest index to count a signature at
     * @param keyInEffect the key in effect at {@code fromIndex}
     * @param expected    the indices the scan must report, ascending
     */
    private record StrandedCase(
        String description,
        List<StaffElement> elements,
        int fromIndex,
        Key keyInEffect,
        List<Integer> expected
    ) {

        @Override
        public String toString() {
            return description;
        }
    }

    /**
     * One list of elements, and the key {@link StaffElementRun#lastKeyChangeKeyFrom} must read
     * off it.
     *
     * @param description the case, which doubles as the parameterized display name
     * @param elements    the elements to scan
     * @param fromIndex   the lowest index to count a signature at
     * @param expected    the key the last signature at or after {@code fromIndex} establishes, or
     *                    null when none stands there
     */
    private record LastKeyCase(
        String description,
        List<StaffElement> elements,
        int fromIndex,
        @Nullable Key expected
    ) {

        @Override
        public String toString() {
            return description;
        }
    }

    /**
     * One kind of element a mid-line key signature may stand behind, all of which the pair rule
     * treats alike.
     *
     * @param description the case, which doubles as the parameterized display name
     * @param barline     the element standing in front of the signature
     */
    private record BarlineCase(String description, StaffElement barline) {

        @Override
        public String toString() {
            return description;
        }
    }

    /** A run of {@code note, barline, signature}, which is the shortest legal placement. */
    private static List<StaffElement> signatureRun(Key key) {
        return List.of(crotchet(), singleBarline(), keyChange(key));
    }

    /** Two such runs back to back, so the second signature is measured against the first. */
    private static List<StaffElement> twoSignatureRuns(Key first, Key second) {
        return List.of(
            crotchet(), singleBarline(), keyChange(first),
            crotchet(), singleBarline(), keyChange(second));
    }

    static Stream<StrandedCase> strandedCases() {
        return Stream.of(
            new StrandedCase(
                "a signature that changes the key is not stranded",
                signatureRun(OTHER_KEY), 1, RUNNING_KEY, List.of()),
            new StrandedCase(
                "a signature restating the key in effect is stranded",
                signatureRun(RUNNING_KEY), 1, RUNNING_KEY, List.of(FIRST_SIGNATURE_INDEX)),
            new StrandedCase(
                "a signature restating what an earlier signature established is stranded",
                twoSignatureRuns(OTHER_KEY, OTHER_KEY), 1, RUNNING_KEY,
                List.of(SECOND_SIGNATURE_INDEX)),
            new StrandedCase(
                "consecutive restatements are all reported, since the tracked key never advances "
                    + "past a stranded one",
                twoSignatureRuns(RUNNING_KEY, RUNNING_KEY), 1, RUNNING_KEY,
                List.of(FIRST_SIGNATURE_INDEX, SECOND_SIGNATURE_INDEX)),
            new StrandedCase(
                "a signature below fromIndex is neither reported nor allowed to move the "
                    + "tracked key",
                twoSignatureRuns(OTHER_KEY, RUNNING_KEY), SECOND_SIGNATURE_INDEX - 1, RUNNING_KEY,
                List.of(SECOND_SIGNATURE_INDEX)),
            new StrandedCase(
                "the first signature is measured against keyInEffect, not against the key the "
                    + "list opens in",
                signatureRun(OTHER_KEY), 1, OTHER_KEY, List.of(FIRST_SIGNATURE_INDEX)));
    }

    static Stream<LastKeyCase> lastKeyCases() {
        return Stream.of(
            new LastKeyCase(
                "the last signature's key, with earlier ones ignored",
                twoSignatureRuns(OTHER_KEY, THIRD_KEY), 0, THIRD_KEY),
            new LastKeyCase(
                "a signature standing at fromIndex itself counts",
                signatureRun(OTHER_KEY), FIRST_SIGNATURE_INDEX, OTHER_KEY),
            new LastKeyCase(
                "null when every signature is below fromIndex",
                signatureRun(OTHER_KEY), FIRST_SIGNATURE_INDEX + 1, null),
            new LastKeyCase(
                "null for a run holding no signature at all",
                List.of(crotchet(), singleBarline(), crotchet()), 0, null));
    }

    static Stream<BarlineCase> barlineCases() {
        return Stream.of(
            new BarlineCase("a single barline", singleBarline()),
            new BarlineCase("a double barline", doubleBarline()),
            new BarlineCase("a repeat", repeatLeft()));
    }

    /** A line in {@link #RUNNING_KEY} holding {@code elements} from index 0. */
    private static Line lineHolding(List<StaffElement> elements) {
        var line = detachedLine();

        line.setKey(RUNNING_KEY);

        for (var element : elements) {
            line.addElement(element);
        }

        return line;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("strandedCases")
    void testEveryKeyChangeRestatingTheKeyTrackedWhereItStandsIsReported(StrandedCase testCase) {
        assertThat(new DetachedLyricRun(testCase.elements())
            .strandedKeyChangeIndices(testCase.fromIndex(), testCase.keyInEffect()))
            .isEqualTo(testCase.expected());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("lastKeyCases")
    void testTheKeyARunLeavesInEffectIsItsLastKeyChangeAtOrAfterTheGivenIndex(
        LastKeyCase testCase) {

        assertThat(new DetachedLyricRun(testCase.elements())
            .lastKeyChangeKeyFrom(testCase.fromIndex()))
            .isEqualTo(testCase.expected());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("barlineCases")
    void testARangeTakesTheBarlineTheStrandedSignatureStandsBehind(BarlineCase testCase) {
        var line = lineHolding(
            List.of(crotchet(), testCase.barline(), keyChange(RUNNING_KEY)));

        assertThat(line.redundantKeyChangeRanges(RUNNING_KEY))
            .containsExactly(
                new StaffElementRun.EffectiveRange(FIRST_SIGNATURE_INDEX - 1, FIRST_SIGNATURE_INDEX));
    }

    @Test
    void testRangesAreMeasuredAgainstTheKeyPassedInRatherThanTheLinesOwn() {
        var line = lineHolding(signatureRun(OTHER_KEY));

        assertThat(line.redundantKeyChangeRanges(line.getRunningKey()))
            .as("the signature changes the key the line actually runs in, so nothing is stranded")
            .isEmpty();

        assertThat(line.redundantKeyChangeRanges(OTHER_KEY))
            .as("under the key the caller's edit will leave the line in, the signature restates it")
            .containsExactly(
                new StaffElementRun.EffectiveRange(FIRST_SIGNATURE_INDEX - 1, FIRST_SIGNATURE_INDEX));
    }

    @Test
    void testTheBoundedFormCountsOnlySignaturesAtOrAfterItsIndexAndMayReachOneBelowIt() {
        var line = lineHolding(twoSignatureRuns(OTHER_KEY, THIRD_KEY));

        assertThat(line.redundantKeyChangeRanges(SECOND_SIGNATURE_INDEX, THIRD_KEY))
            .as("the earlier signature is behind the edit and is not counted, while the later one "
                + "restates the key the edit leaves in effect and takes its barline with it")
            .containsExactly(
                new StaffElementRun.EffectiveRange(SECOND_SIGNATURE_INDEX - 1, SECOND_SIGNATURE_INDEX));
    }
}
