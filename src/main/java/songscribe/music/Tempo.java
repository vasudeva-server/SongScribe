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

import songscribe.ui.ProfileManager;
import songscribe.ui.component.MainFrame;
import songscribe.ui.playback.PlaybackController;

public class Tempo {

    private int visibleTempo;
    private Type tempoType;
    private String tempoDescription;
    private boolean showTempo;

    public static Tempo getTempoFromProfile() {
        var pm = MainFrame.getInstance().getProfileManager();

        return new Tempo(
            Integer.parseInt(
                pm.getDefaultProperty(ProfileManager.ProfileKey.TEMPO)
            ),
            Tempo.Type.valueOf(
                pm.getDefaultProperty(ProfileManager.ProfileKey.TEMPO_TYPE)
            ),
            pm.getDefaultProperty(ProfileManager.ProfileKey.TEMPO_DESCRIPTION),
            true
        );
    }

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
        return ((visibleTempo * tempoType.getNote().getDuration()) / PlaybackController.PPQ);
    }

    public boolean shouldShowTempo() {
        return showTempo;
    }

    public void setShowTempo(boolean showTempo) {
        this.showTempo = showTempo;
    }

    public enum Type {
        SEMI_BREVE(NoteType.SEMIBREVE.newInstance()), // Whole note
        MINIM_DOTTED(NoteType.MINIM.newInstance()), // Dotted half note
        MINIM(NoteType.MINIM.newInstance()), // Half note
        CROTCHET_DOTTED(NoteType.CROTCHET.newInstance()), // Dotted quarter note
        CROTCHET(NoteType.CROTCHET.newInstance()), // Quarter note
        QUAVER_DOTTED(NoteType.QUAVER.newInstance()), // Dotted eighth note
        QUAVER(NoteType.QUAVER.newInstance()), // Eighth note

        // IO values
        SEMIBREVE(Type.SEMI_BREVE),
        MINIMDOTTED(Type.MINIM_DOTTED),
        CROTCHETDOTTED(Type.CROTCHET_DOTTED),
        QUAVERDOTTED(Type.QUAVER_DOTTED);

        private final Note note;

        static {
            MINIM_DOTTED.note.setDotCount(1);
            MINIM_DOTTED.note.setYPos(1);
            CROTCHET_DOTTED.note.setDotCount(1);
            CROTCHET_DOTTED.note.setYPos(1);
            QUAVER_DOTTED.note.setDotCount(1);
            QUAVER_DOTTED.note.setYPos(1);
        }

        Type(Note note) {
            this.note = note;
        }

        Type(Tempo.Type type) {
            note = type.note;
        }

        public Note getNote() {
            return note;
        }
    }
}
