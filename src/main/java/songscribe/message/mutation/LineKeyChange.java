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

import org.jspecify.annotations.Nullable;

import songscribe.dom.Key;
import songscribe.dom.Line;

/**
 * Mutation recording a change to the key a line establishes at its own start.
 *
 * <p>Both values carry the meaning {@link Line#getKey()} gives them: a null key means the line
 * establishes nothing and inherits the key in effect at the end of the previous line. Both are
 * the values {@link Line#setKey} settled on after its no-op normalization, not the raw argument
 * it was handed, so replaying either one reproduces the recorded state exactly.
 *
 * <p>A mid-line key change is a {@code KeyChangeElement} and is recorded as an ordinary
 * element mutation instead; this record covers only the line's own key.
 *
 * @param line   the line whose key changed
 * @param oldKey the key the line established before the change, or null if it inherited
 * @param newKey the key the line establishes after the change, or null if it now inherits
 */
public record LineKeyChange(Line line, @Nullable Key oldKey, @Nullable Key newKey)
    implements Mutation, LineScopedMutation {

    @Override
    public Line getLine() {
        return line;
    }
}
