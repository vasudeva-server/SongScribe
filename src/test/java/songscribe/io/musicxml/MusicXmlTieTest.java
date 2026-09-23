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

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Key;
import songscribe.dom.NoteElement;
import songscribe.dom.StaffElement;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.compactSong;
import static songscribe.io.XmlFixtures.element;
import static songscribe.io.XmlFixtures.emptyTag;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.EIGHTH_DURATION;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.MIDDLE_C_PITCH;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.middleCEighth;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.note;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.parse;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.roundTrip;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.scoreWithMeasureBody;

/**
 * A tie is note state, so unlike a span it survives a write and a read across a line break as
 * well as within a line. The reader ties only a note whose kind is a note: a start marker on a
 * rest or a grace note is ignored.
 */
class MusicXmlTieTest extends UnitTest {

    private static final int PITCH = 3;
    private static final int FIRST_LINE = 0;
    private static final int SECOND_LINE = 1;
    private static final int FIRST_ELEMENT = 0;
    private static final int SECOND_ELEMENT = 1;

    /** The {@code <tied type="start">} marker, inside {@code <notations>}. */
    private static final String TIED_START =
        element(MusicXmlTags.NOTATIONS, emptyTag(MusicXmlTags.TIED, MusicXmlTags.ATTR_TYPE, MusicXmlTags.TYPE_START));

    @Test
    void testATieWithinALineAndATieAcrossALineBreakSurviveAWriteAndARead() throws Exception {
        var song = compactSong(Key.NO_ACCIDENTALS, PITCH + " ~ " + PITCH, "" + PITCH);
        var firstLine = song.getLine(FIRST_LINE);
        var crossing = (NoteElement) firstLine.getElement(SECOND_ELEMENT);

        // The compact grammar ties only within a line, so the tie across the break is added here.
        song.withoutMutationTracking(() -> firstLine.addTie(crossing));

        var read = roundTrip(song);
        var readFirstLine = read.getLine(FIRST_LINE);
        var readAnchor = (NoteElement) readFirstLine.getElement(FIRST_ELEMENT);
        var readCrossing = (NoteElement) readFirstLine.getElement(SECOND_ELEMENT);
        var readNextLineFirst = (NoteElement) read.getLine(SECOND_LINE).getElement(FIRST_ELEMENT);

        assertThat(readAnchor.isTiedTo(readCrossing)).as("the tie within the line").isTrue();
        assertThat(readCrossing.isTiedTo(readNextLineFirst)).as("the tie across the line break").isTrue();
    }

    /**
     * A tie start marker on the measure's first note, followed by a middle-C eighth.
     *
     * @param description the case, as the test's display name
     * @param marked      the note markup that carries the start marker
     * @param kind        the kind of element {@code marked} is read as
     * @param tied        whether that element is tied
     */
    private record MarkerCase(String description, String marked, ElementType.Kind kind, boolean tied) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("markerCases")
    void testOnlyANoteCarryingATieStartIsTied(MarkerCase testCase) throws Exception {
        var song = parse(scoreWithMeasureBody(testCase.marked() + middleCEighth()));

        var first = (StaffElement) song.getLine(FIRST_LINE).getElement(FIRST_ELEMENT);

        assertThat(first.getType().kind()).as("the marked element is the one read first").isEqualTo(testCase.kind());
        assertThat(first instanceof NoteElement note && note.isTiedToNext()).isEqualTo(testCase.tied());
    }

    static Stream<MarkerCase> markerCases() {
        return Stream.of(
            new MarkerCase("a note", middleCEighth(TIED_START), ElementType.Kind.NOTE, true),
            new MarkerCase("a rest",
                note(element(MusicXmlTags.REST, ""), element(MusicXmlTags.DURATION, EIGHTH_DURATION),
                    element(MusicXmlTags.NOTE_TYPE, NoteTypeMapping.TYPE_EIGHTH), TIED_START),
                ElementType.Kind.REST, false),
            new MarkerCase("a grace note",
                note(element(MusicXmlTags.GRACE, ""), MIDDLE_C_PITCH,
                    element(MusicXmlTags.NOTE_TYPE, NoteTypeMapping.TYPE_EIGHTH), TIED_START),
                ElementType.Kind.GRACE_NOTE, false)
        );
    }
}
