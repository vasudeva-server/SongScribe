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

public class Tempo implements Copyable<Tempo> {

    // The default tempo produced by the no-arg constructor. Note that
    // DEFAULT_DESCRIPTION is persisted to MusicXML and read back verbatim, so it is
    // deliberately not a localized string.

    public static final int DEFAULT_BPM = 120;
    public static final Duration DEFAULT_TYPE = Duration.CROTCHET;
    public static final String DEFAULT_DESCRIPTION = "Moderate";
    public static final boolean DEFAULT_SHOW_TEMPO = true;

    private int visibleTempo;
    private Duration tempoType;
    private String tempoDescription;
    private boolean showTempo;

    public Tempo() {
        this(DEFAULT_BPM, DEFAULT_TYPE, DEFAULT_DESCRIPTION, DEFAULT_SHOW_TEMPO);
    }

    public Tempo(
        int tempo,
        Duration tempoType,
        String tempoDescription,
        boolean showTempo
    ) {
        visibleTempo = tempo;
        this.tempoType = tempoType;
        this.tempoDescription = tempoDescription;
        this.showTempo = showTempo;
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

    public String getTempoDescription() {
        return tempoDescription;
    }

    public void setTempoDescription(String tempoDescription) {
        this.tempoDescription = tempoDescription;
    }

    public int getRealTempo() {
        return ((visibleTempo * tempoType.getNote().getDuration()) / MidiSequenceBuilder.PPQ);
    }

    public boolean shouldShowTempo() {
        return showTempo;
    }

    public void setShowTempo(boolean showTempo) {
        this.showTempo = showTempo;
    }

    /**
     * Returns a deep copy of this tempo, so a copy and its original hold independent state
     * even if a future mutator starts changing this tempo's fields in place.
     *
     * @return a tempo with the same values, sharing no state with this one
     */
    @Override
    public Tempo copy() {
        return new Tempo(visibleTempo, tempoType, tempoDescription, showTempo);
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
            && a.tempoDescription.equals(b.tempoDescription)
            && a.showTempo == b.showTempo;
    }

    /**
     * Whether two tempos — either of which may be null — define the same beat.
     *
     * <p>The beat is the tempo <em>type</em> alone. It is what beams group against and what a
     * tuplet is measured in, so changing it revalidates the notation of the whole song. The BPM,
     * the description and the show-tempo flag say how the tempo is displayed and leave the
     * notation underneath untouched.
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
