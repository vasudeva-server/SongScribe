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

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import songscribe.dom.Duration;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;

/**
 * Round-trip coverage for tempo {@code <direction>} / {@code <metronome>}
 * emission and parsing (Phase 3). Songs are built in post-load (canonical)
 * form: the song base tempo is mirrored onto the first element of the first
 * line (as {@code Line.attachInitialTempoIfNeeded} does on load), so the
 * anchor invariant {@code firstElement.hasTempo ⟺ song.tempo != null} holds
 * before the round-trip.
 */
class MusicXmlTempoRoundTripTest extends MusicXmlRoundTripSupport {

    // Visible tempo (BPM) values — each distinct so a mix-up is caught.
    private static final int BASE_TEMPO_BPM = 120;
    private static final int PER_NOTE_TEMPO_BPM = 90;
    private static final int HIDDEN_TEMPO_BPM = 100;
    private static final int DOTTED_TEMPO_BPM = 76;
    private static final int DESCRIBED_TEMPO_BPM = 138;

    // No tempo description → no <words> emitted → recovered as the empty string.
    private static final String NO_DESCRIPTION = "";
    private static final String TEMPO_DESCRIPTION = "Allegro vivace";

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

        // isNotNull() above already fails the test on null; this guard narrows
        // the type for NullAway on the field accesses below.
        if (actual == null) {
            return;
        }

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
     * Attaches {@code tempo} to {@code element} exactly as the model mirrors the
     * base tempo onto a note on load.
     */
    private static void attachTempo(StaffElement element, Tempo tempo) {
        element.addAttachment(new TempoChangeAttachment(element, tempo));
    }

    /**
     * Builds a single-line song whose first note carries {@code baseTempo} both
     * as an attachment and as the song base tempo (canonical post-load form).
     */
    private Song buildBaseTempoSong(Tempo baseTempo) {
        var song = buildSong(line -> {
            var note0 = ElementType.CROTCHET.newInstance();
            var note1 = ElementType.CROTCHET.newInstance();
            line.addElement(note0);
            line.addElement(note1);
            attachTempo(note0, baseTempo);
        });

        song.withoutMutationTracking(() -> song.setTempo(baseTempo));
        return song;
    }

    // -------------------------------------------------------------------------
    // Round-trip tests
    // -------------------------------------------------------------------------

    @Test
    void testNoTempoStaysAbsentAfterRoundTrip() throws Exception {
        // A song with no explicit tempo (e.g. a brand-new document) must not gain
        // one merely by being saved and reloaded. Regression test for issue #658.
        var song = buildSong(line -> line.addElement(ElementType.CROTCHET.newInstance()));

        var xml = writeToString(song);
        assertThat(xml).as("no <sound tempo> should be written").doesNotContain("<sound tempo");

        var reloaded = roundTrip(song);

        assertThat(reloaded.getTempo()).as("reloaded song must still have no explicit tempo").isNull();
        assertThat(tempoOf(reloaded.getLine(0), 0)).as("first note carries no tempo").isNull();
    }

    @Test
    void testBaseTempoOnlyRoundTrips() throws Exception {
        var baseTempo = new Tempo(BASE_TEMPO_BPM, Duration.CROTCHET, NO_DESCRIPTION, true);
        var song = buildBaseTempoSong(baseTempo);

        var reloaded = roundTrip(song);
        var line = reloaded.getLine(0);

        assertTempoEquals(reloaded.getTempo(), BASE_TEMPO_BPM, Duration.CROTCHET, NO_DESCRIPTION, true, "song base tempo");
        assertTempoEquals(tempoOf(line, 0), BASE_TEMPO_BPM, Duration.CROTCHET, NO_DESCRIPTION, true, "first note tempo");
        assertThat(tempoOf(line, 1)).as("plain note carries no tempo").isNull();
    }

    @Test
    void testBaseAndDistinctPerNoteTempoRoundTrip() throws Exception {
        var baseTempo = new Tempo(BASE_TEMPO_BPM, Duration.CROTCHET, NO_DESCRIPTION, true);
        var perNoteTempo = new Tempo(PER_NOTE_TEMPO_BPM, Duration.MINIM, NO_DESCRIPTION, true);

        var song = buildSong(line -> {
            var note0 = ElementType.CROTCHET.newInstance();
            var note1 = ElementType.CROTCHET.newInstance();
            var note2 = ElementType.CROTCHET.newInstance();
            line.addElement(note0);
            line.addElement(note1);
            line.addElement(note2);
            attachTempo(note0, baseTempo);
            attachTempo(note2, perNoteTempo);
        });
        song.withoutMutationTracking(() -> song.setTempo(baseTempo));

        var reloaded = roundTrip(song);
        var line = reloaded.getLine(0);

        assertTempoEquals(reloaded.getTempo(), BASE_TEMPO_BPM, Duration.CROTCHET, NO_DESCRIPTION, true, "song base tempo");
        assertTempoEquals(tempoOf(line, 0), BASE_TEMPO_BPM, Duration.CROTCHET, NO_DESCRIPTION, true, "first note tempo");
        assertThat(tempoOf(line, 1)).as("middle note carries no tempo").isNull();
        assertTempoEquals(
            tempoOf(line, PER_NOTE_INDEX),
            PER_NOTE_TEMPO_BPM, Duration.MINIM, NO_DESCRIPTION, true, "per-note tempo");
    }

    @Test
    void testHiddenTempoRoundTrips() throws Exception {
        var hiddenTempo = new Tempo(HIDDEN_TEMPO_BPM, Duration.CROTCHET, NO_DESCRIPTION, false);
        var song = buildBaseTempoSong(hiddenTempo);

        var reloaded = roundTrip(song);
        var line = reloaded.getLine(0);

        assertTempoEquals(reloaded.getTempo(), HIDDEN_TEMPO_BPM, Duration.CROTCHET, NO_DESCRIPTION, false, "hidden base tempo");
        assertTempoEquals(tempoOf(line, 0), HIDDEN_TEMPO_BPM, Duration.CROTCHET, NO_DESCRIPTION, false, "hidden first note tempo");
    }

    @Test
    void testDottedBeatUnitRoundTrips() throws Exception {
        var dottedTempo = new Tempo(DOTTED_TEMPO_BPM, Duration.CROTCHET_DOTTED, NO_DESCRIPTION, true);
        var song = buildBaseTempoSong(dottedTempo);

        var reloaded = roundTrip(song);
        var line = reloaded.getLine(0);

        assertTempoEquals(
            reloaded.getTempo(), DOTTED_TEMPO_BPM, Duration.CROTCHET_DOTTED, NO_DESCRIPTION, true, "dotted base tempo");
        assertTempoEquals(
            tempoOf(line, 0), DOTTED_TEMPO_BPM, Duration.CROTCHET_DOTTED, NO_DESCRIPTION, true, "dotted first note tempo");
    }

    @Test
    void testTempoDescriptionRoundTrips() throws Exception {
        var describedTempo = new Tempo(DESCRIBED_TEMPO_BPM, Duration.CROTCHET, TEMPO_DESCRIPTION, true);
        var song = buildBaseTempoSong(describedTempo);

        var reloaded = roundTrip(song);
        var line = reloaded.getLine(0);

        assertTempoEquals(
            reloaded.getTempo(), DESCRIBED_TEMPO_BPM, Duration.CROTCHET, TEMPO_DESCRIPTION, true, "described base tempo");
        assertTempoEquals(
            tempoOf(line, 0), DESCRIBED_TEMPO_BPM, Duration.CROTCHET, TEMPO_DESCRIPTION, true, "described first note tempo");
    }

    @Test
    void testUnmirroredBaseTempoRoundTrips() throws Exception {
        // A song whose base tempo is set on the song but NOT mirrored onto the
        // first note (a not-yet-materialized song). The writer's tempoForElement
        // must fall back to song.getTempo() for the first element and still emit
        // the base tempo; otherwise it is silently lost on write.
        var baseTempo = new Tempo(BASE_TEMPO_BPM, Duration.CROTCHET, NO_DESCRIPTION, true);

        var song = buildSong(line -> {
            line.addElement(ElementType.CROTCHET.newInstance());
            line.addElement(ElementType.CROTCHET.newInstance());
        });
        song.withoutMutationTracking(() -> song.setTempo(baseTempo));

        var reloaded = roundTrip(song);
        var line = reloaded.getLine(0);

        assertTempoEquals(reloaded.getTempo(), BASE_TEMPO_BPM, Duration.CROTCHET, NO_DESCRIPTION, true, "unmirrored base tempo");
        assertTempoEquals(tempoOf(line, 0), BASE_TEMPO_BPM, Duration.CROTCHET, NO_DESCRIPTION, true, "reconstructed first note tempo");
    }

    @Test
    void testPerNoteTempoOnLaterLineRoundTrips() throws Exception {
        // A per-note tempo on a note in line 2 exercises the reader's cross-line
        // direction binding (the tempo <direction> and its note straddle a system
        // break). No base tempo is set, so line 1 stays tempo-free.
        var perNoteTempo = new Tempo(PER_NOTE_TEMPO_BPM, Duration.MINIM, NO_DESCRIPTION, true);

        var song = buildSong(
            line -> line.addElement(ElementType.CROTCHET.newInstance()),
            line -> {
                var note = ElementType.CROTCHET.newInstance();
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
    void testInitialTempoAnchorsOnLeadingGraceNoteRoundTrip() throws Exception {
        // When the first note is a grace note, the base tempo anchors on it — the
        // first element of the first line. The anchor must survive a MusicXML
        // round-trip on both the write (firstElementOfSong) and read
        // (applyInitialTempo) sides.
        var baseTempo = new Tempo(BASE_TEMPO_BPM, Duration.CROTCHET, NO_DESCRIPTION, true);

        var song = buildSong(line -> {
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            var hostNote = ElementType.CROTCHET.newInstance();
            line.addElement(graceNote);
            line.addElement(hostNote);
            attachTempo(graceNote, baseTempo);
        });
        song.withoutMutationTracking(() -> song.setTempo(baseTempo));

        var reloaded = roundTrip(song);
        var graceAnchor = reloaded.getLine(0).getElement(0);

        var graceAttachment = graceAnchor.findAttachment(TempoChangeAttachment.class);
        assertTempoEquals(
            graceAttachment == null ? null : graceAttachment.getTempo(),
            BASE_TEMPO_BPM, Duration.CROTCHET, NO_DESCRIPTION, true, "grace-note tempo after round-trip");
        assertTempoEquals(
            reloaded.getTempo(), BASE_TEMPO_BPM, Duration.CROTCHET, NO_DESCRIPTION, true, "recovered base tempo");
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
            var note0 = ElementType.CROTCHET.newInstance();
            var note1 = ElementType.CROTCHET.newInstance();
            var note2 = ElementType.CROTCHET.newInstance();
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
