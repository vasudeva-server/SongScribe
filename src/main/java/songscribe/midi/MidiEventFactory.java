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

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Track;

import songscribe.dom.Key;
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
    private static final int KEY_SIGNATURE_MESSAGE_LENGTH = 2;

    /** {@code mi} byte for a MIDI key signature meta-event: every SongScribe key is major. */
    private static final byte MAJOR_MODE = 0;

    private MidiEventFactory() {
    }

    public static void addTempoEvent(
        Track track,
        int ticks,
        Tempo tempo,
        int tempoChangePercent
    ) throws InvalidMidiDataException {
        addTempoEvent(track, ticks, (tempo.realTempo() * tempoChangePercent) / PERCENT);
    }

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

    public static void addKeySignatureEvent(Track track, int ticks, Key key)
        throws InvalidMidiDataException {
        var keySignatureMessage = new MetaMessage();
        keySignatureMessage.setMessage(
            MidiMetaMessageTypes.KEY_SIGNATURE,
            new byte[]{
                (byte) key.fifths(),
                MAJOR_MODE,
            },
            KEY_SIGNATURE_MESSAGE_LENGTH
        );
        track.add(new MidiEvent(keySignatureMessage, ticks));
    }
}
