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
 * minute, a description, and whether the metronome glyph and the speed are drawn.
 *
 * <p><strong>This type is mutable, and it has no value equality on purpose.</strong> Four
 * setters change an instance in place, and {@code Song.tempoDidChange} uses them rather than
 * replacing the instance. So {@code equals} is identity here, and a tempo would lose its place
 * in a hash-based collection the moment a setter ran. Compare two tempos by value with
 * {@link #haveSameValue(Tempo, Tempo)}.
 *
 * <p><strong>Every tempo draws something.</strong> That is not a rule this type keeps — it is
 * a shape {@link TempoMarking} has, so no producer of a tempo can break it and nothing
 * downstream checks for it.
 *
 * <p>For the reasoning behind all of the above, see {@code docs/song-tempo.md}.
 */
public class Tempo implements Copyable<Tempo> {

    // The default tempo produced by the no-arg constructor. Note that the default marking's
    // description is persisted to MusicXML and read back verbatim, so it is deliberately not a
    // localized string.

    public static final int DEFAULT_BPM = 120;
    public static final Duration DEFAULT_TYPE = Duration.CROTCHET;
    public static final TempoMarking DEFAULT_MARKING = new TempoMarking.Metronome("Moderate");

    private int visibleTempo;
    private Duration tempoType;
    private TempoMarking marking;

    public Tempo() {
        this(DEFAULT_BPM, DEFAULT_TYPE, DEFAULT_MARKING);
    }

    public Tempo(int tempo, Duration tempoType, TempoMarking marking) {
        visibleTempo = tempo;
        this.tempoType = tempoType;
        this.marking = marking;
    }

    public int getVisibleTempo() {
        return visibleTempo;
    }

    public void setVisibleTempo(int visibleTempo) {
        this.visibleTempo = visibleTempo;
    }

    public Duration getTempoType() {
        return tempoType;
    }

    public void setTempoType(Duration tempoType) {
        this.tempoType = tempoType;
    }

    /**
     * @return what this tempo draws — see {@link TempoMarking}
     */
    public TempoMarking getMarking() {
        return marking;
    }

    public void setMarking(TempoMarking marking) {
        this.marking = marking;
    }

    public int getRealTempo() {
        return ((visibleTempo * tempoType.getNote().getDuration()) / MidiSequenceBuilder.PPQ);
    }

    /**
     * Copies every value of {@code source} onto this tempo.
     *
     * <p>In place rather than by replacement, because a caller that holds this instance must go
     * on holding it. {@code Song.tempoDidChange} is that caller: the song's tempo is reachable
     * from the song and from the layout, so a replacement would leave both on the old instance.
     *
     * <p>The marking is shared rather than copied, which is safe because a marking is immutable.
     *
     * @param source the tempo to take the values from, which this does not modify
     * @effects sets all three of this tempo's values
     */
    public void copyFrom(Tempo source) {
        visibleTempo = source.visibleTempo;
        tempoType = source.tempoType;
        marking = source.marking;
    }

    /**
     * Returns a copy of this tempo, so a copy and its original hold independent state even if a
     * future mutator starts changing this tempo's fields in place.
     *
     * <p>The marking is shared rather than copied, which is safe because a marking is immutable.
     *
     * @return a tempo with the same values, sharing no mutable state with this one
     */
    @Override
    public Tempo copy() {
        return new Tempo(visibleTempo, tempoType, marking);
    }

    /**
     * Whether two tempos — either of which may be null — describe the same tempo.
     *
     * <p>Deliberately not {@code equals}/{@code hashCode}: a {@code Tempo} is mutable, so
     * value equality on it would be unsafe the moment an instance entered a hash-based
     * collection. Callers that need to tell a copied tempo from a changed one ask this
     * instead.
     */
    public static boolean haveSameValue(@Nullable Tempo a, @Nullable Tempo b) {
        if (a == b) {
            return true;
        }

        if (a == null || b == null) {
            return false;
        }

        return a.visibleTempo == b.visibleTempo
            && a.tempoType == b.tempoType
            && a.marking.equals(b.marking);
    }

    /**
     * Whether two tempos — either of which may be null — define the same beat.
     *
     * <p>The beat is the tempo <em>type</em> alone. It is what beams group against and what a
     * tuplet is measured in, so changing it revalidates the notation of the whole song. The BPM
     * and the marking say how the tempo is displayed and leave the notation underneath
     * untouched.
     *
     * <p>Asked by everything that would otherwise redo beat-dependent work for a tempo edit that
     * only changed how the marking reads.
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
