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
    private Type tempoType;
    private String tempoDescription;
    private boolean showTempo;

    public Tempo() {
        this(120, Type.CROTCHET, "Moderate", true);
    }

    public Tempo(
        int tempo,
        Type tempoType,
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

    public Type getTempoType() {
        return tempoType;
    }

    public void setTempoType(Type tempoType) {
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

    public enum Type {
        SEMI_BREVE(ElementType.SEMIBREVE.newInstance()), // Whole note
        MINIM_DOTTED(ElementType.MINIM.newInstance()), // Dotted half note
        MINIM(ElementType.MINIM.newInstance()), // Half note
        CROTCHET_DOTTED(ElementType.CROTCHET.newInstance()), // Dotted quarter note
        CROTCHET(ElementType.CROTCHET.newInstance()), // Quarter note
        QUAVER_DOTTED(ElementType.QUAVER.newInstance()), // Dotted eighth note
        QUAVER(ElementType.QUAVER.newInstance()), // Eighth note

        // IO values
        SEMIBREVE(Type.SEMI_BREVE),
        MINIMDOTTED(Type.MINIM_DOTTED),
        CROTCHETDOTTED(Type.CROTCHET_DOTTED),
        QUAVERDOTTED(Type.QUAVER_DOTTED);

        private final StaffElement note;

        static {
            MINIM_DOTTED.note.setDotCount(1);
            MINIM_DOTTED.note.setStaffPosition(1);
            CROTCHET_DOTTED.note.setDotCount(1);
            CROTCHET_DOTTED.note.setStaffPosition(1);
            QUAVER_DOTTED.note.setDotCount(1);
            QUAVER_DOTTED.note.setStaffPosition(1);
        }

        Type(StaffElement note) {
            this.note = note;
        }

        Type(Tempo.Type type) {
            note = type.note;
        }

        public StaffElement getNote() {
            return note;
        }
    }
}
