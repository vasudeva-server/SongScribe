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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.graceQuaver;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.buildSong;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.parse;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.roundTrip;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.scoreWithMeasureBody;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.writeToString;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Duration;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;

/**
 * Round-trip coverage for tempo {@code <direction>} / {@code <metronome>}
 * emission and parsing. The song tempo ({@link Song#getTempo()}) is a
 * property of the score, not of any note: it is emitted exactly once, as the
 * first child of measure 1, bound to no {@code <note>}, and is read back the
 * same way — the first tempo {@code <direction>} in the first {@code
 * <measure>} sets {@code song.tempo} and produces no {@link
 * TempoChangeAttachment}. A {@code TempoChangeAttachment} in written or
 * parsed XML is always a genuine per-note tempo change, never a mirror of the
 * song tempo.
 */
class MusicXmlTempoRoundTripTest extends UnitTest {

    // Visible tempo (BPM) values — each distinct so a mix-up is caught.
    private static final int BASE_TEMPO_BPM = 120;
    private static final int PER_NOTE_TEMPO_BPM = 90;
    private static final int HIDDEN_TEMPO_BPM = 100;
    private static final int DOTTED_TEMPO_BPM = 76;
    private static final int DESCRIBED_TEMPO_BPM = 138;

    // No tempo description → no <words> emitted → recovered as the empty string.
    private static final String NO_DESCRIPTION = "";
    private static final String TEMPO_DESCRIPTION = "Allegro vivace";

    // Beat-unit token used by the hand-authored old-format tempo directions,
    // matching Duration.CROTCHET (BeatUnitMapping's mapping for it).
    private static final String QUARTER_BEAT_UNIT_TOKEN = "quarter";

    // Element index of the distinct mid-song per-note tempo (0 and 1 hold the
    // base-tempo note and a plain note).
    private static final int PER_NOTE_INDEX = 2;

    // A two-line song exercises tempo directions across a system break.
    private static final int TWO_LINE_COUNT = 2;
    private static final int SECOND_LINE_INDEX = 1;

    // -------------------------------------------------------------------------
    // Assertion helpers
    // -------------------------------------------------------------------------

    private static void assertTempoEquals(
            @Nullable Tempo actual,
            int expectedVisibleTempo,
            Duration expectedType,
            String expectedDescription,
            boolean expectedShow,
            String context) {
        assertThat(actual).as("%s: present", context).isNotNull();

        assertThat(actual.getVisibleTempo()).as("%s: visibleTempo", context).isEqualTo(expectedVisibleTempo);
        assertThat(actual.getTempoType()).as("%s: tempoType", context).isEqualTo(expectedType);
        assertThat(actual.getTempoDescription()).as("%s: description", context).isEqualTo(expectedDescription);
        assertThat(actual.shouldShowTempo()).as("%s: shouldShowTempo", context).isEqualTo(expectedShow);
    }

    private static @Nullable Tempo tempoOf(Line line, int elementIndex) {
        var attachment = line.getElement(elementIndex).findAttachment(TempoChangeAttachment.class);
        return attachment == null ? null : attachment.getTempo();
    }

    /**
     * Attaches {@code tempo} to {@code element} as a genuine per-note tempo
     * change (never a mirror of the song tempo — that invariant is gone).
     */
    private static void attachTempo(StaffElement element, Tempo tempo) {
        element.addAttachment(new TempoChangeAttachment(element, tempo));
    }

    /**
     * Builds a single-line, two-note song and sets {@code tempo} as the song's
     * tempo. Neither note carries a {@link TempoChangeAttachment} — the song
     * tempo is a property of the song alone.
     */
    private Song buildSongWithTempo(Tempo tempo) {
        var song = buildSong(line -> {
            line.addElement(crotchet());
            line.addElement(crotchet());
        });

        song.withoutMutationTracking(() -> song.setTempo(tempo));
        return song;
    }

    /**
     * A hand-authored old-format tempo {@code <direction>}: the minimal
     * beat-unit form ({@code <beat-unit>} + {@code <per-minute>}), matching
     * what {@code MusicXmlDirectionWriter.writeTempoDirection} emits for a
     * plain (undescribed, visible) {@link Duration#CROTCHET} tempo.
     */
    private static String tempoDirectionXml(int bpm) {
        return
            "      <" + MusicXmlTags.DIRECTION + ">\n" +
            "        <" + MusicXmlTags.DIRECTION_TYPE + "><" + MusicXmlTags.METRONOME + ">" +
                "<" + MusicXmlTags.BEAT_UNIT + ">" + QUARTER_BEAT_UNIT_TOKEN + "</" + MusicXmlTags.BEAT_UNIT + ">" +
                "<" + MusicXmlTags.PER_MINUTE + ">" + bpm + "</" + MusicXmlTags.PER_MINUTE + ">" +
                "</" + MusicXmlTags.METRONOME + "></" + MusicXmlTags.DIRECTION_TYPE + ">\n" +
            "        <" + MusicXmlTags.SOUND + " " + MusicXmlTags.ATTR_TEMPO + "=\"" + bpm + "\"/>\n" +
            "      </" + MusicXmlTags.DIRECTION + ">\n";
    }

    /** A minimal plain {@code <note>}, for use after {@link #tempoDirectionXml}. */
    private static String noteXml() {
        return
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>480</duration>\n" +
            "        <type>quarter</type>\n" +
            "      </note>\n";
    }

    // -------------------------------------------------------------------------
    // Round-trip tests
    // -------------------------------------------------------------------------

    @Test
    void testDefaultTempoIsWrittenAndRoundTrips() throws Exception {
        // Song.tempo is non-null and seeded with Tempo's defaults, so even a
        // song whose tempo was never touched still emits and recovers one.
        var song = buildSong(line -> line.addElement(crotchet()));

        var xml = writeToString(song);
        assertThat(xml).as("default tempo <sound tempo> is written").contains("<" + MusicXmlTags.SOUND + " " + MusicXmlTags.ATTR_TEMPO);

        var reloaded = roundTrip(song);

        assertTempoEquals(
            reloaded.getTempo(),
            Tempo.DEFAULT_BPM, Tempo.DEFAULT_TYPE, Tempo.DEFAULT_DESCRIPTION, Tempo.DEFAULT_SHOW_TEMPO,
            "default song tempo");
    }

    @Test
    void testBaseTempoOnlyRoundTrips() throws Exception {
        var baseTempo = new Tempo(BASE_TEMPO_BPM, Duration.CROTCHET, NO_DESCRIPTION, true);
        var song = buildSongWithTempo(baseTempo);

        var reloaded = roundTrip(song);
        var line = reloaded.getLine(0);

        assertTempoEquals(reloaded.getTempo(), BASE_TEMPO_BPM, Duration.CROTCHET, NO_DESCRIPTION, true, "song base tempo");
        assertThat(tempoOf(line, 0)).as("first note carries no tempo attachment").isNull();
        assertThat(tempoOf(line, 1)).as("second note carries no tempo attachment").isNull();
    }

    @Test
    void testBaseAndDistinctPerNoteTempoRoundTrip() throws Exception {
        var baseTempo = new Tempo(BASE_TEMPO_BPM, Duration.CROTCHET, NO_DESCRIPTION, true);
        var perNoteTempo = new Tempo(PER_NOTE_TEMPO_BPM, Duration.MINIM, NO_DESCRIPTION, true);

        var song = buildSong(line -> {
            var note0 = crotchet();
            var note1 = crotchet();
            var note2 = crotchet();
            line.addElement(note0);
            line.addElement(note1);
            line.addElement(note2);
            attachTempo(note2, perNoteTempo);
        });
        song.withoutMutationTracking(() -> song.setTempo(baseTempo));

        var reloaded = roundTrip(song);
        var line = reloaded.getLine(0);

        assertTempoEquals(reloaded.getTempo(), BASE_TEMPO_BPM, Duration.CROTCHET, NO_DESCRIPTION, true, "song base tempo");
        assertThat(tempoOf(line, 0)).as("first note carries no tempo attachment").isNull();
        assertThat(tempoOf(line, 1)).as("middle note carries no tempo").isNull();
        assertTempoEquals(
            tempoOf(line, PER_NOTE_INDEX),
            PER_NOTE_TEMPO_BPM, Duration.MINIM, NO_DESCRIPTION, true, "per-note tempo");
    }

    @Test
    void testHiddenTempoRoundTrips() throws Exception {
        var hiddenTempo = new Tempo(HIDDEN_TEMPO_BPM, Duration.CROTCHET, NO_DESCRIPTION, false);
        var song = buildSongWithTempo(hiddenTempo);

        var reloaded = roundTrip(song);
        var line = reloaded.getLine(0);

        assertTempoEquals(reloaded.getTempo(), HIDDEN_TEMPO_BPM, Duration.CROTCHET, NO_DESCRIPTION, false, "hidden base tempo");
        assertThat(tempoOf(line, 0)).as("first note carries no tempo attachment").isNull();
    }

    @Test
    void testDottedBeatUnitRoundTrips() throws Exception {
        var dottedTempo = new Tempo(DOTTED_TEMPO_BPM, Duration.CROTCHET_DOTTED, NO_DESCRIPTION, true);
        var song = buildSongWithTempo(dottedTempo);

        var reloaded = roundTrip(song);

        assertTempoEquals(
            reloaded.getTempo(), DOTTED_TEMPO_BPM, Duration.CROTCHET_DOTTED, NO_DESCRIPTION, true, "dotted base tempo");
    }

    @Test
    void testTempoDescriptionRoundTrips() throws Exception {
        var describedTempo = new Tempo(DESCRIBED_TEMPO_BPM, Duration.CROTCHET, TEMPO_DESCRIPTION, true);
        var song = buildSongWithTempo(describedTempo);

        var reloaded = roundTrip(song);

        assertTempoEquals(
            reloaded.getTempo(), DESCRIBED_TEMPO_BPM, Duration.CROTCHET, TEMPO_DESCRIPTION, true, "described base tempo");
    }

    @Test
    void testPerNoteTempoOnLaterLineRoundTrips() throws Exception {
        // A per-note tempo on a note in line 2 exercises the reader's cross-line
        // direction binding (the tempo <direction> and its note straddle a system
        // break). No base tempo is set, so line 1 stays tempo-free.
        var perNoteTempo = new Tempo(PER_NOTE_TEMPO_BPM, Duration.MINIM, NO_DESCRIPTION, true);

        var song = buildSong(
            line -> line.addElement(crotchet()),
            line -> {
                var note = crotchet();
                line.addElement(note);
                attachTempo(note, perNoteTempo);
            }
        );

        var reloaded = roundTrip(song);

        assertThat(reloaded.getLines()).as("line count").hasSize(TWO_LINE_COUNT);
        assertThat(tempoOf(reloaded.getLine(0), 0)).as("line 1 note carries no tempo").isNull();
        assertTempoEquals(
            tempoOf(reloaded.getLine(SECOND_LINE_INDEX), 0),
            PER_NOTE_TEMPO_BPM, Duration.MINIM, NO_DESCRIPTION, true, "line 2 per-note tempo");
    }

    @Test
    void testLeadingGraceNoteDoesNotAffectSongTempoRoundTrip() throws Exception {
        // The song tempo is written positionally (first child of measure 1),
        // not anchored to any element, so a leading grace note makes no
        // difference to where it is written or read.
        var baseTempo = new Tempo(BASE_TEMPO_BPM, Duration.CROTCHET, NO_DESCRIPTION, true);

        var song = buildSong(line -> {
            line.addElement(graceQuaver());
            line.addElement(crotchet());
        });
        song.withoutMutationTracking(() -> song.setTempo(baseTempo));

        var reloaded = roundTrip(song);
        var graceNote = reloaded.getLine(0).getElement(0);

        assertTempoEquals(
            reloaded.getTempo(), BASE_TEMPO_BPM, Duration.CROTCHET, NO_DESCRIPTION, true,
            "song tempo past a leading grace note");
        assertThat(graceNote.findAttachment(TempoChangeAttachment.class))
            .as("leading grace note carries no tempo attachment")
            .isNull();
    }

    @Test
    void testLeadingEmptyLineDoesNotAffectSongTempoRoundTrip() throws Exception {
        // Likewise for a leading empty line: the song tempo is written as the
        // first child of measure 1 regardless of which line that measure
        // belongs to, empty or not.
        var baseTempo = new Tempo(BASE_TEMPO_BPM, Duration.CROTCHET, NO_DESCRIPTION, true);

        var song = buildSong(
            line -> { },
            line -> line.addElement(crotchet())
        );
        song.withoutMutationTracking(() -> song.setTempo(baseTempo));

        var reloaded = roundTrip(song);

        assertThat(reloaded.getLines()).as("line count").hasSize(TWO_LINE_COUNT);
        assertThat(reloaded.getLine(0).isEmpty())
            .as("the leading empty line survives the round-trip")
            .isTrue();
        assertTempoEquals(
            reloaded.getTempo(), BASE_TEMPO_BPM, Duration.CROTCHET, NO_DESCRIPTION, true,
            "song tempo past a leading empty line");
        assertThat(tempoOf(reloaded.getLine(SECOND_LINE_INDEX), 0))
            .as("first note of the second line carries no tempo attachment")
            .isNull();
    }

    @Test
    void testSongTempoIsTheFirstChildOfTheFirstMeasure() throws Exception {
        var tempo = new Tempo(BASE_TEMPO_BPM, Duration.CROTCHET, NO_DESCRIPTION, true);
        var song = buildSong(line -> line.addElement(crotchet()));
        song.withoutMutationTracking(() -> song.setTempo(tempo));

        var xml = writeToString(song);

        var measureStart = xml.indexOf('<' + MusicXmlTags.MEASURE);
        assertThat(measureStart).as("first <measure> tag found").isGreaterThanOrEqualTo(0);

        var measureEnd = xml.indexOf("</" + MusicXmlTags.MEASURE + '>', measureStart);
        assertThat(measureEnd).as("first </measure> tag found").isGreaterThan(measureStart);

        var measureBody = xml.substring(measureStart, measureEnd);
        var directionIndex = measureBody.indexOf('<' + MusicXmlTags.DIRECTION);
        var printIndex = measureBody.indexOf('<' + MusicXmlTags.PRINT);
        var attributesIndex = measureBody.indexOf('<' + MusicXmlTags.ATTRIBUTES);

        assertThat(directionIndex).as("tempo <direction> present in measure 1").isGreaterThanOrEqualTo(0);
        assertThat(printIndex).as("<print> present in measure 1").isGreaterThanOrEqualTo(0);
        assertThat(attributesIndex).as("<attributes> present in measure 1").isGreaterThanOrEqualTo(0);
        assertThat(directionIndex).as("tempo direction precedes <print>").isLessThan(printIndex);
        assertThat(directionIndex).as("tempo direction precedes <attributes>").isLessThan(attributesIndex);
    }

    @Test
    void testSongWithNoNotesStillWritesItsTempo() throws Exception {
        var tempo = new Tempo(BASE_TEMPO_BPM, Duration.CROTCHET, NO_DESCRIPTION, true);
        var song = buildSong();
        song.withoutMutationTracking(() -> song.setTempo(tempo));

        var reloaded = roundTrip(song);

        assertTempoEquals(
            reloaded.getTempo(), BASE_TEMPO_BPM, Duration.CROTCHET, NO_DESCRIPTION, true, "tempo of a note-less song");
    }

    @Test
    void testTempoDirectionBoundToTheFirstNoteReadsAsTheSongTempo() throws Exception {
        // Backward compatibility: files written before the song tempo moved to
        // being the measure's first child carried it immediately before the
        // first <note> instead. The reader's rule is purely positional --
        // "first tempo direction in measure 1" -- so this still loads as the
        // song tempo, with no attachment on the note.
        var xml = scoreWithMeasureBody(tempoDirectionXml(BASE_TEMPO_BPM) + noteXml());

        var song = parse(xml);

        assertTempoEquals(
            song.getTempo(), BASE_TEMPO_BPM, Duration.CROTCHET, NO_DESCRIPTION, true, "song tempo from old-format file");
        assertThat(tempoOf(song.getLine(0), 0)).as("first note carries no tempo attachment").isNull();
    }

    @Test
    void testASecondTempoDirectionInMeasureOneBindsToItsNote() throws Exception {
        // Only the first tempo direction of measure 1 is the song tempo; a
        // second one anywhere in that same measure is an ordinary per-note
        // tempo change bound to the following note, exactly as today.
        var xml = scoreWithMeasureBody(
            tempoDirectionXml(BASE_TEMPO_BPM) + noteXml() +
            tempoDirectionXml(PER_NOTE_TEMPO_BPM) + noteXml());

        var song = parse(xml);
        var line = song.getLine(0);

        assertTempoEquals(
            song.getTempo(), BASE_TEMPO_BPM, Duration.CROTCHET, NO_DESCRIPTION, true, "song tempo from first direction");
        assertThat(tempoOf(line, 0)).as("first note carries no tempo attachment").isNull();
        assertTempoEquals(
            tempoOf(line, 1),
            PER_NOTE_TEMPO_BPM, Duration.CROTCHET, NO_DESCRIPTION, true, "second direction binds to its note");
    }

    // -------------------------------------------------------------------------
    // Schema validation
    // -------------------------------------------------------------------------

    @Test
    void testTempoDirectionsValidateAgainstSchema() throws Exception {
        var baseTempo = new Tempo(BASE_TEMPO_BPM, Duration.CROTCHET, TEMPO_DESCRIPTION, true);
        var hiddenTempo = new Tempo(HIDDEN_TEMPO_BPM, Duration.CROTCHET, NO_DESCRIPTION, false);
        var dottedTempo = new Tempo(DOTTED_TEMPO_BPM, Duration.CROTCHET_DOTTED, NO_DESCRIPTION, true);

        var song = buildSong(line -> {
            var note0 = crotchet();
            var note1 = crotchet();
            var note2 = crotchet();
            line.addElement(note0);
            line.addElement(note1);
            line.addElement(note2);
            attachTempo(note0, baseTempo);
            attachTempo(note1, hiddenTempo);
            attachTempo(note2, dottedTempo);
        });
        song.withoutMutationTracking(() -> song.setTempo(baseTempo));

        var xml = writeToString(song);
        var validator = new MusicXmlSchemaValidator();

        assertThatCode(() -> validator.validate(xml))
            .as("tempo direction output validates against the MusicXML 4.0 schema")
            .doesNotThrowAnyException();
    }
}
