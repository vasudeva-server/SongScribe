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

import org.jspecify.annotations.Nullable;

/**
 * What a span being added does to the same-kind spans already on its line — its kind's
 * displacement policy, read by {@link Span#addTo} through {@link Span#displacement()}.
 * <p>
 * "Same kind" is {@link Span#isSameKindAs}: the same concrete class, and for a hairpin the same
 * {@link Hairpin.Kind}, so a crescendo never displaces a diminuendo. Every displaced span leaves
 * through its own {@link Span#removeFrom}, recorded before the new span's addition so undo
 * restores the displaced spans after removing the new one.
 */
enum Displacement {

    /** Leaves every existing span in place: two spans of the kind may overlap freely. */
    COEXIST(null),

    /** Removes every same-kind span overlapping the new one before it is added. */
    REPLACE_OVERLAPPING(null),

    /**
     * Widens the new span over every same-kind span it overlaps and removes the spans the
     * widened range subsumes. Two spans written back to back stay two: they are two deliberate
     * groupings, not one gesture interrupted by an accident.
     */
    ABSORB_OVERLAPPING(0),

    /**
     * As {@link #ABSORB_OVERLAPPING}, and also absorbs a same-kind span within
     * {@link LineSpans#SPAN_ADJACENCY_REACH} of an endpoint: two spans that close describe one
     * uninterrupted gesture, so adding either merges them.
     */
    ABSORB_ADJACENT(LineSpans.SPAN_ADJACENCY_REACH);

    /** How far past an endpoint, in elements, an absorbed span may sit; null when nothing is absorbed. */
    private final @Nullable Integer reach;

    Displacement(@Nullable Integer reach) {
        this.reach = reach;
    }

    /**
     * How far past the new span's endpoints, in elements, an existing same-kind span may sit and
     * still be absorbed.
     *
     * @return {@code 0} for {@link #ABSORB_OVERLAPPING}, {@link LineSpans#SPAN_ADJACENCY_REACH}
     *     for {@link #ABSORB_ADJACENT}
     * @throws IllegalStateException for {@link #COEXIST} and {@link #REPLACE_OVERLAPPING},
     *     which absorb nothing
     */
    int absorbReach() {
        if (reach == null) {
            throw new IllegalStateException(this + " absorbs nothing");
        }

        return reach;
    }
}
