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
 * <p><strong>A tempo is a value.</strong> It is immutable and it compares by value, so a caller
 * holding one cannot be surprised by a change made elsewhere, and a controller can tell a
 * gathered tempo from the one the document already holds. Whoever wants a different tempo builds
 * one; {@link Song#setTempo} and {@link TempoChangeAttachment#setTempo} are how a document takes
 * it.
 *
 * <p><strong>Every tempo draws something.</strong> That is not a rule this type keeps — it is
 * a shape {@link TempoMarking} has, so no producer of a tempo can break it and nothing
 * downstream checks for it.
 *
 * <p>For the reasoning behind all of the above, see {@code docs/song-tempo.md}.
 *
 * @param visibleTempo the speed in beats per minute, as written
 * @param tempoType    the beat unit the speed counts, which is what beams group against and what
 *                     a tuplet is measured in
 * @param marking      what this tempo draws — see {@link TempoMarking}
 */
public record Tempo(int visibleTempo, Duration tempoType, TempoMarking marking)
    implements Copyable<Tempo> {

    // The default tempo produced by the no-arg constructor. Note that the default marking's
    // description is persisted to MusicXML and read back verbatim, so it is deliberately not a
    // localized string.

    public static final int DEFAULT_BPM = 120;
    public static final Duration DEFAULT_TYPE = Duration.CROTCHET;
    public static final TempoMarking DEFAULT_MARKING = new TempoMarking.Metronome("Moderate");

    /**
     * The tempo a song has before anything states one.
     */
    public Tempo() {
        this(DEFAULT_BPM, DEFAULT_TYPE, DEFAULT_MARKING);
    }

    /**
     * @return the speed in MIDI terms: the written speed scaled by how long the beat unit is, so
     *         playback runs at the written tempo whatever unit it counts
     */
    public int realTempo() {
        return ((visibleTempo * tempoType.getNote().getDuration()) / MidiSequenceBuilder.PPQ);
    }

    /**
     * {@inheritDoc}
     *
     * @return {@code this}. Every component is immutable, so a tempo holds no state for a copy to
     *         separate.
     */
    @Override
    public Tempo copy() {
        return this;
    }

    /**
     * Whether two tempos define the same beat.
     *
     * <p>The beat is the tempo <em>type</em> alone. It is what beams group against and what a
     * tuplet is measured in, so changing it revalidates the notation of the whole song. The BPM
     * and the marking say how the tempo is displayed and leave the notation underneath
     * untouched.
     *
     * <p>Asked by everything that would otherwise redo beat-dependent work for a tempo edit that
     * only changed how the marking reads.
     *
     * @param a one tempo, or {@code null} where there is none
     * @param b the other tempo, or {@code null} where there is none
     * @return {@code true} when both are null, or both name the same beat unit; {@code false}
     *         when exactly one is null, since no beat and some beat are not the same beat
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
