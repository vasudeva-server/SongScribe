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
package songscribe.dom;

import org.jspecify.annotations.Nullable;

import songscribe.midi.MidiSequenceBuilder;
import songscribe.util.Copyable;

/**
 * The tempo of a song, or of one tempo change within it: a beat unit, a speed in beats per
 * minute, and a marking saying what is drawn.
 *
 * <p>Immutable, compared by value. Every tempo draws something, by the shape of
 * {@link TempoMarking}. See {@code docs/song-tempo.md}.
 */
public record Tempo(int visibleTempo, BeatUnit tempoType, TempoMarking marking)
    implements Copyable<Tempo> {

    // The default tempo produced by the no-arg constructor. Note that the default marking's
    // description is persisted to MusicXML and read back verbatim, so it is deliberately not a
    // localized string.

    public static final int DEFAULT_BPM = 120;
    public static final BeatUnit DEFAULT_TYPE = BeatUnit.CROTCHET;
    public static final TempoMarking DEFAULT_MARKING = new TempoMarking.Metronome("Moderate");

    public Tempo() {
        this(DEFAULT_BPM, DEFAULT_TYPE, DEFAULT_MARKING);
    }

    /**
     * @return the speed in MIDI terms, so playback runs at the written tempo whatever unit it
     *         counts
     */
    public int realTempo() {
        return ((visibleTempo * tempoType.ticks()) / MidiSequenceBuilder.PPQ);
    }

    @Override
    public Tempo copy() {
        return this;
    }

    /**
     * The beat is the tempo type alone: changing it revalidates the notation of the whole song,
     * while the BPM and the marking do not.
     *
     * @param a {@code null} where there is no tempo
     * @param b {@code null} where there is no tempo
     * @return {@code false} when exactly one is null
     */
    public static boolean haveSameBeat(@Nullable Tempo a, @Nullable Tempo b) {
        if (a == b) {
            return true;
        }

        if (a == null || b == null) {
            return false;
        }

        return a.tempoType == b.tempoType;
    }
}
