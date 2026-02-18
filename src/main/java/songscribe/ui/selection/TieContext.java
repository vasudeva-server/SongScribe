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

package songscribe.ui.selection;

import songscribe.data.IntervalSet;

/**
 * Context information about whether a tie can be toggled for the current selection.
 *
 * @param canToggle Whether the selection can toggle a tie
 * @param intervals The IntervalSet if the selection is part of an existing tie, null otherwise
 */
public record TieContext(
    boolean canToggle,
    IntervalSet intervals
) {}
