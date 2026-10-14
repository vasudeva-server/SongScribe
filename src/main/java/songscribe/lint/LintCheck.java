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
 * The algorithm of one {@link LintRule}.
 */
@FunctionalInterface
interface LintCheck {

    List<Violation> check(Song song);

    @FunctionalInterface
    interface ElementVisitor {

        void visit(StaffElement element, int position);
    }

    static void walk(Song song, ElementVisitor visitor) {
        var position = 0;

        for (var line : song.getLines()) {
            for (var element : line.getElements()) {
                visitor.visit(element, position);
                position++;
            }
        }
    }

    /** Hands violations found out of song order back in song order. */
    final class SongOrderedViolations {

        private record Positioned(int position, Violation violation) {
        }

        private final List<Positioned> found = new ArrayList<>();

        void add(int position, Violation violation) {
            found.add(new Positioned(position, violation));
        }

        List<Violation> inSongOrder() {
            return found.stream()
                        .sorted(Comparator.comparingInt(Positioned::position))
                        .map(Positioned::violation)
                        .toList();
        }
    }
}
