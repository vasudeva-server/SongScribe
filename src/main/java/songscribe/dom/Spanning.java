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

import java.util.List;

import songscribe.Strings;

/**
 * The answer a span kind's factory gives for a range of a line: the span over it, or the reason
 * there is none.
 * <p>
 * A refusal names its {@link Cause} as well as its reason, because a caller acts on the two
 * differently. A span is built over the range it will hold once added, which for an absorbing
 * kind is wider than the range asked about, so {@link Spanned} names the same-kind spans that
 * range absorbs.
 */
public sealed interface Spanning<S extends Span<?>> permits Spanning.Spanned, Spanning.Refused {

    /**
     * @param span     built but not added to its line
     * @param absorbed in no defined order; empty when it absorbs none
     */
    record Spanned<S extends Span<?>>(S span, List<S> absorbed) implements Spanning<S> {}

    /**
     * @param reasonKey  a {@code Strings} key whose value is plain text the user reads
     * @param headingKey the one the refused kind's {@link Span#reportHeadingKey} returns; its
     *                   value is a MessageFormat pattern taking a count as {@code {0}}
     */
    record Refused<S extends Span<?>>(String reasonKey, Cause cause, String headingKey) implements Spanning<S> {

        public RecordedChange reportedAt(ChangeLines where) {
            return new RecordedChange(headingKey, Strings.get(reasonKey), where);
        }
    }

    enum Cause {

        /** The requested range cannot hold the span, whatever else is on the line. */
        RANGE,

        /** A span already on the line is the obstacle. */
        OTHER_SPAN
    }
}
