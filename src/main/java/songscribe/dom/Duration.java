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

public enum Duration {
    SEMI_BREVE(ElementType.SEMIBREVE.newInstance()), // Whole note
    MINIM_DOTTED(ElementType.MINIM.newInstance()), // Dotted half note
    MINIM(ElementType.MINIM.newInstance()), // Half note
    CROTCHET_DOTTED(ElementType.CROTCHET.newInstance()), // Dotted quarter note
    CROTCHET(ElementType.CROTCHET.newInstance()), // Quarter note
    QUAVER_DOTTED(ElementType.QUAVER.newInstance()), // Dotted eighth note
    QUAVER(ElementType.QUAVER.newInstance()); // Eighth note

    private final StaffElement note;

    static {
        MINIM_DOTTED.note.setDotCount(1);
        MINIM_DOTTED.note.setStaffPosition(1);
        CROTCHET_DOTTED.note.setDotCount(1);
        CROTCHET_DOTTED.note.setStaffPosition(1);
        QUAVER_DOTTED.note.setDotCount(1);
        QUAVER_DOTTED.note.setStaffPosition(1);
    }

    Duration(StaffElement note) {
        this.note = note;
    }

    public StaffElement getNote() {
        return note.clone();
    }
}
