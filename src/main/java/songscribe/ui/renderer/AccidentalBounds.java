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

package songscribe.ui.renderer;

/**
 * Vertical and horizontal extent of a note's accidental (including parentheses) in staff-space units.
 *
 * <p>Coordinates are relative to the notehead glyph origin:
 * <ul>
 *   <li>{@code leftSs} — left edge X, measured from the notehead glyph origin (typically negative)</li>
 *   <li>{@code widthSs} — total horizontal span of the accidental drawing</li>
 *   <li>{@code topSs} — top edge Y, relative to the note center (Y-down; typically negative)</li>
 *   <li>{@code botSs} — bottom edge Y, relative to the note center (Y-down; typically positive)</li>
 * </ul>
 *
 * @param leftSs  left edge X relative to notehead glyph origin (staff spaces)
 * @param widthSs horizontal span of the accidental (staff spaces)
 * @param topSs   top edge Y relative to note center, Y-down (staff spaces)
 * @param botSs   bottom edge Y relative to note center, Y-down (staff spaces)
 */
public record AccidentalBounds(double leftSs, double widthSs, double topSs, double botSs) {}
