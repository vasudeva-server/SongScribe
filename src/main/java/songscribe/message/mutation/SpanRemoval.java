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
import songscribe.dom.Span;

/**
 * Mutation recording that a span was removed from a line's span registry. The same instance is
 * carried, so identity survives an undo and redo round trip.
 *
 * <p>The line is recorded rather than derived from the span because the edit may also have
 * deleted the span's anchor, which then belongs to no line.
 *
 * @param line the line whose registry the span left, which was its anchor's line at the time
 */
public record SpanRemoval(Line line, Span<?> span)
    implements Mutation, LineScopedMutation {

    @Override
    public Line getLine() {
        return line;
    }
}
