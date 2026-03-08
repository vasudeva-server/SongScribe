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

package songscribe.ui;

/**
 * This enum represents the different modes that the user can be in when editing a song.
 */
public enum Mode {
    // Selecting notes or a staff line
    SELECT,

    // Editing notes
    EDIT,

    // Adjusting the horizontal position of notes
    ADJUSTMENT,

    // Adjusting the horizontal position of syllabified lyrics
    LYRICS_ADJUSTMENT,

    // Adjusting the vertical position of score elements
    VERTICAL_ADJUSTMENT;

    public boolean isAdjustmentMode() {
        return (
            (this == ADJUSTMENT) ||
                (this == VERTICAL_ADJUSTMENT) ||
                (this == LYRICS_ADJUSTMENT)
        );
    }
}
