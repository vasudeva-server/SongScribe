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
package songscribe.music;

import songscribe.midi.MidiSequenceBuilder;

public class Tempo {

    private int visibleTempo;
    private Duration tempoType;
    private String tempoDescription;
    private boolean showTempo;

    public Tempo() {
        this(120, Duration.CROTCHET, "Moderate", true);
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

}
