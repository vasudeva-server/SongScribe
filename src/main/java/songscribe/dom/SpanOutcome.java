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
 * What should happen to a {@link Span} when a pending {@link ElementChange} lands on its line:
 * the answer {@link Span#outcomeFor(ElementChange)} gives, and the one answer the sweep acts on.
 * <p>
 * The span survives untouched ({@link Keep}), is removed ({@link Remove}), or survives over a
 * different range ({@link Reshape}). Keep carries no data. Remove carries the reason the span's
 * rule gives, which is what the user is told when the removal is reported. Reshape names the
 * hairpin it reshapes, because a hairpin is the only span kind an edit reshapes, so no other kind
 * can write one.
 */
public sealed interface SpanOutcome permits SpanOutcome.Keep, SpanOutcome.Remove, SpanOutcome.Reshape {

    /**
     * The span is unaffected.
     */
    enum Keep implements SpanOutcome {
        INSTANCE
    }

    /**
     * The span cannot survive the change and must be removed.
     *
     * @param reasonKey the {@code Strings} key of the reason the span's rule gives, whose value
     *                  is the plain text the report shows for the removal
     */
    record Remove(String reasonKey) implements SpanOutcome { }

    /**
     * The hairpin survives the change over {@code range}. A hairpin is the only span kind an
     * edit reshapes, and only {@link Hairpin#outcomeFor} answers this.
     *
     * @param hairpin the hairpin that survives, the one that was asked
     * @param range   the range it survives over, a range over the change's
     *                {@link ElementChange#projectedElements}, not over the pre-change line
     */
    record Reshape(Hairpin hairpin, ElementRange range) implements SpanOutcome { }
}
