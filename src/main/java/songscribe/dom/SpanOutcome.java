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
 */
public sealed interface SpanOutcome permits SpanOutcome.Keep, SpanOutcome.Remove, SpanOutcome.Reshape {

    enum Keep implements SpanOutcome {
        INSTANCE
    }

    record Remove(String reasonKey) implements SpanOutcome { }

    /**
     * @param range over the change's {@link ElementChange#projectedElements}, not over the
     *              pre-change line
     */
    record Reshape(Hairpin hairpin, ElementRange range) implements SpanOutcome { }
}
