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

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.music.ElementType;
import songscribe.music.StaffElement;
import songscribe.music.Tempo;
import songscribe.ui.playback.MidiMetaMessageTypes;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link LineTrackBuilder} emits a colorize event for the host note
 * at the grace note's tick, so that the grace+host pair highlights together from
 * the grace's onset during playback.
 */
class LineTrackBuilderGraceHostColorizeTest extends UnitTest {

    private static final int GRACE_INDEX = 0;
    private static final int HOST_INDEX = 1;
    private static final int LINE_INDEX = 0;

    private static final PlaybackSettings DEFAULT_SETTINGS = new PlaybackSettings(
        0, 100, 100, false
    );

    /**
     * For a paired grace+host line, addToTrack must emit:
     *   1. grace colorize — when the grace is processed
     *   2. host colorize  — emitted by addColorizeForGraceAndHost at the grace's tick
     *   3. host colorize  — emitted by the host's own normal loop iteration
     * Events 1 and 2 are in that order at the grace's tick; event 3 is at the host's
     * tick. Grace notes have zero written duration, so the grace tick and host tick
     * are the same in a minimal two-element line, but the ordering within that tick
     * is guaranteed: grace first, then host (helper), then host (normal).
     */
    @Test
    void testPairedGraceEmitsHostColorizeAtGraceTick() throws Exception {
        var grace = ElementType.GRACE_QUAVER.newInstance();
        grace.setGlissando(StaffElement.Glissando.Type.CONNECTED);
        var host = ElementType.CROTCHET.newInstance();
        host.setStaffPosition(-2);
        var line = detachedLine();
        line.addElement(grace);
        line.addElement(host);

        var track = buildMidiTrack(line);
        var colorizeEvents = getColorizeEvents(track);

        // grace(0) + host(1, from helper) + host(1, from normal loop) = 3
        assertThat(colorizeEvents)
            .as("grace+host colorize event count")
            .hasSize(3);

        // Events emitted when the grace is processed: grace first, then host
        assertThat(decodeElementIndex(colorizeEvents.get(0)))
            .as("first colorize carries grace index")
            .isEqualTo(GRACE_INDEX);
        assertThat(decodeElementIndex(colorizeEvents.get(1)))
            .as("second colorize carries host index (from addColorizeForGraceAndHost)")
            .isEqualTo(HOST_INDEX);

        // The helper emits the host colorize at the grace's tick
        assertThat(colorizeEvents.get(1).getTick())
            .as("helper-emitted host colorize is at the grace's tick")
            .isEqualTo(colorizeEvents.get(0).getTick());

        // Event emitted when the host is processed normally
        assertThat(decodeElementIndex(colorizeEvents.get(2)))
            .as("third colorize carries host index (from host's own loop iteration)")
            .isEqualTo(HOST_INDEX);
    }

    @Test
    void testUnpairedNoteEmitsSingleColorize() throws Exception {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());

        var track = buildMidiTrack(line);
        var colorizeEvents = getColorizeEvents(track);

        assertThat(colorizeEvents)
            .as("single note produces exactly one colorize event")
            .hasSize(1);
        assertThat(decodeElementIndex(colorizeEvents.get(0)))
            .as("colorize carries the note's own index")
            .isEqualTo(0);
    }

    private static Track buildMidiTrack(songscribe.music.Line line) throws Exception {
        var sequence = new Sequence(Sequence.PPQ, 96);
        var track = sequence.createTrack();
        new LineTrackBuilder(line).addToTrack(track, LINE_INDEX, 0, new Tempo(), DEFAULT_SETTINGS);
        return track;
    }

    private static List<MidiEvent> getColorizeEvents(Track track) {
        var events = new ArrayList<MidiEvent>();

        for (var i = 0; i < track.size(); i++) {
            var event = track.get(i);

            if (event.getMessage() instanceof MetaMessage meta
                && meta.getType() == MidiMetaMessageTypes.SEQUENCE_NUMBER) {
                events.add(event);
            }
        }

        return events;
    }

    /** Decodes the element index from a SEQUENCE_NUMBER meta message payload. */
    private static int decodeElementIndex(MidiEvent event) {
        var data = ((MetaMessage) event.getMessage()).getData();
        return ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
    }
}
