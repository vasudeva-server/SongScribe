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

package songscribe.dom;

/**
 * Where one endpoint of a span sits <i>relative to the line that was asked</i>.
 * <p>
 * A span's endpoint is not always a position in the asking line. A tie may straddle a line
 * boundary, with its anchor in one line and its end in the next; each line owns one endpoint
 * and must say of the other only which side of itself it fell off. A type rather than a sentinel {@code int} because the answer is fed straight
 * into comparisons and, in {@link Line}'s span merge, into arithmetic and
 * {@code List.get}: a sentinel number silently wins a {@code min()} and then throws, while a
 * non-{@link At} case here fails to compile until the caller narrows it.
 *
 * @see SpanLookup#anchorIndexOf
 */
public sealed interface SpanBound {

    /** The endpoint is in the asking line, at element {@code index}. */
    record At(int index) implements SpanBound {

        @Override
        public boolean atOrBefore(int queryIndex) {
            return index <= queryIndex;
        }

        @Override
        public boolean atOrAfter(int queryIndex) {
            return index >= queryIndex;
        }

        @Override
        public int indexOr(int fallback) {
            return index;
        }
    }

    /**
     * The three answers that name no position in the asking line. An enum so each is a
     * singleton: resolving an endpoint allocates only for {@link At}.
     */
    enum Unpositioned implements SpanBound {

        /**
         * The endpoint is in an earlier line of the song, so this line's half of the span
         * enters through its left edge and covers every element from index 0 onward.
         */
        BEFORE_LINE,

        /**
         * The endpoint is in a later line of the song, so this line's half of the span exits
         * through its right edge and covers every element up to the last.
         */
        AFTER_LINE,

        /**
         * The endpoint has no position and no direction: it is unset, it belongs to no line,
         * or it belongs to a line the song no longer holds. Nothing can be said about which
         * elements the span covers here, so a containment query rejects it outright.
         */
        ABSENT;

        @Override
        public boolean atOrBefore(int queryIndex) {
            return this == BEFORE_LINE;
        }

        @Override
        public boolean atOrAfter(int queryIndex) {
            return this == AFTER_LINE;
        }

        @Override
        public boolean isAbsent() {
            return this == ABSENT;
        }

        @Override
        public int indexOr(int fallback) {
            return fallback;
        }
    }

    /** @see Unpositioned#BEFORE_LINE */
    SpanBound BEFORE_LINE = Unpositioned.BEFORE_LINE;

    /** @see Unpositioned#AFTER_LINE */
    SpanBound AFTER_LINE = Unpositioned.AFTER_LINE;

    /** @see Unpositioned#ABSENT */
    SpanBound ABSENT = Unpositioned.ABSENT;

    /**
     * Whether this bound sits at or before {@code queryIndex} in the asking line.
     * {@link Unpositioned#BEFORE_LINE} is before every index; {@link Unpositioned#AFTER_LINE}
     * is after every one; {@link Unpositioned#ABSENT} has no position, so it is neither.
     */
    boolean atOrBefore(int queryIndex);

    /**
     * Whether this bound sits at or after {@code queryIndex} in the asking line — the mirror
     * of {@link #atOrBefore}, and {@link Unpositioned#ABSENT} is likewise neither.
     */
    boolean atOrAfter(int queryIndex);

    /**
     * Whether this bound names element {@code queryIndex} of the asking line exactly. Only an
     * {@link At} can: a bound off either edge of that line, or with no position at all, names
     * no element in it and so equals no queried index.
     * <p>
     * Being at or before a position and at or after the same one leaves only being at it, so
     * this is the conjunction of the two predicates above rather than a third case for each
     * implementation to get right on its own.
     */
    default boolean isAt(int queryIndex) {
        return atOrBefore(queryIndex) && atOrAfter(queryIndex);
    }

    /**
     * Whether this bound names no position at all, as opposed to a position off one edge of
     * the asking line. Two callers ask. {@link Span#overlapping} must keep reporting a span
     * whose anchor no longer resolves, so the removal sweeps can still find it and clean it
     * up; {@link Tie#isInvalidatedByInsertion} and its replacement counterpart must do the
     * opposite and decline to judge such a tie at all, since a bound with no position bounds
     * nothing.
     */
    default boolean isAbsent() {
        return false;
    }

    /**
     * The position this bound names, or {@code fallback} when it names none.
     * <p>
     * The one place a bound is allowed to become a plain {@code int}, so that every caller
     * that needs a number states in one expression what it wants for the off-edge and absent
     * cases. What the right fallback is depends entirely on the caller — this line's first
     * index, its last, or the un-merged position a merge started from — so it is a parameter
     * rather than a fixed value here.
     * <p>
     * A caller that must distinguish the three non-{@link At} cases from each other cannot
     * use this: it should pattern-match on {@link At} directly, as
     * {@link Line#anchorIndexOf}'s callers in the layout and export paths do.
     */
    int indexOr(int fallback);
}
