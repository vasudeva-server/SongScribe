/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.ui.hit;

/**
 * Result of a hit test against the selectable elements of a single staff line.
 */
public sealed interface HitResult {
    record ElementHead(int index) implements HitResult {}

    record Slide(int elementIndex) implements HitResult {}

    record Hairpin(songscribe.dom.Hairpin hairpin) implements HitResult {}

    record Ending(songscribe.layout.Ending ending) implements HitResult {}

    record GraceGlissando() implements HitResult {}

    record StaffLine() implements HitResult {}

    record Nothing() implements HitResult {}
}
