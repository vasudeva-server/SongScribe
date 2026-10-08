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
 * A span as a reader found it in a file, before the load pass has judged it.
 * <p>
 * A reader does not build the span: every span of a kind that answers with a {@link Spanning} is
 * well formed, and whether a range holds a well-formed one is that kind's factory's question, not
 * the reader's. So a reader records what the file said here, and {@link SpanLoadPass#run} — once
 * the song is complete — turns each pending span into a span on its anchor's line or drops it.
 * <p>
 * A pending span is not a span. It sits in no registry, is never drawn or recorded for undo, and
 * nothing outside the load path sees one.
 *
 * @param <S> the kind of span the file states
 */
public sealed interface PendingSpan<S extends Span<?>> permits PendingEnding, PendingHairpin {

    /**
     * Returns the span's first element.
     *
     * @return the span's first element, in a line of the song being read
     */
    StaffElement anchor();

    /**
     * Returns the span's last element.
     *
     * @return the span's last element, in the anchor's line at or after the anchor
     */
    StaffElement end();

    /**
     * Returns the user's horizontal offset of the span.
     *
     * @return the offset in staff spaces, positive rightward; it becomes the span's
     *     {@link LineElement#getUserXOffsetSs()}
     */
    double xOffsetSs();

    /**
     * Returns the user's vertical offset of the span.
     *
     * @return the offset in staff spaces, positive downward; it becomes the span's
     *     {@link LineElement#getUserYOffsetSs()}
     */
    double yOffsetSs();

    /**
     * Asks the kind's factory for the span this pending span states over {@code range} of
     * {@code line}.
     *
     * @param line  the anchor's line
     * @param range the range from the anchor's index to the end's index in {@code line}
     * @return the kind's answer for {@code range}: the span, built but not added, or the refusal
     */
    Spanning<S> spanning(Line line, ElementRange range);
}
