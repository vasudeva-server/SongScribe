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

package songscribe.ui.component.score;

/**
 * Result of a hit test against selectable elements in a {@link LineComponent}.
 * <p>
 * The cascade tests note heads first, then glissandos, then staff-line proximity.
 * If nothing is hit, {@link Nothing} is returned.
 */
sealed interface HitResult {
    record NoteHead(int index) implements HitResult {}
    record Glissando(int noteIndex) implements HitResult {}
    record StaffLine() implements HitResult {}
    record Nothing() implements HitResult {}
}
