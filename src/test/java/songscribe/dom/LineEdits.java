/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.dom;

import songscribe.engraving.StaffPosition;
import songscribe.message.mutation.ElementField;

/** Edits to one note of a line, made the way the editor makes them, for a test that runs inside a modification bracket. */
final class LineEdits {

    private LineEdits() {}

    /**
     * Chooses {@code accidental} on the note at {@code index}.
     *
     * @param line       the line holding the note
     * @param index      the note's index in {@code line}
     * @param accidental the accidental to choose
     */
    static void choose(Line line, int index, Accidental accidental) {
        line.modifyElement(index, ElementField.ACCIDENTAL, () -> ((NoteElement) line.getElement(index)).choose(accidental));
    }

    /**
     * Moves the note at {@code index} to {@code staffPosition}.
     *
     * @param line          the line holding the note
     * @param index         the note's index in {@code line}
     * @param staffPosition the note's new staff position
     */
    static void move(Line line, int index, int staffPosition) {
        line.modifyElement(index, ElementField.PITCH,
            () -> ((NoteElement) line.getElement(index)).setStaffPosition(new StaffPosition(staffPosition)));
    }
}
