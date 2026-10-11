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

package songscribe.lint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import songscribe.dom.Song;
import songscribe.dom.StaffElement;

/**
 * The algorithm of one {@link LintRule}: finds every place the song breaks the rule.
 */
@FunctionalInterface
interface LintCheck {

    /**
     * Finds every violation of this check's rule in {@code song}.
     *
     * @param song the song to examine; read only, never changed
     * @return the violations, in song order; empty when the song keeps the rule
     */
    List<Violation> check(Song song);

    /** Receives one element of a {@link #walk} with its position in song order. */
    @FunctionalInterface
    interface ElementVisitor {

        void visit(StaffElement element, int position);
    }

    /**
     * Visits every element of {@code song} once, in song order: line by line, each line's
     * elements in order, the terminal included.
     *
     * @param visitor receives each element with its position, which counts from 0 across the
     *                whole song and so orders elements of different lines
     */
    static void walk(Song song, ElementVisitor visitor) {
        var position = 0;

        for (var line : song.getLines()) {
            for (var element : line.getElements()) {
                visitor.visit(element, position);
                position++;
            }
        }
    }

    /**
     * Collects violations found out of song order, as a check does when it can tell only later
     * in its pass that an earlier element is at fault, and hands them back in song order.
     */
    final class SongOrderedViolations {

        private record Positioned(int position, Violation violation) {
        }

        private final List<Positioned> found = new ArrayList<>();

        /**
         * Adds a violation found on the element at {@code position}.
         *
         * @param position the element's position from {@link #walk}
         */
        void add(int position, Violation violation) {
            found.add(new Positioned(position, violation));
        }

        /**
         * The violations added so far.
         *
         * @return the violations ordered by the position of their element; violations on one
         *         element keep the order they were added in
         */
        List<Violation> inSongOrder() {
            return found.stream()
                        .sorted(Comparator.comparingInt(Positioned::position))
                        .map(Positioned::violation)
                        .toList();
        }
    }
}
