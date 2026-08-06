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

package songscribe.message.mutation;

import songscribe.dom.Span;

/**
 * Marker interface for mutations that add or remove a span. Mutations carrying a span
 * implement this interface; the rest do not.
 * <p>
 * The companion of {@link LineScopedMutation} for the second question a subscriber asks of
 * a span mutation. A line-scoped mutation names one line, but a tie whose two notes sit in
 * different lines changes what <em>both</em> lines draw, so a repaint has to ask the span
 * itself which lines it reaches. Declaring that here rather than pattern-matching the
 * mutation types at each call site means a span mutation added later is answered by every
 * such caller the moment it is written, instead of falling through a {@code default} arm
 * that quietly reports no span at all.
 */
@FunctionalInterface
public interface SpanMutation {
    Span getSpan();
}
