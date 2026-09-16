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

import java.util.function.Function;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.graceQuaver;

/**
 * What {@link StaffElementRun} promises about a note's real neighbors: a grace note is
 * transparent to {@link StaffElementRun#previousNonGraceIndex} and
 * {@link StaffElementRun#nextNonGraceIndex}, the element asked from is never its own answer, and
 * a direction holding nothing but grace notes, or nothing at all, has no neighbor to offer.
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

    /** The fixture line, by position: a grace note, a note, two grace notes, a note. */
    private static final int LEADING_GRACE = 0;
    private static final int FIRST_NOTE = 1;
    private static final int FIRST_INNER_GRACE = 2;
    private static final int SECOND_INNER_GRACE = 3;
    private static final int LAST_NOTE = 4;

    private Line line;

    @BeforeAll
    void buildLine() {
        line = detachedLine();
        line.addElement(graceQuaver());
        line.addElement(crotchet());
        line.addElement(graceQuaver());
        line.addElement(graceQuaver());
        line.addElement(crotchet());
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
}
