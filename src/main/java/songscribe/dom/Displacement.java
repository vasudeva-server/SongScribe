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

/**
 * What a span being added does to the same-kind spans already on its line, read by
 * {@link Span#addTo} through {@link Span#displacement()}.
 * <p>
 * Every displaced span leaves through its own recorded removal, before the new span's addition,
 * so undo restores the displaced spans after removing the new one. A span displaced under
 * {@link #REPLACE_OVERLAPPING} leaves through {@link Span#removeReported}, since it is lost
 * rather than merged; a span absorbed under {@link #ABSORB_OVERLAPPING} or
 * {@link #ABSORB_ADJACENT} leaves through {@link Span#removeFrom}, since the widened span covers it.
 */
enum Displacement {

    COEXIST,

    REPLACE_OVERLAPPING,

    /**
     * Two spans written back to back stay two: they are deliberate groupings.
     */
    ABSORB_OVERLAPPING,

    ABSORB_ADJACENT
}
