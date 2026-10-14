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
 * What an edit operation did, which tells its caller whether the user still owes an explanation
 * and whether anything was committed.
 *
 * <p>Three-way rather than a boolean because a refusal for want of horizontal room already puts
 * an error dialog on screen; a caller that beeped on every falsy outcome would beep the moment
 * the user dismisses that dialog.
 */
public enum EditResult {

    /** The line was modified, in exactly one undo step. */
    MODIFIED,

    /** Nothing was modified and nothing was said, so the caller owes the user feedback. */
    REFUSED,

    /** Nothing was modified and an error dialog already told the user why; the caller adds no feedback. */
    REPORTED;

    public static EditResult of(boolean modified) {
        return modified ? MODIFIED : REFUSED;
    }
}
