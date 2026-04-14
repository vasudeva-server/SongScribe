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
package songscribe.music;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import org.jspecify.annotations.Nullable;

public class SpanSet<T extends Span> {

    private final LinkedList<T> spans = new LinkedList<>();

    @Nullable
    @SuppressWarnings("unchecked")
    public T addSpan(int start, int end) {
        return addSpan(start, end, null);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public T addSpan(int start, int end, @Nullable String data) {
        if (start >= end) {
            return null;
        }

        // Determine the new span
        var startSpan = findSpan(start);
        var endSpan = findSpan(end);
        var newSpan = (T) new Span(
            (startSpan != null) ? startSpan.start : start,
            (endSpan != null) ? endSpan.end : end,
            data
        );

        // Remove overlaps
        removeOverlaps(newSpan);

        // Add the new span
        spans.addFirst(newSpan);
        return newSpan;
    }

    @Nullable
    public T addSpan(T span) {
        if (span.start >= span.end) {
            return null;
        }

        // Expand bounds if overlapping spans exist
        var startSpan = findSpan(span.start);
        var endSpan = findSpan(span.end);

        if (startSpan != null) {
            span.start = startSpan.start;
        }

        if (endSpan != null) {
            span.end = endSpan.end;
        }

        // Remove overlaps
        removeOverlaps(span);

        // Add the span
        spans.addFirst(span);
        return span;
    }

    public void removeSpan(int start, int end) {
        var startSpan = findSpan(start);
        var endSpan = findSpan(end);

        //noinspection ObjectEquality
        if ((startSpan != null) && (startSpan == endSpan)) {
            @SuppressWarnings("unchecked")
            var endSpanCopy = (T) endSpan.copyRange(endSpan.start, endSpan.end);
            endSpan = endSpanCopy;
            spans.addFirst(endSpan);
        }

        if (startSpan != null) {
            startSpan.end = start;
        }

        if (endSpan != null) {
            endSpan.start = end;
        }

        // Check if any became zero-length
        removeIfInvalid(startSpan);
        removeIfInvalid(endSpan);

        removeOverlaps(start, end);
    }

    public void removeSpan(T span) {
        spans.remove(span);
    }

    public ListIterator<T> listIterator() {
        return spans.listIterator();
    }

    @Nullable
    public T findSpan(int index) {
        return spans
            .stream()
            .filter(
                span -> (span.start <= index) && (index <= span.end)
            )
            .findFirst()
            .orElse(null);
    }

    /**
     * Returns all spans that overlap [begin, end] inclusive. The returned list is a
     * snapshot — safe to iterate while removing spans from this set.
     */
    public List<T> findOverlapping(int begin, int end) {
        var result = new ArrayList<T>();

        for (var iter = spans.listIterator(); iter.hasNext(); ) {
            var span = iter.next();

            if (span.start <= end && span.end >= begin) {
                result.add(span);
            }
        }

        return result;
    }

    public boolean isStartOfAnySpan(int index) {
        return spans.stream().anyMatch(span -> span.start == index);
    }

    public boolean isEndOfAnySpan(int index) {
        return spans.stream().anyMatch(span -> span.end == index);
    }

    public boolean isInsideAnySpan(int index) {
        return findSpan(index) != null;
    }

    public void shiftValues(int from, int shift) {
        for (var iter = spans.listIterator(); iter.hasNext();) {
            var span = iter.next();

            if (span.start >= from) {
                span.start += shift;
            }

            if (span.end >= from) {
                span.end += shift;
            }

            if (span.start >= span.end) {
                iter.remove();
            }
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isEmpty() {
        return spans.isEmpty();
    }

    public SpanSet<T> copySpan(int start, int end) {
        var result = new SpanSet<T>();

        for (var span : spans) {
            @SuppressWarnings("unchecked")
            var copy = (T) span.copyRange(span.start, span.end);
            result.spans.addFirst(copy);
        }

        result.removeSpan(Integer.MIN_VALUE, start);
        result.removeSpan(end, Integer.MAX_VALUE);
        return result;
    }

    private void removeIfInvalid(@Nullable T span) {
        if ((span != null) && (span.start >= span.end)) {
            spans.remove(span);
        }
    }

    private void removeOverlaps(T bigSpan) {
        removeOverlaps(bigSpan.start, bigSpan.end);
    }

    private void removeOverlaps(int start, int end) {
        spans.removeIf(
            span -> (start <= span.start) && (span.end <= end)
        );
    }
}
