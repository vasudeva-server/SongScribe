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

package songscribe.message.mutation;

import songscribe.dom.Line;
import songscribe.dom.NoteElement;

/**
 * Mutation recording that a note was tied to the next note. The tie's end is derived, not
 * recorded — see {@link NoteElement#tie()}.
 *
 * @param line   the line the tie was recorded on, which is {@code anchor}'s line
 * @param anchor the note that became tied
 */
public record TieAddition(Line line, NoteElement anchor)
    implements Mutation, LineScopedMutation {

    @Override
    public Line getLine() {
        return line;
    }
}
