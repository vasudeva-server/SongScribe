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

public enum BeatChange {
    QUAVER_EQUALS_QUAVER(
        NoteType.QUAVER.newInstance(),
        NoteType.QUAVER.newInstance(),
        1f
    ),
    DOTTED_CROCHET_EQUALS_MINIM(
        createDottedVersion(NoteType.CROTCHET.newInstance()),
        NoteType.MINIM.newInstance(),
        3f / 4f
    ),
    MINIM_EQUALS_DOTTED_CROCHET(
        NoteType.MINIM.newInstance(),
        createDottedVersion(NoteType.CROTCHET.newInstance()),
        4f / 3f
    ),
    CROTCHET_EQUALS_DOTTED_CROCHET(
        NoteType.CROTCHET.newInstance(),
        createDottedVersion(NoteType.CROTCHET.newInstance()),
        2f / 3f
    ),
    DOTTED_CROCHET_EQUALS_CROCHET(
        createDottedVersion(NoteType.CROTCHET.newInstance()),
        NoteType.CROTCHET.newInstance(),
        3f / 2f
    );

    private final Note firstNote;
    private final Note secondNote;
    private final float tempoChange;

    BeatChange(Note firstNote, Note secondNote, float tempoChange) {
        this.firstNote = firstNote;
        this.secondNote = secondNote;
        this.tempoChange = tempoChange;
    }

    private static Note createDottedVersion(Note note) {
        note.setDotCount(1);
        note.setYPos(1);
        return note;
    }

    public Note getFirstNote() {
        return firstNote;
    }

    public Note getSecondNote() {
        return secondNote;
    }

    public float getTempoChange() {
        return tempoChange;
    }
}
