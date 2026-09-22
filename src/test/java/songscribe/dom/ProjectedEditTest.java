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

import java.util.HashMap;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.StaffElementRun.ElementRange;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.compactSong;

/**
 * {@link ProjectedEdit}'s promise, stated on its class Javadoc: an edit's preview and its result
 * cannot disagree about a note. A projected walk, taken before anything commits, is compared
 * against the same edit committed for real, over every note: one the projection restamped holds
 * the state it predicted, and every other note keeps the state it had.
 */
class ProjectedEditTest extends UnitTest {

    private static final int F = 3;
    private static final int OTHER = 4;

    /** A song, the projected edit, and the same edit committed through the live route. */
    private record Fixture(Song song, ProjectedEdit edit, Runnable land) {}

    private record ProjectionCase(String description, Supplier<Fixture> arrange) {
        @Override
        public String toString() {
            return description;
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("projectionCases")
    void testAProjectedWalkAgreesWithWhatActuallyLandsWhenTheEditCommits(ProjectionCase testCase) {
        var fixture = testCase.arrange().get();
        var song = fixture.song();
        var expected = new HashMap<NoteElement, AccidentalState>();

        notesOf(Stream.concat(elementsOf(song), fixture.edit().elements().stream()))
            .forEach(note -> expected.put(note, note.accidentalState()));

        for (var restamp : fixture.edit().walk()) {
            expected.put(restamp.note(), restamp.state());
        }

        song.withModification(fixture.land());

        var landed = notesOf(elementsOf(song))
            .collect(Collectors.toMap(Function.identity(), NoteElement::accidentalState));

        // A note the edit removes is in the song before it and not after.
        expected.keySet().retainAll(landed.keySet());

        assertThat(landed)
            .as("every note holds the state the projection predicted for it, or kept its own where it predicted nothing")
            .isEqualTo(expected);
    }

    static Stream<ProjectionCase> projectionCases() {
        return Stream.of(
            new ProjectionCase("a note replaced by a key change", ProjectedEditTest::replacementCase),
            new ProjectionCase("a fragment holding a key change past its first element, inserted",
                ProjectedEditTest::fragmentWithKeyChangeCase));
    }

    private static Fixture replacementCase() {
        var song = compactSong(Key.NO_ACCIDENTALS, F + "f " + OTHER + " " + F);
        var line = song.getLine(0);
        var keyChange = StaffElementFactory.keyChange(Key.ONE_SHARP);

        return new Fixture(song,
            ProjectedEdit.replacing(line, ElementRange.single(1), List.of(keyChange)),
            () -> line.setElement(1, keyChange));
    }

    /**
     * The paste route: the projection walks the whole fragment as one edit, and the paste lands it
     * through {@link Line#insertRun}, one insertion at a time. The key the fragment brings in
     * moves notes after it on this line and on the line that inherits from it.
     */
    private static Fixture fragmentWithKeyChangeCase() {
        var song = compactSong(Key.NO_ACCIDENTALS, F + "f " + OTHER + " " + F, OTHER + " " + F);
        var line = song.getLine(0);
        var fragment = List.<StaffElement>of(
            StaffElementFactory.note(OTHER), StaffElementFactory.keyChange(Key.ONE_SHARP),
            StaffElementFactory.note(F, Accidental.NATURAL));

        return new Fixture(song,
            ProjectedEdit.inserting(line, 1, fragment),
            () -> line.insertRun(1, fragment));
    }

    private static Stream<StaffElement> elementsOf(Song song) {
        return song.getLines().stream().flatMap(line -> line.getElements().stream());
    }

    private static Stream<NoteElement> notesOf(Stream<? extends StaffElement> elements) {
        return elements.filter(NoteElement.class::isInstance).map(NoteElement.class::cast);
    }
}
