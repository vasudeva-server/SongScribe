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

/**
 * The up-or-down sense shared by a stem, a tie's curve, and a tuplet bracket.
 */
public enum Direction {
    UP,
    DOWN;

    public boolean isUp() {
        return this == UP;
    }

    public boolean isDown() {
        return this == DOWN;
    }

    public Direction opposite() {
        return this == UP ? DOWN : UP;
    }

    /**
     * Returns this direction as a multiplier for a distance measured upward, so that a caller
     * can move a stem or a bracket end the right way with one multiplication.
     *
     * @return {@code +1} for {@link #UP}, {@code -1} for {@link #DOWN}
     */
    public int sign() {
        return this == UP ? 1 : -1;
    }
}
