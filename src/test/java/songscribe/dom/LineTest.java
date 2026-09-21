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
import java.util.function.Consumer;
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
 * of the run, the predecessor's against the first — and a glissando loses its target to an
 * insertion and to {@link Line#deleteRange} alike. {@link Line#canInsertElementAt} and
 * {@link Line#canBearSyllableAt} are the two questions every caller asks about a position, and
 * both turn on the closed pairs a line can hold. {@link Line#keyAt} and
 * {@link Line#keyAtEndOfLineAfterRemoving} read the key a position is in, and an append keeps the
 * song's terminal last.
 */
class LineTest extends UnitTest {

    /** The index the run lands at in every fixture here: after the line's first element. */
    private static final int RUN_INDEX = 1;

    private static final String SYLLABLE = "la";

    /**
     * One edit that changes which element follows the line's first note.
     *
     * @param description      the edit, as part of the test's display name
     * @param edit             performs it on a line holding the note and its follower
     * @param leavesNoteBehind whether a pitched note stands directly after the first note
     *     afterwards, by way of this edit's insertion
     */
    private record FollowerEdit(String description, Consumer<Line> edit, boolean leavesNoteBehind) {}

    /**
     * One slide on the first note, under one {@link FollowerEdit}.
     *
     * @param description the case, as the test's display name
     * @param slide       the slide the first note carries before the edit
     * @param edit        the edit that changes its follower
     */
    private record SlideCase(String description, Slide slide, FollowerEdit edit) {

        @Override
        public String toString() {
            return description;
        }
    }

    private static Stream<FollowerEdit> followerEdits() {
        return Stream.of(
            new FollowerEdit("a non-pitched run inserted after it",
                line -> insertRun(line, List.of(singleBarline(), keyChange(Key.NO_ACCIDENTALS))), false),
            new FollowerEdit("a pitched run inserted after it",
                line -> insertRun(line, List.of(crotchet(), crotchet())), true),
            new FollowerEdit("its follower deleted",
                line -> line.deleteRange(
                    line.effectiveRange(new StaffElementRun.ElementRange(RUN_INDEX, RUN_INDEX))),
                false));
    }

    /** Every {@link Slide} crossed with every {@link FollowerEdit}. */
    static Stream<SlideCase> slideCases() {
        return Stream.of(Slide.values())
            .flatMap(slide -> followerEdits()
                .map(edit -> new SlideCase(slide + ", " + edit.description(), slide, edit)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("slideCases")
    void testASlideSurvivesAChangeOfFollowerUnlessItsTargetIsGone(SlideCase testCase) {
        var line = detachedLine();
        var source = crotchet();
        source.setSlide(testCase.slide());
        line.addElement(source);
        line.addElement(crotchet());

        testCase.edit().edit().accept(line);

        // Slide's own contract: a glissando connects to the note that follows, a fall has no
        // target. Only a slide with a target can lose it.
        var hasTarget = switch (testCase.slide()) {
            case GLISSANDO -> true;
            case FALL -> false;
        };
        var expectedSlide = hasTarget && !testCase.edit().leavesNoteBehind() ? null : testCase.slide();

        assertThat(source.getSlide()).isEqualTo(expectedSlide);
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
        successor.putLyric(new Lyric(Lyric.FIRST_VERSE, SYLLABLE, Lyric.Extend.NONE, Lyric.Syllabic.MIDDLE, false));
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
        grace.setSlide(Slide.GLISSANDO);
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

    /** The keys of {@link #keyLine()}: the one it runs in, and the two its key changes establish. */
    private static final Key RUNNING_KEY = Key.NO_ACCIDENTALS;
    private static final Key FIRST_CHANGE_KEY = Key.TWO_SHARPS;
    private static final Key SECOND_CHANGE_KEY = Key.THREE_FLATS;

    /** {@link #keyLine()}, by position. */
    private static final int FIRST_NOTE = 0;
    private static final int FIRST_BARLINE = 1;
    private static final int FIRST_KEY_CHANGE = 2;
    private static final int NOTE_UNDER_FIRST_CHANGE = 3;
    private static final int SECOND_KEY_CHANGE = 5;
    private static final int LAST_NOTE = 6;

    /**
     * A line in {@link #RUNNING_KEY} holding {@code note, barline, key change, note, barline,
     * key change, note}, the key changes establishing {@link #FIRST_CHANGE_KEY} and then
     * {@link #SECOND_CHANGE_KEY}.
     */
    private static Line keyLine() {
        var line = detachedLine();
        line.setKey(RUNNING_KEY);
        line.addElement(crotchet());
        line.addElement(singleBarline());
        line.addElement(keyChange(FIRST_CHANGE_KEY));
        line.addElement(crotchet());
        line.addElement(singleBarline());
        line.addElement(keyChange(SECOND_CHANGE_KEY));
        line.addElement(crotchet());

        return line;
    }

    /**
     * One index on {@link #keyLine()} and the key in effect there.
     *
     * @param description the case, as the test's display name
     * @param index       the insertion index asked about
     * @param expected    the key in effect at it
     */
    private record KeyAtCase(String description, int index, Key expected) {}

    static Stream<KeyAtCase> keyAtCases() {
        return Stream.of(
            new KeyAtCase("index 0 is in the running key", FIRST_NOTE, RUNNING_KEY),
            new KeyAtCase("an index with no key change at or before it is in the running key",
                FIRST_BARLINE, RUNNING_KEY),
            new KeyAtCase("a key change is in effect at its own index", FIRST_KEY_CHANGE, FIRST_CHANGE_KEY),
            new KeyAtCase("a key change stays in effect past its index",
                NOTE_UNDER_FIRST_CHANGE, FIRST_CHANGE_KEY),
            new KeyAtCase("the insertion index past the last element is in the last key change's key",
                LAST_NOTE + 1, SECOND_CHANGE_KEY));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("keyAtCases")
    void testKeyAtIsTheLastKeyChangeAtOrBeforeTheIndexElseTheRunningKey(KeyAtCase testCase) {
        assertThat(keyLine().keyAt(testCase.index())).isEqualTo(testCase.expected());
    }

    /**
     * One range removed from {@link #keyLine()} and the key the line then leaves off in.
     *
     * @param description the case, as the test's display name
     * @param named       the range a caller names, before widening
     * @param expected    the key the line leaves off in without it
     */
    private record KeyAfterRemovingCase(String description, StaffElementRun.ElementRange named, Key expected) {}

    static Stream<KeyAfterRemovingCase> keyAfterRemovingCases() {
        return Stream.of(
            new KeyAfterRemovingCase("a key change surviving after the range decides",
                StaffElementRun.ElementRange.single(FIRST_KEY_CHANGE), SECOND_CHANGE_KEY),
            new KeyAfterRemovingCase("with none surviving, the key in effect before the range",
                StaffElementRun.ElementRange.single(SECOND_KEY_CHANGE), FIRST_CHANGE_KEY),
            new KeyAfterRemovingCase("a range from index 0 leaves the running key",
                new StaffElementRun.ElementRange(FIRST_NOTE, LAST_NOTE), RUNNING_KEY));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("keyAfterRemovingCases")
    void testKeyAtEndOfLineAfterRemovingReadsTheSurvivingKeyChangeElseTheKeyBeforeTheRange(
        KeyAfterRemovingCase testCase) {

        var line = keyLine();

        assertThat(line.keyAtEndOfLineAfterRemoving(line.effectiveRange(testCase.named())))
            .isEqualTo(testCase.expected());
    }

    @Test
    void testAppendingToTheLastLineOfASongKeepsTheTerminalLast() {
        var song = new Song();
        var line = song.getLine(song.lineCount() - 1);
        var terminal = line.getElement(line.elementCount() - 1);

        assertThat(song.isTerminalOfItsSong(terminal))
            .as("the fixture reaches the case only if the line carries its song's terminal")
            .isTrue();

        var appended = List.of(crotchet(), crotchet(), crotchet());

        song.withModification(() -> appended.forEach(line::addElement));

        var expected = new ArrayList<StaffElement>(appended);
        expected.add(terminal);

        assertThat(line.getElements(new StaffElementRun.ElementRange(0, line.elementCount() - 1)))
            .containsExactlyElementsOf(expected);
    }

    private static void insertRun(Line line, List<StaffElement> elements) {
        line.insertRun(RUN_INDEX, elements);
    }
}
