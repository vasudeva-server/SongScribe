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
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.audiveris.proxymusic.BeamValue;
import org.audiveris.proxymusic.Note;
import org.audiveris.proxymusic.ScorePartwise;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.NoteElement;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SpanFactory.beamedSong;
import static songscribe.dom.StaffElementFactory.demiSemiquaver;
import static songscribe.dom.StaffElementFactory.dottedQuaver;
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

    /**
     * One beam group over every note of a one-line song, and the markers each note writes.
     *
     * @param description the group, as the test's display name
     * @param notes       builds the line's notes, in order; the beam runs from the first to the last
     * @param markers     each written note's {@code <beam>} values, keyed by the {@code number}
     *                    attribute that names the level
     */
    private record BeamCase(
        String description, Supplier<List<NoteElement>> notes, List<Map<Integer, BeamValue>> markers
    ) {}

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
                List.of(levels(BeamValue.BEGIN), levels(BeamValue.END))),
            new BeamCase("three quavers",
                () -> List.of(quaver(), quaver(), quaver()),
                List.of(levels(BeamValue.BEGIN), levels(BeamValue.CONTINUE), levels(BeamValue.END))),
            new BeamCase("a grace note inside the group",
                () -> List.of(quaver(), graceQuaver(), quaver()),
                List.of(levels(BeamValue.BEGIN), levels(), levels(BeamValue.END))),
            new BeamCase("two semiquavers share both levels",
                () -> List.of(semiquaver(), semiquaver()),
                List.of(levels(BeamValue.BEGIN, BeamValue.BEGIN), levels(BeamValue.END, BeamValue.END))),
            new BeamCase("two demisemiquavers share all three levels",
                () -> List.of(demiSemiquaver(), demiSemiquaver()),
                List.of(levels(BeamValue.BEGIN, BeamValue.BEGIN, BeamValue.BEGIN),
                    levels(BeamValue.END, BeamValue.END, BeamValue.END))),
            new BeamCase("a third-level sub-beam over the last two members",
                () -> List.of(quaver(), demiSemiquaver(), demiSemiquaver()),
                List.of(levels(BeamValue.BEGIN),
                    levels(BeamValue.CONTINUE, BeamValue.BEGIN, BeamValue.BEGIN),
                    levels(BeamValue.END, BeamValue.END, BeamValue.END))),
            new BeamCase("a sub-beam over the last two members",
                () -> List.of(quaver(), semiquaver(), semiquaver()),
                List.of(levels(BeamValue.BEGIN),
                    levels(BeamValue.CONTINUE, BeamValue.BEGIN),
                    levels(BeamValue.END, BeamValue.END))),
            new BeamCase("a lone semiquaver last hooks backward",
                () -> List.of(dottedQuaver(), semiquaver()),
                List.of(levels(BeamValue.BEGIN), levels(BeamValue.END, BeamValue.BACKWARD_HOOK))),
            new BeamCase("a lone semiquaver first hooks forward",
                () -> List.of(semiquaver(), dottedQuaver()),
                List.of(levels(BeamValue.BEGIN, BeamValue.FORWARD_HOOK), levels(BeamValue.END)))
        );
    }

    /** The {@code <beam>} values one note writes, {@code values[0]} on level 1, the next on level 2. */
    private static Map<Integer, BeamValue> levels(BeamValue... values) {
        var byLevel = new TreeMap<Integer, BeamValue>();

        for (var i = 0; i < values.length; i++) {
            byLevel.put(i + 1, values[i]);
        }

        return byLevel;
    }

    /** Every written note's {@code <beam>} values, in document order, each keyed by its {@code number}. */
    private static List<Map<Integer, BeamValue>> beamValuesByNote(ScorePartwise score) {
        var values = new ArrayList<Map<Integer, BeamValue>>();

        for (var part : score.getPart()) {
            for (var measure : part.getMeasure()) {
                for (var child : measure.getNoteOrBackupOrForward()) {
                    if (child instanceof Note note) {
                        var byNumber = new TreeMap<Integer, BeamValue>();

                        for (var beam : note.getBeam()) {
                            byNumber.put(beam.getNumber(), beam.getValue());
                        }

                        values.add(byNumber);
                    }
                }
            }
        }

        return values;
    }
}
