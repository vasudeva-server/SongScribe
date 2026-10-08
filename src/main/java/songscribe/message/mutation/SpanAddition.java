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
 * Mutation recording that a span was added to a line's span registry. The span is carried
 * whole, and undo removes that same instance through {@link Span#removeFrom()}, while redo adds
 * it back through {@link Span#addTo()}, so identity survives a round trip.
 *
 * <p>The line is recorded rather than derived from the span because subscribers read it after
 * the edit, through {@link #getLine()}, and by then the span's anchor may no longer be in a line.
 *
 * @param line the line whose registry the span joined, which was its anchor's line at the time
 * @param span the span that was added
 */
public record SpanAddition(Line line, Span<?> span)
    implements Mutation, LineScopedMutation {

    @Override
    public Line getLine() {
        return line;
    }
}
