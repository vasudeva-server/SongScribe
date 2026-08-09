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
 * What should happen to a {@link Span} when it is asked to judge itself against a
 * pending {@link ElementChange} via {@link Span#outcomeFor(ElementChange, Line)}.
 */
public sealed interface SpanOutcome {

    /**
     * The two outcomes that carry no data.
     * <p>
     * An enum rather than a pair of stateless records: there is exactly one of each,
     * which is what "carries no data" means, and only an enum can say so — a record's
     * canonical constructor cannot be narrowed, so any caller could mint another. A
     * {@code switch} over {@link SpanOutcome} still reads flat and is still checked for
     * exhaustiveness, since a qualified enum constant is a legal case label for a
     * sealed selector type.
     */
    enum Simple implements SpanOutcome {
        /** The span is unaffected. */
        KEEP,

        /** The span cannot survive the change and must be removed. */
        REMOVE
    }

    /**
     * The span survives over {@code [begin, end]}, as indices into the change's
     * {@link ElementChange#projectedElements}, not into the pre-change line.
     */
    record Reshape(int begin, int end) implements SpanOutcome { }
}
