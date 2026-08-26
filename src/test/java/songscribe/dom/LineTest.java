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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.graceQuaver;
import static songscribe.dom.StaffElementFactory.keyChange;
import static songscribe.dom.StaffElementFactory.singleBarline;

/**
 * What {@link Line} promises about a position and about a run landing at one.
 *
 * <p>{@link Line#insertRun} repairs both of its seams — the successor's against the last element
 * of the run, the predecessor's against the first. {@link Line#canInsertElementAt} and
 * {@link Line#canBearSyllableAt} are the two questions every caller asks about a position, and
 * both turn on the closed pairs a line can hold.
 */
class LineTest extends UnitTest {

    /** The index the run lands at in every fixture here: after the line's first element. */
    private static final int RUN_INDEX = 1;

    private static final double RUN_POSITION_SS = 0.0;

    private static final int NO_TAIL_SHIFT_PX = 0;

    private static final String SYLLABLE = "la";

    @Test
    void testANonPitchedRunStripsThePredecessorsOrphanedGlissando() {
        var line = detachedLine();
        var source = crotchet();
        source.setGlissando();
        line.addElement(source);
        line.addElement(crotchet());

        insertRun(line, List.of(singleBarline(), keyChange(Key.NO_ACCIDENTALS)));

        assertThat(line.getElement(0).hasGlissando())
            .as("the glissando's target is no longer the element after it, so it cannot survive")
            .isFalse();
    }

    @Test
    void testAPitchedRunLandingInsideAGraceHostPairRemakesTheMelismaAgainstItsFirstElement() {
        var line = detachedLine();
        var grace = graceQuaver();
        grace.setGlissando();
        grace.setLyricForVerse(Lyric.FIRST_VERSE, Lyric.Syllabic.SINGLE, false, SYLLABLE, Lyric.Extend.NONE);
        line.addElement(grace);
        line.addElement(crotchet());

        insertRun(line, List.of(crotchet(), crotchet()));

        var graceLyric = line.getElement(0).getLyricForVerse(Lyric.FIRST_VERSE);
        var newHostLyric = line.getElement(RUN_INDEX).getLyricForVerse(Lyric.FIRST_VERSE);

        assertThat(graceLyric).isNotNull();
        assertThat(graceLyric.extend())
            .as("the grace note carries the syllable, so its melisma runs across its new host")
            .isEqualTo(Lyric.Extend.START);

        assertThat(newHostLyric).isNotNull();
        assertThat(newHostLyric.extend())
            .as("the first element of the run is the new host, so it takes the carrier")
            .isEqualTo(Lyric.Extend.STOP);
    }

    /**
     * One run inserted between a syllable and the syllable that continues its word, and what that
     * leaves the successor reading as.
     *
     * @param description      the case, as the test's display name
     * @param run              the elements inserted, in the order they land
     * @param expectedSyllabic what the successor's syllable then is
     */
    private record InsertedRunCase(
        String description,
        List<StaffElement> run,
        Lyric.Syllabic expectedSyllabic
    ) {}

    static Stream<InsertedRunCase> insertedRunCases() {
        return Stream.of(
            // Blank notes are syllable slots arriving empty, so the word cannot be sung as one.
            new InsertedRunCase("a run of notes interrupts the word",
                List.of(crotchet(), crotchet()), Lyric.Syllabic.BEGIN),
            // The discriminating case: judged element by element, one interrupting element in the
            // run is enough. A run judged by its every element would leave this one transparent.
            new InsertedRunCase("a run mixing a transparent element with a note interrupts the word",
                List.of(singleBarline(), crotchet()), Lyric.Syllabic.BEGIN),
            new InsertedRunCase("a run carrying no syllable slot leaves the word whole",
                List.of(singleBarline(), keyChange(Key.NO_ACCIDENTALS)), Lyric.Syllabic.MIDDLE));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("insertedRunCases")
    void testTheSuccessorsSyllabicChainBreaksOnlyForARunHoldingASyllableSlot(InsertedRunCase testCase) {
        var line = detachedLine();
        line.addElement(crotchet());

        var successor = crotchet();
        successor.setLyricForVerse(Lyric.FIRST_VERSE, Lyric.Syllabic.MIDDLE, false, SYLLABLE, Lyric.Extend.NONE);
        line.addElement(successor);

        insertRun(line, testCase.run());

        var successorLyric = successor.getLyricForVerse(Lyric.FIRST_VERSE);

        assertThat(successorLyric).isNotNull();
        assertThat(successorLyric.syllabic()).isEqualTo(testCase.expectedSyllabic());
    }

    /**
     * One index on {@link #pairedLine()}, and what the line's two position questions answer there.
     *
     * @param description       the case, as the test's display name
     * @param index             the index being asked about
     * @param canInsert         whether an element may be inserted in front of the element there
     * @param canBearSyllable   whether a syllable may be written on the element there
     */
    private record PositionCase(
        String description,
        int index,
        boolean canInsert,
        boolean canBearSyllable
    ) {}

    /**
     * A line holding both closed pairs: a key change behind its barline at 1–2, and a grace
     * note with the note it decorates at 4–5.
     */
    private static Line pairedLine() {
        var line = detachedLine();
        line.addElement(crotchet());
        line.addElement(singleBarline());
        line.addElement(keyChange(Key.TWO_SHARPS));
        line.addElement(crotchet());

        var grace = graceQuaver();
        grace.setGlissando();
        line.addElement(grace);
        line.addElement(crotchet());

        return line;
    }

    static Stream<PositionCase> positionCases() {
        return Stream.of(
            new PositionCase("a note takes an insertion in front of it and a syllable on it",
                0, true, true),
            new PositionCase("the barline a key change stands behind takes an insertion, not a syllable",
                1, true, false),
            new PositionCase("the slot in front of a key change is inside the pair",
                2, false, false),
            new PositionCase("the note after the pair is clear of it",
                3, true, true),
            new PositionCase("the slot in front of a grace note is outside the pair, and the grace carries the syllable",
                4, true, true),
            new PositionCase("the slot in front of a paired grace note's host is inside the pair, and the host takes no syllable",
                5, false, false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("positionCases")
    void testCanInsertElementAtRefusesOnlyTheSlotsInsideAPair(PositionCase testCase) {
        assertThat(pairedLine().canInsertElementAt(testCase.index()))
            .isEqualTo(testCase.canInsert());
    }

    @Test
    void testCanInsertElementAtAcceptsTheSlotPastTheLastElement() {
        var line = pairedLine();

        assertThat(line.canInsertElementAt(line.elementCount()))
            .as("nothing stands past the last element, so there is no pair there to split")
            .isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("positionCases")
    void testCanBearSyllableAtRefusesEveryElementNoSyllableCanBeSungOn(PositionCase testCase) {
        assertThat(pairedLine().canBearSyllableAt(testCase.index()))
            .isEqualTo(testCase.canBearSyllable());
    }

    private static void insertRun(Line line, List<StaffElement> elements) {
        line.insertRun(
            RUN_INDEX,
            elements.stream().map(element -> new Line.PlacedElement(element, RUN_POSITION_SS)).toList(),
            NO_TAIL_SHIFT_PX);
    }
}
