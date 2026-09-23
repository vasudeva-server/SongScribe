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

package songscribe.io.musicxml;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.audiveris.proxymusic.Note;
import org.audiveris.proxymusic.ScorePartwise;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.Beam;
import songscribe.dom.Dots;
import songscribe.dom.NoteElement;
import songscribe.dom.Song;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.buildSong;
import static songscribe.dom.StaffElementFactory.graceQuaver;
import static songscribe.dom.StaffElementFactory.quaver;
import static songscribe.dom.StaffElementFactory.semiquaver;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.build;

/**
 * The {@code <beam>} markers a beam group writes, one list per written note, in level order: the
 * primary beam runs begin, continue, end; a secondary level shared by a run of members does the
 * same over that run; a secondary level on one member alone is a hook; and a grace note inside the
 * group is not a member and carries none.
 */
class MusicXmlBeamTest extends UnitTest {

    private static final String BEGIN = "begin";
    private static final String CONTINUE = "continue";
    private static final String END = "end";
    private static final String FORWARD_HOOK = "forward hook";
    private static final String BACKWARD_HOOK = "backward hook";

    /**
     * One beam group over every note of a one-line song, and the markers each note writes.
     *
     * @param description the group, as the test's display name
     * @param notes       builds the line's notes, in order; the beam runs from the first to the last
     * @param markers     each written note's {@code <beam>} values, by level
     */
    private record BeamCase(String description, Supplier<List<NoteElement>> notes, List<List<String>> markers) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("beamCases")
    void testABeamGroupWritesEachLevelAsARunOrAHook(BeamCase testCase) {
        var notes = testCase.notes().get();
        var song = beamedSong(notes);

        assertThat(beamValuesByNote(build(song))).isEqualTo(testCase.markers());
    }

    static Stream<BeamCase> beamCases() {
        return Stream.of(
            new BeamCase("two quavers",
                () -> List.of(quaver(), quaver()),
                List.of(List.of(BEGIN), List.of(END))),
            new BeamCase("three quavers",
                () -> List.of(quaver(), quaver(), quaver()),
                List.of(List.of(BEGIN), List.of(CONTINUE), List.of(END))),
            new BeamCase("a grace note inside the group",
                () -> List.of(quaver(), graceQuaver(), quaver()),
                List.of(List.of(BEGIN), List.of(), List.of(END))),
            new BeamCase("two semiquavers share both levels",
                () -> List.of(semiquaver(), semiquaver()),
                List.of(List.of(BEGIN, BEGIN), List.of(END, END))),
            new BeamCase("a sub-beam over the last two members",
                () -> List.of(quaver(), semiquaver(), semiquaver()),
                List.of(List.of(BEGIN), List.of(CONTINUE, BEGIN), List.of(END, END))),
            new BeamCase("a lone semiquaver last hooks backward",
                () -> List.of(dottedQuaver(), semiquaver()),
                List.of(List.of(BEGIN), List.of(END, BACKWARD_HOOK))),
            new BeamCase("a lone semiquaver first hooks forward",
                () -> List.of(semiquaver(), dottedQuaver()),
                List.of(List.of(BEGIN, FORWARD_HOOK), List.of(END)))
        );
    }

    /** A one-line song holding {@code notes}, beamed from the first to the last. */
    private static Song beamedSong(List<NoteElement> notes) {
        return buildSong(line -> {
            notes.forEach(line::addElement);
            new Beam(notes.getFirst(), notes.getLast()).addTo(line);
        });
    }

    /** Every written note's {@code <beam>} values, in document order, each list ordered by level. */
    private static List<List<String>> beamValuesByNote(ScorePartwise score) {
        var values = new ArrayList<List<String>>();

        for (var part : score.getPart()) {
            for (var measure : part.getMeasure()) {
                for (var child : measure.getNoteOrBackupOrForward()) {
                    if (child instanceof Note note) {
                        values.add(note.getBeam().stream()
                            .sorted((first, second) -> Integer.compare(first.getNumber(), second.getNumber()))
                            .map(beam -> beam.getValue().value())
                            .toList());
                    }
                }
            }
        }

        return values;
    }

    private static NoteElement dottedQuaver() {
        var note = quaver();
        note.setDots(Dots.ONE);

        return note;
    }
}
