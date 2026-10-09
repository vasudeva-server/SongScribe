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
 * What a span being added does to the same-kind spans already on its line — its kind's
 * displacement policy, read by {@link Span#addTo} through {@link Span#displacement()}.
 * <p>
 * "Same kind" is {@link Span#isSameKindAs}: the same concrete class, which a subclass may narrow.
 * {@link Hairpin} narrows it to the same {@link Hairpin.Kind}, so a crescendo never displaces a
 * diminuendo. Every displaced span leaves through its own recorded removal, before the new span's
 * addition so undo restores the displaced spans after removing the new one. A span displaced under
 * {@link #REPLACE_OVERLAPPING} leaves through {@link Span#removeReported}, since it is lost rather than
 * merged into the new one; a span absorbed under {@link #ABSORB_OVERLAPPING} or
 * {@link #ABSORB_ADJACENT} leaves through {@link Span#removeFrom}, since the widened span covers it.
 */
enum Displacement {

    /** Leaves every existing span in place: two spans of the kind may overlap freely. */
    COEXIST,

    /**
     * Removes every same-kind span overlapping the new one before it is added, and reports each
     * removal with {@link songscribe.Strings#REPORT_REASON_REPLACED}.
     */
    REPLACE_OVERLAPPING,

    /**
     * Widens the new span over every same-kind span it overlaps and removes the spans the
     * widened range subsumes. Two spans written back to back stay two: they are two deliberate
     * groupings, not one gesture interrupted by an accident.
     */
    ABSORB_OVERLAPPING,

    /**
     * As {@link #ABSORB_OVERLAPPING}, and also absorbs a same-kind span within
     * {@link LineSpans#SPAN_ADJACENCY_REACH} of an endpoint: two spans that close describe one
     * uninterrupted gesture, so adding either merges them.
     */
    ABSORB_ADJACENT
}
