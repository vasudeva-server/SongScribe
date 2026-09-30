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
 * The answer a span kind's factory gives for a range of a line: the span over it, or the reason
 * there is none. {@link Ending#spanning} and {@link Hairpin#spanning} answer with it.
 * <p>
 * A refusal names its {@link Cause} as well as its reason, because a caller acts on the two
 * differently: the hairpin menu shows an item as "ineligible" for {@link Cause#RANGE}, where the
 * selected music cannot carry a hairpin, and as "blocked" for {@link Cause#OTHER_SPAN}, where a
 * hairpin already on the line is in the way.
 *
 * @param <S> the kind of span the range would hold
 */
public sealed interface Spanning<S extends Span<?>> permits Spanning.Spanned, Spanning.Refused {

    /**
     * The range holds the span.
     *
     * @param span the span over the range, built but not added to its line
     * @param <S>  the kind of span
     */
    record Spanned<S extends Span<?>>(S span) implements Spanning<S> {}

    /**
     * The range holds no span.
     *
     * @param reasonKey the {@code Strings} key of the reason, as the user reads it
     * @param cause     what stands in the way: the range itself, or a span already on the line
     * @param <S>       the kind of span the range would have held
     */
    record Refused<S extends Span<?>>(String reasonKey, Cause cause) implements Spanning<S> {}

    /** What a {@link Refused} range is refused for. */
    enum Cause {

        /** The requested range cannot hold the span, whatever else is on the line. */
        RANGE,

        /** A span already on the line is the obstacle. */
        OTHER_SPAN
    }
}
