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
 * What should happen to a {@link Span} when it is asked to judge itself against a
 * pending {@link ElementChange} via {@link Span#outcomeFor(ElementChange)}.
 * <p>
 * Every span can answer {@link Keep} or {@link Remove}, and that is the whole of this type.
 * Keep carries no data. Remove carries the reason the span's rule gives, which is what the
 * user is told when the removal is reported. A hairpin alone can also survive an edit in a
 * different shape, so it answers the wider {@link HairpinOutcome} through
 * {@link Hairpin#hairpinOutcomeFor(ElementChange)}; the split is what makes a
 * {@link Reshape} from any other span kind unwriteable rather than merely unexpected.
 */
public sealed interface SpanOutcome permits SpanOutcome.Keep, SpanOutcome.Remove {

    /**
     * The span is unaffected.
     */
    enum Keep implements SpanOutcome, HairpinOutcome {
        INSTANCE
    }

    /**
     * The span cannot survive the change and must be removed.
     *
     * @param reasonKey the {@code Strings} key of the reason the span's rule gives, whose value
     *                  is the plain text the report shows for the removal
     */
    record Remove(String reasonKey) implements SpanOutcome, HairpinOutcome { }

    /**
     * What should happen to a {@link Hairpin}: {@link Keep}, {@link Remove}, or a
     * {@link Reshape}.
     */
    sealed interface HairpinOutcome permits Keep, Remove, Reshape { }

    /**
     * The hairpin survives over {@code range}, a range over the change's
     * {@link ElementChange#projectedElements}, not over the pre-change line.
     */
    record Reshape(ElementRange range) implements HairpinOutcome { }
}
