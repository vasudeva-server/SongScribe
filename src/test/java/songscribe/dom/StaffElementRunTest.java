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
import java.util.function.Function;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.breathMark;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.crotchetRest;
import static songscribe.dom.StaffElementFactory.graceQuaver;
import static songscribe.dom.StaffElementFactory.singleBarline;

/**
 * What {@link StaffElementRun} promises about a note's real neighbors and about where content
 * may land on a run: a grace note is transparent to {@link StaffElementRun#previousNonGraceIndex},
 * {@link StaffElementRun#nextNonGraceIndex} and {@link StaffElementRun#nonGraceRange}, the
 * element asked from is never its own answer, and a direction — or a range — holding nothing but
 * grace notes has no neighbor, or no narrowed range, to offer. {@link StaffElementRun#canInsertRunAt}
 * composes the breath-mark placement rule for a run's two ends with the single-element insertion
 * rule it otherwise defers to.
 */
class StaffElementRunTest extends UnitTest {

    /**
     * One neighbor lookup and the index it must answer.
     *
     * @param description the case, as the test's display name
     * @param query       the lookup, bound to the index it asks from
     * @param expected    the neighbor's index, or {@code null} when the run offers none
     */
    private record NeighborCase(
        String description, Function<StaffElementRun, @Nullable Integer> query, @Nullable Integer expected
    ) {}

    /** The fixture line, by position: a grace note, a note, two grace notes, a note, a grace note. */
    private static final int LEADING_GRACE = 0;
    private static final int FIRST_NOTE = 1;
    private static final int FIRST_INNER_GRACE = 2;
    private static final int SECOND_INNER_GRACE = 3;
    private static final int LAST_NOTE = 4;
    private static final int TRAILING_GRACE = 5;

    /**
     * The fixture line for {@link StaffElementRun#canInsertRunAt}, by position: a note, a rest, a
     * barline, a note, a note, a breath mark, a note, a grace note paired with the note after it,
     * and that note — the host of the pair.
     */
    private static final int AFTER_REST = 2;
    private static final int AFTER_BARLINE = 3;
    private static final int AFTER_NOTE = 4;
    private static final int AT_EXISTING_BREATH_MARK = 5;
    private static final int GRACE_PAIR_HOST = 8;

    private Line line;
    private Line insertRunLine;

    @BeforeAll
    void buildLine() {
        line = detachedLine();
        line.addElement(graceQuaver());
        line.addElement(crotchet());
        line.addElement(graceQuaver());
        line.addElement(graceQuaver());
        line.addElement(crotchet());
        line.addElement(graceQuaver());
    }

    @BeforeAll
    void buildInsertRunLine() {
        insertRunLine = detachedLine();
        insertRunLine.addElement(crotchet());
        insertRunLine.addElement(crotchetRest());
        insertRunLine.addElement(singleBarline());
        insertRunLine.addElement(crotchet());
        insertRunLine.addElement(crotchet());
        insertRunLine.addElement(breathMark());
        insertRunLine.addElement(crotchet());

        var pairedGrace = graceQuaver();
        pairedGrace.setSlide(Slide.GLISSANDO);
        insertRunLine.addElement(pairedGrace);
        insertRunLine.addElement(crotchet());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("neighborCases")
    void testTheNearestNonGraceNeighborStepsOverGraceNotesAndIsAbsentPastTheEnds(NeighborCase testCase) {
        assertThat(testCase.query().apply(line)).isEqualTo(testCase.expected());
    }

    static Stream<NeighborCase> neighborCases() {
        return Stream.of(
            new NeighborCase("a run of grace notes before is stepped over",
                run -> run.previousNonGraceIndex(LAST_NOTE), FIRST_NOTE),
            new NeighborCase("a single grace note before is stepped over",
                run -> run.previousNonGraceIndex(SECOND_INNER_GRACE), FIRST_NOTE),
            new NeighborCase("only a grace note before leaves no predecessor",
                run -> run.previousNonGraceIndex(FIRST_NOTE), null),
            new NeighborCase("nothing before leaves no predecessor",
                run -> run.previousNonGraceIndex(LEADING_GRACE), null),
            new NeighborCase("a run of grace notes after is stepped over",
                run -> run.nextNonGraceIndex(FIRST_NOTE), LAST_NOTE),
            new NeighborCase("a single grace note after is stepped over",
                run -> run.nextNonGraceIndex(FIRST_INNER_GRACE), LAST_NOTE),
            new NeighborCase("the element asked from is not its own successor",
                run -> run.nextNonGraceIndex(LEADING_GRACE), FIRST_NOTE),
            new NeighborCase("nothing after leaves no successor",
                run -> run.nextNonGraceIndex(LAST_NOTE), null)
        );
    }

    /**
     * One {@link StaffElementRun#nonGraceRange} call and the range it must narrow to.
     *
     * @param description the case, as the test's display name
     * @param range        the range to narrow
     * @param expected     the narrowed range, or {@code null} when {@code range} holds only
     *                     grace notes
     */
    private record NonGraceRangeCase(
        String description, StaffElementRun.ElementRange range, StaffElementRun.@Nullable ElementRange expected
    ) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonGraceRangeCases")
    void testNonGraceRangeNarrowsToTheNonGraceEndpoints(NonGraceRangeCase testCase) {
        assertThat(line.nonGraceRange(testCase.range())).isEqualTo(testCase.expected());
    }

    static Stream<NonGraceRangeCase> nonGraceRangeCases() {
        return Stream.of(
            new NonGraceRangeCase("grace notes at both ends are excluded",
                new StaffElementRun.ElementRange(LEADING_GRACE, TRAILING_GRACE),
                new StaffElementRun.ElementRange(FIRST_NOTE, LAST_NOTE)),
            new NonGraceRangeCase("a range of only grace notes yields null",
                new StaffElementRun.ElementRange(FIRST_INNER_GRACE, SECOND_INNER_GRACE), null),
            new NonGraceRangeCase("a range with one non-grace element yields that single",
                new StaffElementRun.ElementRange(SECOND_INNER_GRACE, LAST_NOTE),
                StaffElementRun.ElementRange.single(LAST_NOTE))
        );
    }

    /**
     * One {@link StaffElementRun#canInsertRunAt} call and the answer it must give.
     *
     * @param description the case, as the test's display name
     * @param index        the slot the run would be inserted at
     * @param run          the elements that would land there
     * @param expected     whether the run may be inserted
     */
    private record InsertRunCase(String description, int index, List<? extends StaffElement> run, boolean expected) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("insertRunCases")
    void testCanInsertRunAtComposesInsertionAndBreathMarkRules(InsertRunCase testCase) {
        assertThat(insertRunLine.canInsertRunAt(testCase.index(), testCase.run())).isEqualTo(testCase.expected());
    }

    static Stream<InsertRunCase> insertRunCases() {
        return Stream.of(
            new InsertRunCase("a breath mark leading a run after a barline is refused",
                AFTER_BARLINE, List.of(breathMark()), false),
            new InsertRunCase("a breath mark leading a run at index 0 is refused",
                0, List.of(breathMark()), false),
            new InsertRunCase("a breath mark leading a run in front of a breath mark is refused",
                AT_EXISTING_BREATH_MARK, List.of(breathMark()), false),
            new InsertRunCase("a breath mark leading a run after a note is accepted",
                AFTER_NOTE, List.of(breathMark()), true),
            new InsertRunCase("a breath mark leading a run after a rest is accepted",
                AFTER_REST, List.of(breathMark()), true),
            new InsertRunCase("a run ending in a barline in front of a breath mark is refused",
                AT_EXISTING_BREATH_MARK, List.of(singleBarline()), false),
            new InsertRunCase("a run ending in a note in front of a breath mark is accepted",
                AT_EXISTING_BREATH_MARK, List.of(crotchet()), true),
            new InsertRunCase("a run holding a note is refused inside a grace pair",
                GRACE_PAIR_HOST, List.of(crotchet()), false),
            new InsertRunCase("a run holding a breath mark is refused inside a grace pair",
                GRACE_PAIR_HOST, List.of(breathMark()), false)
        );
    }
}
