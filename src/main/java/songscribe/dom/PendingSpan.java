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

import songscribe.dom.StaffElementRun.ElementRange;

/**
 * A span as a reader found it in a file, before the load pass has judged it.
 * <p>
 * A reader records what the file said here, and {@link SpanLoadPass#run} turns each pending span
 * into a span on its anchor's line or drops it. A pending span is not a span: nothing outside the
 * load path sees one.
 */
public sealed interface PendingSpan<S extends Span<?>> permits PendingEnding, PendingHairpin {

    /**
     * @return in a line of the song being read
     */
    StaffElement anchor();

    /**
     * @return in the anchor's line at or after the anchor
     */
    StaffElement end();

    /**
     * @return in staff spaces, positive rightward
     */
    double xOffsetSs();

    /**
     * @return in staff spaces, positive downward
     */
    double yOffsetSs();

    Spanning<S> spanning(Line line, ElementRange range);
}
