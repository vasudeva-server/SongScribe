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
 * Mutation recording that a span was removed from a line's span registry. The span is carried
 * whole, and undo adds that same instance back through {@link Span#addTo()}, while redo removes
 * it again through {@link Span#removeFrom()}, so identity survives a round trip.
 *
 * <p>The line is recorded rather than derived from the span because subscribers read it after
 * the edit, through {@link #getLine()}, and the edit that removed the span may also have
 * deleted its anchor, which then belongs to no line.
 *
 * @param line the line whose registry the span left, which was its anchor's line at the time
 * @param span the span that was removed
 */
public record SpanRemoval(Line line, Span<?> span)
    implements Mutation, LineScopedMutation {

    @Override
    public Line getLine() {
        return line;
    }
}
