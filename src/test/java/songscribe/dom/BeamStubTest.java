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
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SpanFactory.beamOverAll;
import static songscribe.dom.StaffElementFactory.demiSemiquaver;
import static songscribe.dom.StaffElementFactory.quaver;
import static songscribe.dom.StaffElementFactory.semiquaver;

/**
 * The side a member's partial-beam stub is drawn on, {@link Beam.Member#stub()}: right for the
 * first member, left for the last, and for any other toward the neighbour with more beams, right
 * when both have the same number. A member whose beams are all shared has no stub.
 */
class BeamStubTest extends UnitTest {

    /**
     * A beamed group and the stub one member draws.
     *
     * @param description the case, as the test's display name
     * @param notes       makes the notes of the group, all of them beamed
     * @param member      the position of the member asked about
     * @param expected    the stub the member draws
     */
    private record StubCase(String description, Supplier<List<NoteElement>> notes, int member, Beam.Stub expected) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("stubCases")
    void testAStubIsDrawnTowardTheNeighbourWithMoreBeams(StubCase testCase) {
        var beam = beamOverAll(testCase.notes().get());

        assertThat(beam.members().get(testCase.member()).stub()).isEqualTo(testCase.expected());
    }

    @Test
    void testCasesCoverEveryStub() {
        assertThat(stubCases().map(StubCase::expected).distinct()).containsExactlyInAnyOrder(Beam.Stub.values());
    }

    static Stream<StubCase> stubCases() {
        return Stream.of(
            new StubCase("the first member's stub is drawn right",
                () -> List.of(semiquaver(), quaver(), quaver()), 0, Beam.Stub.RIGHT),
            new StubCase("the last member's stub is drawn left",
                () -> List.of(quaver(), quaver(), semiquaver()), 2, Beam.Stub.LEFT),
            new StubCase("an interior stub is drawn toward the neighbour on the right with more beams",
                () -> List.of(quaver(), demiSemiquaver(), semiquaver()), 1, Beam.Stub.RIGHT),
            new StubCase("an interior stub is drawn toward the neighbour on the left with more beams",
                () -> List.of(semiquaver(), demiSemiquaver(), quaver()), 1, Beam.Stub.LEFT),
            new StubCase("an interior stub between neighbours with the same number of beams is drawn right",
                () -> List.of(quaver(), semiquaver(), quaver()), 1, Beam.Stub.RIGHT),
            new StubCase("a member whose beams are all shared has no stub",
                () -> List.of(semiquaver(), semiquaver(), quaver()), 1, Beam.Stub.NONE),
            new StubCase("a member with only the primary beam has no stub",
                () -> List.of(quaver(), quaver(), quaver()), 1, Beam.Stub.NONE)
        );
    }
}
