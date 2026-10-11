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
 * there is none. {@link Beam#spanning}, {@link Tuplet#spanning}, {@link Tuplet#spanningStated},
 * {@link Ending#spanning} and {@link Hairpin#spanning} answer with it.
 * <p>
 * A refusal names its {@link Cause} as well as its reason, because a caller acts on the two
 * differently: the hairpin menu shows an item as "ineligible" for {@link Cause#RANGE}, where the
 * selected music cannot carry a hairpin, and as "blocked" for {@link Cause#OTHER_SPAN}, where a
 * hairpin already on the line is in the way. A refusal also carries the report heading of the
 * kind it refuses, so a caller that drops the span reports the drop through
 * {@link Refused#reportedAt} without naming the kind itself.
 * <p>
 * Every kind answers with this one type. A span is built over the range it will hold once added,
 * which for an absorbing kind is wider than the range asked about, so {@link Spanned} names the
 * same-kind spans that range absorbs alongside the span. A kind that absorbs nothing names none:
 * an {@link Ending} coexists with every other ending, and a {@link Tuplet} replaces the tuplets
 * it overlaps when it is added rather than absorbing them.
 *
 * @param <S> the kind of span the range would hold
 */
public sealed interface Spanning<S extends Span<?>> permits Spanning.Spanned, Spanning.Refused {

    /**
     * The range holds the span.
     *
     * @param span     the span over the range it will hold once added, built but not added to its
     *                 line
     * @param absorbed every same-kind span already on the line that adding {@code span} absorbs,
     *                 in no defined order; empty when it absorbs none
     * @param <S>      the kind of span
     */
    record Spanned<S extends Span<?>>(S span, List<S> absorbed) implements Spanning<S> {}

    /**
     * The range holds no span.
     *
     * @param reasonKey  the {@code Strings} key of the reason, whose value is plain text the user
     *                   reads
     * @param cause      what stands in the way: the range itself, or a span already on the line
     * @param headingKey the {@code Strings} key of the report heading of the refused kind, the
     *                   one its {@link Span#reportHeadingKey} returns; its value is a
     *                   MessageFormat pattern taking a count as {@code {0}}
     * @param <S>        the kind of span the range would have held
     */
    record Refused<S extends Span<?>>(String reasonKey, Cause cause, String headingKey) implements Spanning<S> {

        /**
         * The report entry for dropping the span this refusal refused, on {@code where}.
         *
         * @param where the line the refused span would have lain on
         * @return an entry under {@link #headingKey}, whose description is the text of
         *     {@link #reasonKey} and whose place is {@code where}
         */
        public RecordedChange reportedAt(ChangeLines where) {
            return new RecordedChange(headingKey, Strings.get(reasonKey), where);
        }
    }

    /** What a {@link Refused} range is refused for. */
    enum Cause {

        /** The requested range cannot hold the span, whatever else is on the line. */
        RANGE,

        /** A span already on the line is the obstacle. */
        OTHER_SPAN
    }
}
