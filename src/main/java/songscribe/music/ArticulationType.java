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

package songscribe.music;

/**
 * Types of articulations that can be applied to notes.
 * <p>
 * Articulations are drawn outward from the note head:
 * <ul>
 *   <li>For downward stems: articulations go down from head (ascending enum order)</li>
 *   <li>For upward stems: articulations go up from head (descending enum order)</li>
 * </ul>
 * <p>
 * The enum order determines drawing priority - closest to note head first.
 */
public enum ArticulationType {

    /**
     * Staccato - short, detached notes.
     * Drawn closest to the note head.
     */
    STACCATO,

    /**
     * Accent - emphasized notes.
     * Drawn outside of staccato when both are present.
     */
    ACCENT;

    /**
     * Returns the articulation types in order for drawing on stem side.
     * For stems pointing down, articulations are drawn below the head (ascending order).
     * For stems pointing up, articulations are drawn above the head (descending order).
     *
     * @param stemUp true if stem points up, false if stem points down
     * @return Array of articulation types in drawing order (closest to head first)
     */
    public static ArticulationType[] getDrawingOrder(boolean stemUp) {
        var values = values();

        if (stemUp) {
            // Reverse order for upward stems (articulations above head)
            var reversed = new ArticulationType[values.length];

            for (int i = 0; i < values.length; i++) {
                reversed[i] = values[values.length - 1 - i];
            }

            return reversed;
        }

        // Normal order for downward stems (articulations below head)
        return values.clone();
    }
}
