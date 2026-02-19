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
package songscribe.data;

import java.util.LinkedList;
import java.util.ListIterator;

import org.jetbrains.annotations.Nullable;

public class IntervalSet {

    private final LinkedList<Interval> intervals = new LinkedList<>();

    public Interval addInterval(int start, int end) {
        return addInterval(start, end, null);
    }

    @Nullable
    public Interval addInterval(int start, int end, String data) {
        if (start >= end) {
            return null;
        }

        // Determine the new interval
        var startInterval = findInterval(start);
        var endInterval = findInterval(end);
        var newInterval = new Interval(
            (startInterval != null) ? startInterval.start : start,
            (endInterval != null) ? endInterval.end : end,
            data
        );

        // Remove overlaps
        removeOverlaps(newInterval);

        // Add the new interval
        intervals.addFirst(newInterval);
        return newInterval;
    }

    public void removeInterval(int start, int end) {
        var startInterval = findInterval(start);
        var endInterval = findInterval(end);

        //noinspection ObjectEquality
        if ((startInterval != null) && (startInterval == endInterval)) {
            endInterval = new Interval(
                endInterval.start,
                endInterval.end,
                endInterval.data
            );
            intervals.addFirst(endInterval);
        }

        if (startInterval != null) {
            startInterval.end = start;
        }

        if (endInterval != null) {
            endInterval.start = end;
        }

        // Check if any became zero-interval
        removeIfInvalid(startInterval);
        removeIfInvalid(endInterval);

        removeOverlaps(start, end);
    }

    public void removeInterval(Interval interval) {
        intervals.remove(interval);
    }

    public ListIterator<Interval> listIterator() {
        return intervals.listIterator();
    }

    @Nullable
    public Interval findInterval(int index) {
        return intervals
            .stream()
            .filter(
                interval -> (interval.start <= index) && (index <= interval.end)
            )
            .findFirst()
            .orElse(null);
    }

    public boolean isStartOfAnyInterval(int index) {
        return intervals.stream().anyMatch(interval -> interval.start == index);
    }

    public boolean isEndOfAnyInterval(int index) {
        return intervals.stream().anyMatch(interval -> interval.end == index);
    }

    public boolean isInsideAnyInterval(int index) {
        return findInterval(index) != null;
    }

    public void shiftValues(int from, int shift) {
        for (var iter = intervals.listIterator(); iter.hasNext();) {
            var interval = iter.next();

            if (interval.start >= from) {
                interval.start += shift;
            }

            if (interval.end >= from) {
                interval.end += shift;
            }

            if (interval.start >= interval.end) {
                iter.remove();
            }
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isEmpty() {
        return intervals.isEmpty();
    }

    public IntervalSet copyInterval(int start, int end) {
        var result = new IntervalSet();

        for (var interval : intervals) {
            result.intervals.addFirst(
                new Interval(interval.start, interval.end, interval.data)
            );
        }

        result.removeInterval(Integer.MIN_VALUE, start);
        result.removeInterval(end, Integer.MAX_VALUE);
        return result;
    }

    private void removeIfInvalid(Interval interval) {
        if ((interval != null) && (interval.start >= interval.end)) {
            intervals.remove(interval);
        }
    }

    private void removeOverlaps(Interval bigInterval) {
        removeOverlaps(bigInterval.start, bigInterval.end);
    }

    private void removeOverlaps(int start, int end) {
        intervals.removeIf(
            interval -> (start <= interval.start) && (interval.end <= end)
        );
    }
}
