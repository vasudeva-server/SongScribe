/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package songscribe.midi;

import module java.desktop;

import songscribe.dom.Tempo;
import songscribe.ui.playback.MidiMetaMessageTypes;

/**
 * Factory for common MIDI events shared across the playback, sequence-building,
 * and instrument-preview code paths.
 */
public final class MidiEventFactory {

    static final int MICROSECONDS_PER_MINUTE = 60_000_000;
    private static final int PERCENT = 100;
    private static final int TEMPO_MESSAGE_LENGTH = 3;

    private MidiEventFactory() {
    }

    /**
     * Adds a SET_TEMPO meta message to the given track. The MIDI tempo value is
     * derived from {@code tempo}'s real BPM scaled by {@code tempoChangePercent}
     * (where 100 means no change).
     */
    public static void addTempoEvent(
        Track track,
        int ticks,
        Tempo tempo,
        int tempoChangePercent
    ) throws InvalidMidiDataException {
        addTempoEvent(track, ticks, (tempo.getRealTempo() * tempoChangePercent) / PERCENT);
    }

    /**
     * Adds a SET_TEMPO meta message to the given track for a plain quarter-note BPM.
     */
    public static void addTempoEvent(Track track, int ticks, int quarterNoteBpm)
        throws InvalidMidiDataException {
        var midiTempo = MICROSECONDS_PER_MINUTE / quarterNoteBpm;
        var tempoMessage = new MetaMessage();
        tempoMessage.setMessage(
            MidiMetaMessageTypes.SET_TEMPO,
            new byte[]{
                (byte) (midiTempo >> 16),
                (byte) (midiTempo >> 8),
                (byte) midiTempo,
            },
            TEMPO_MESSAGE_LENGTH
        );
        track.add(new MidiEvent(tempoMessage, ticks));
    }
}
