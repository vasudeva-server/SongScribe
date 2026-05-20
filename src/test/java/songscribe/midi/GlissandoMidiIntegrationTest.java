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
import java.util.Objects;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for glissando MIDI generation using the {@code connections}
 * fixture. Verifies that connected glissandos and slide-outs produce correct
 * pitch bend and RPN control change events when rendered to a MIDI track.
 */
@SuppressWarnings({ "OverlyBroadThrowsClause", "StaticVariableMayNotBeInitialized" })
class GlissandoMidiIntegrationTest extends UnitTest {

    // Element indices in connections.mssw
    private static final int TEMPO_INDEX = 0;

    private static final PlaybackSettings DEFAULT_SETTINGS = new PlaybackSettings(
        0, 100, 100, false
    );

    private static Line line;
    private static Tempo tempo;

    @BeforeAll
    static void loadFixtureData() throws Exception {
        var song = loadFixture("connections");
        line = song.getLine(0);
        var tempoAttachment = Objects.requireNonNull(
            line.getElement(TEMPO_INDEX).findAttachment(TempoChangeAttachment.class));
        tempo = tempoAttachment.getTempo();
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
    class SlideOut {

        @Test
        void testPitchBendEventsPresent() throws Exception {
            var track = buildMidiTrack(line, tempo);
            var bendEvents = getEventsByCommand(track, ShortMessage.PITCH_BEND);

            assertThat(bendEvents).as("slide-out pitch bend present").isNotEmpty();
        }

        @Test
        void testRpnSensitivityIncludesSlideOutSemitones() throws Exception {
            var track = buildMidiTrack(line, tempo);
            var ccEvents = getEventsByCommand(track, ShortMessage.CONTROL_CHANGE);

            var hasSlideOutSensitivity = ccEvents.stream()
                .filter(e -> ((ShortMessage) e.getMessage()).getData1() == 6)
                .anyMatch(e -> ((ShortMessage) e.getMessage()).getData2()
                    == GlissandoMidiHelper.SLIDE_OUT_SEMITONES);

            assertThat(hasSlideOutSensitivity)
                .as("RPN sensitivity includes slide-out semitones").isTrue();
        }
    }

    @Test
    void testNoPitchBendWithoutGlissando() {
        // PAIR_A_SRC (index 5) has no glissando in the unmodified fixture
        var note = line.getElement(5);
        assertThat(note.getGlissando()).as("pair A source has no glissando").isNull();
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GraceHostPair {

        @Test
        void testNoteOnCountMatchesNonGracePitchedNotes() throws Exception {
            var grace = ElementType.GRACE_QUAVER.newInstance();
            grace.setGlissando(StaffElement.Glissando.Type.CONNECTED);
            var host = ElementType.CROTCHET.newInstance();
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
