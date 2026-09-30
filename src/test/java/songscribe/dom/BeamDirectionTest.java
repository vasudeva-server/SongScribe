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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.StaffElementRun.ElementRange;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.buildSong;
import static songscribe.dom.SpanFactory.beamOver;
import static songscribe.dom.StaffElementFactory.graceQuaver;
import static songscribe.dom.StaffElementFactory.quaver;

/**
 * The direction {@link Beam#direction()} gives a group: the first forced stem decides it, and
 * otherwise the contour of the members does, unless the contour would bury a grace note's stem
 * under the beam.
 */
class BeamDirectionTest extends UnitTest {

    private static final int FIRST_LINE = 0;

    /** Staff positions count down from the middle line: negative is above it. */
    private static final int MIDDLE_LINE = 0;
    private static final int LOW_POSITION = 4;
    private static final int LOWER_POSITION = 6;
    private static final int HIGH_POSITION = -4;
    private static final int HIGHER_POSITION = -6;

    /**
     * One group of notes, in line order, and the direction its beam gives.
     *
     * @param description the group, as the test's display name
     * @param notes       makes the notes of the group; the first and the last are not grace notes
     * @param expected    the direction the beam gives
     */
    private record DirectionCase(String description, Supplier<List<NoteElement>> notes, Direction expected) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("directionCases")
    void testTheGroupDirectionFollowsTheForcedStemElseTheContour(DirectionCase testCase) {
        var notes = testCase.notes().get();
        var song = buildSong(line -> notes.forEach(line::addElement));
        var line = song.getLine(FIRST_LINE);
        var beam = song.withoutMutationTrackingResult(() -> {
            var built = beamOver(line, new ElementRange(0, notes.size() - 1));
            built.addTo(line);

            return built;
        });

        assertThat(beam.direction()).isEqualTo(testCase.expected());
    }

    static Stream<DirectionCase> directionCases() {
        return Stream.of(
            new DirectionCase("members below the middle line point up",
                () -> List.of(quaver(LOW_POSITION, Stem.AUTO), quaver(LOWER_POSITION, Stem.AUTO)),
                Direction.UP),
            new DirectionCase("members above the middle line point down",
                () -> List.of(quaver(HIGH_POSITION, Stem.AUTO), quaver(HIGHER_POSITION, Stem.AUTO)),
                Direction.DOWN),
            new DirectionCase("members on the middle line point down",
                () -> List.of(quaver(MIDDLE_LINE, Stem.AUTO), quaver(MIDDLE_LINE, Stem.AUTO)),
                Direction.DOWN),
            new DirectionCase("the first forced stem decides, against an upward contour",
                () -> List.of(
                    quaver(LOW_POSITION, Stem.AUTO),
                    quaver(LOW_POSITION, new Stem.Forced(Direction.DOWN)),
                    quaver(LOW_POSITION, new Stem.Forced(Direction.UP))),
                Direction.DOWN),
            new DirectionCase("the first forced stem decides, against a downward contour",
                () -> List.of(
                    quaver(HIGH_POSITION, Stem.AUTO),
                    quaver(HIGH_POSITION, new Stem.Forced(Direction.UP)),
                    quaver(HIGH_POSITION, new Stem.Forced(Direction.DOWN))),
                Direction.UP),
            new DirectionCase("a grace note higher than the members points an upward contour down",
                () -> List.of(
                    quaver(LOW_POSITION, Stem.AUTO),
                    graceQuaver(HIGH_POSITION),
                    quaver(LOWER_POSITION, Stem.AUTO)),
                Direction.DOWN),
            new DirectionCase("a grace note as low as the members leaves an upward contour up",
                () -> List.of(
                    quaver(LOW_POSITION, Stem.AUTO),
                    graceQuaver(LOWER_POSITION),
                    quaver(LOWER_POSITION, Stem.AUTO)),
                Direction.UP)
        );
    }
}
