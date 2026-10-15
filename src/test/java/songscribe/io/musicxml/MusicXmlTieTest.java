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

import java.util.List;
import java.util.stream.Stream;

import org.audiveris.proxymusic.Note;
import org.audiveris.proxymusic.StartStop;
import org.audiveris.proxymusic.Tied;
import org.audiveris.proxymusic.TiedType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.Beam;
import songscribe.dom.ElementType;
import songscribe.dom.Key;
import songscribe.dom.NoteElement;
import songscribe.dom.LineRange;
import songscribe.dom.ReportedChange;
import songscribe.dom.StaffElement;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.compactSong;
import static songscribe.io.XmlFixtures.closeTag;
import static songscribe.io.XmlFixtures.element;
import static songscribe.io.XmlFixtures.emptyTag;
import static songscribe.io.XmlFixtures.openTag;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.build;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.parseResult;
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
 * rest or a grace note is dropped and reported. A beam that joins the two notes of a tie is
 * dropped too, and the tie stays.
 */
class MusicXmlTieTest extends UnitTest {

    private static final int PITCH = 3;
    private static final int FIRST_LINE = 0;
    private static final int SECOND_LINE = 1;
    private static final int FIRST_ELEMENT = 0;
    private static final int SECOND_ELEMENT = 1;

    /** The {@code <tied type="start">} marker, inside {@code <notations>}. */
    private static final String TIED_START =
        element(MusicXmlRoundTripSupport.NOTATIONS, emptyTag(MusicXmlRoundTripSupport.TIED, MusicXmlRoundTripSupport.ATTR_TYPE, MusicXmlRoundTripSupport.TYPE_START));

    @Test
    void testATieWithinALineAndATieAcrossALineBreakSurviveAWriteAndARead() throws Exception {
        var song = compactSong(Key.NO_ACCIDENTALS, PITCH + " ~ " + PITCH + " ~", PITCH + " " + PITCH);

        var read = roundTrip(song);
        var readFirstLine = read.getLine(FIRST_LINE);
        var readAnchor = (NoteElement) readFirstLine.getElement(FIRST_ELEMENT);
        var readCrossing = (NoteElement) readFirstLine.getElement(SECOND_ELEMENT);
        var readNextLineFirst = (NoteElement) read.getLine(SECOND_LINE).getElement(FIRST_ELEMENT);
        var readControl = (NoteElement) read.getLine(SECOND_LINE).getElement(SECOND_ELEMENT);

        assertThat(readAnchor.isTiedTo(readCrossing)).as("the tie within the line").isTrue();
        assertThat(readCrossing.isTiedTo(readNextLineFirst)).as("the tie across the line break").isTrue();
        assertThat(readNextLineFirst.isTiedToNext()).as("the note the chain ends on").isFalse();
        assertThat(readControl.isTiedToNext()).as("the untied note after the chain").isFalse();
        assertThat(readNextLineFirst.isTiedTo(readControl)).as("no tie runs on to the control note").isFalse();
    }

    @Test
    void testTheWriterEmitsATieAndATiedForEachEndOfATieChain() {
        var song = compactSong(Key.NO_ACCIDENTALS, PITCH + " ~ " + PITCH + " ~ " + PITCH + " " + PITCH);

        var notes = build(song).getPart().stream()
            .flatMap(part -> part.getMeasure().stream())
            .flatMap(measure -> measure.getNoteOrBackupOrForward().stream())
            .filter(Note.class::isInstance)
            .map(Note.class::cast)
            .toList();

        assertThat(notes.stream().map(note -> note.getTie().stream().map(tie -> tie.getType()).toList()).toList())
            .as("the <tie> markers of each note: a chain's middle note stops one tie and starts the next")
            .containsExactly(
                List.of(StartStop.START),
                List.of(StartStop.STOP, StartStop.START),
                List.of(StartStop.STOP),
                List.of());
        assertThat(notes.stream().map(MusicXmlTieTest::tiedTypes).toList())
            .as("the <tied> markers of each note")
            .containsExactly(
                List.of(TiedType.START),
                List.of(TiedType.STOP, TiedType.START),
                List.of(TiedType.STOP),
                List.of());
    }

    private static List<TiedType> tiedTypes(Note note) {
        return note.getNotations().stream()
            .flatMap(notations -> notations.getTiedOrSlurOrTuplet().stream())
            .filter(Tied.class::isInstance)
            .map(Tied.class::cast)
            .map(tied -> tied.getType())
            .toList();
    }

    @Test
    void testAFileThatBeamsTheTwoNotesOfATieLoadsWithTheTieAndWithoutTheBeam() throws Exception {
        var markup = middleCEighth(beam(MusicXmlRoundTripSupport.BEAM_BEGIN), TIED_START) + middleCEighth(beam(MusicXmlRoundTripSupport.BEAM_END));

        var result = parseResult(scoreWithMeasureBody(markup));
        var line = result.song().getLine(FIRST_LINE);
        var anchor = (NoteElement) line.getElement(FIRST_ELEMENT);
        var end = (NoteElement) line.getElement(SECOND_ELEMENT);

        assertThat(anchor.isTiedTo(end)).as("the tie stays").isTrue();
        assertThat(line.spanRegistry().findSpans(Beam.class)).as("the beam goes").isEmpty();
        assertThat(result.report().changes()).containsExactly(new ReportedChange(
            Strings.REPORT_BEAMS_REMOVED,
            Strings.get(Strings.REPORT_REASON_BEAM_TIED_NOTES),
            new LineRange(FIRST_LINE, FIRST_LINE)));
    }

    /** The primary {@code <beam number="1">} marker with {@code beamValue}. */
    private static String beam(String beamValue) {
        return openTag(MusicXmlRoundTripSupport.BEAM, MusicXmlRoundTripSupport.ATTR_NUMBER, MusicXmlTags.NUMBER_1)
            + beamValue + closeTag(MusicXmlRoundTripSupport.BEAM);
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
                note(element(MusicXmlRoundTripSupport.REST, ""), element(MusicXmlRoundTripSupport.DURATION, EIGHTH_DURATION),
                    element(MusicXmlRoundTripSupport.NOTE_TYPE, NoteTypeMapping.TYPE_EIGHTH), TIED_START),
                ElementType.Kind.REST, false),
            new MarkerCase("a grace note",
                note(element(MusicXmlRoundTripSupport.GRACE, ""), MIDDLE_C_PITCH,
                    element(MusicXmlRoundTripSupport.NOTE_TYPE, NoteTypeMapping.TYPE_EIGHTH), TIED_START),
                ElementType.Kind.GRACE_NOTE, false)
        );
    }
}
