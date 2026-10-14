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
 * <p>Both values carry the meaning {@link Line#getKey()} gives them, as {@link Line#setKey}
 * settled on them after its no-op normalization, not the raw argument. A mid-line key change is
 * recorded as an ordinary element mutation instead.
 */
public record LineKeyChange(Line line, @Nullable Key oldKey, @Nullable Key newKey)
    implements Mutation, LineScopedMutation {

    @Override
    public Line getLine() {
        return line;
    }
}
