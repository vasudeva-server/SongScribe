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

package songscribe.midi;

import module java.desktop;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Line;
import songscribe.dom.Tempo;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.graceQuaver;

/**
 * Integration tests for slide MIDI generation using the {@code connections}
 * fixture. Verifies that connecting glissandos and falls produce correct
 * pitch bend and RPN control change events when rendered to a MIDI track.
 */
@SuppressWarnings({ "OverlyBroadThrowsClause", "StaticVariableMayNotBeInitialized" })
class SlideMidiIntegrationTest extends UnitTest {


    private static final int PLAIN_NOTE_STAFF_POS = -2;

    /** CC number for RPN Data Entry MSB, which carries the pitch-bend sensitivity in semitones. */
    private static final int RPN_DATA_ENTRY_MSB_CC = 6;

    /** Bit shift to combine a 14-bit pitch-bend value from its 7-bit MSB and LSB bytes. */
    private static final int PITCH_BEND_MSB_SHIFT = 7;

    private static final PlaybackSettings DEFAULT_SETTINGS = new PlaybackSettings(
        0, 100, 100, false
    );

    private static Line line;
    private static Tempo tempo;

    @BeforeAll
    static void loadFixtureData() throws Exception {
        var song = loadFixture("connections");
        line = song.getLine(0);
        // The fixture's tempo sits on the song's first element, which the MusicXML reader
        // now reads as the song-level tempo rather than a per-note TempoChangeAttachment.
        tempo = song.getTempo();
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ConnectedGlissando {

        @Test
        void testCcEventsContainRpnSequence() throws Exception {
            var track = buildMidiTrack(line, tempo);
            var ccEvents = getEventsByCommand(track, ShortMessage.CONTROL_CHANGE);

            assertThat(ccEvents).as("CC events present").hasSizeGreaterThanOrEqualTo(4);

            var controllers = ccEvents.stream()
                .map(e -> ((ShortMessage) e.getMessage()).getData1())
                .toList();

            assertThat(controllers.subList(0, 4))
                .as("RPN 0 sequence")
                .containsExactly(101, 100, 6, 38);
        }

        @Test
        void testPitchBendEventsPresent() throws Exception {
            var track = buildMidiTrack(line, tempo);
            var bendEvents = getEventsByCommand(track, ShortMessage.PITCH_BEND);

            assertThat(bendEvents).as("pitch bend present").isNotEmpty();
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class Fall {

        @Test
        void testPitchBendEventsPresent() throws Exception {
            var track = buildMidiTrack(line, tempo);
            var bendEvents = getEventsByCommand(track, ShortMessage.PITCH_BEND);

            assertThat(bendEvents).as("fall pitch bend present").isNotEmpty();
        }

        @Test
        void testFallBendsDownward() throws Exception {
            // Isolate a single note with a fall so the bend events come only from the fall,
            // not the fixture's connecting glissandos (which can bend either direction).
            var note = crotchet();
            note.setStaffPosition(PLAIN_NOTE_STAFF_POS);
            note.setFall();
            var fallLine = detachedLine();
            fallLine.addElement(note);

            var track = buildMidiTrack(fallLine, new Tempo());
            var bendValues = getEventsByCommand(track, ShortMessage.PITCH_BEND).stream()
                .map(e -> (ShortMessage) e.getMessage())
                .map(sm -> (sm.getData2() << PITCH_BEND_MSB_SHIFT) | sm.getData1())
                .toList();

            // A fall bends down from the source pitch: every event stays at or below center,
            // and at least one drops below it. An upward (or absent) bend would fail this.
            assertThat(bendValues)
                .as("fall pitch bend values stay at or below center")
                .allMatch(value -> value <= SlideMidiHelper.PITCH_BEND_CENTER)
                .as("fall pitch bend drops below center (downward)")
                .anyMatch(value -> value < SlideMidiHelper.PITCH_BEND_CENTER);
        }

        @Test
        void testRpnSensitivityIncludesFallSemitones() throws Exception {
            var track = buildMidiTrack(line, tempo);
            var ccEvents = getEventsByCommand(track, ShortMessage.CONTROL_CHANGE);

            var dataEntryMsbValues = ccEvents.stream()
                .map(e -> (ShortMessage) e.getMessage())
                .filter(sm -> sm.getData1() == RPN_DATA_ENTRY_MSB_CC)
                .map(ShortMessage::getData2)
                .toList();

            assertThat(dataEntryMsbValues)
                .as("RPN data-entry MSB includes the fall's pitch-bend sensitivity")
                .contains(SlideMidiHelper.FALL_SEMITONES);
        }
    }

    @Test
    void testNoPitchBendEventsWhenNoGlissando() throws Exception {
        var note = crotchet();
        note.setStaffPosition(PLAIN_NOTE_STAFF_POS);
        var plainLine = detachedLine();
        plainLine.addElement(note);

        var track = buildMidiTrack(plainLine, new Tempo());
        var bendEvents = getEventsByCommand(track, ShortMessage.PITCH_BEND);

        assertThat(bendEvents).as("no pitch bend events when no glissando").isEmpty();
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GraceHostPair {

        @Test
        void testNoteOnCountMatchesNonGracePitchedNotes() throws Exception {
            var grace = graceQuaver();
            grace.setGlissando();
            var host = crotchet();
            host.setStaffPosition(-2);
            var graceHostLine = detachedLine();
            graceHostLine.addElement(grace);
            graceHostLine.addElement(host);

            var track = buildMidiTrack(graceHostLine, new Tempo());
            var noteOnEvents = getEventsByCommand(track, ShortMessage.NOTE_ON);
            var bendEvents = getEventsByCommand(track, ShortMessage.PITCH_BEND);

            var expectedNoteOns = countNonGracePitchedNotes(graceHostLine);

            assertThat(noteOnEvents).as("only host NOTE_ON").hasSize(expectedNoteOns);
            assertThat(bendEvents).as("slide-in pitch bend present").isNotEmpty();
        }

        private int countNonGracePitchedNotes(Line line) {
            var count = 0;

            for (var i = 0; i < line.effectiveElementCount(); i++) {
                var type = line.getElement(i).getType();

                if (type.isPitchedNote() && !type.isGraceNote()) {
                    count++;
                }
            }

            return count;
        }
    }

    private static Track buildMidiTrack(Line line, Tempo tempo) throws Exception {
        var sequence = new Sequence(Sequence.PPQ, 96);
        var track = sequence.createTrack();
        new LineTrackBuilder(line).addToTrack(track, 0, 0, tempo, DEFAULT_SETTINGS);
        return track;
    }

    private static List<MidiEvent> getEventsByCommand(Track track, int command) {
        var events = new ArrayList<MidiEvent>();

        for (var i = 0; i < track.size(); i++) {
            var event = track.get(i);

            if (event.getMessage() instanceof ShortMessage sm && sm.getCommand() == command) {
                events.add(event);
            }
        }

        return events;
    }
}
