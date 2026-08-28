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

package songscribe.smufl;

/**
 * The engraving measurements the font sets, in staff spaces.
 *
 * <p>Only the ones the application takes from the font are here. SMuFL declares others —
 * beam thickness and spacing among them — that this application deliberately does not
 * follow; see {@code engraving.LineThickness}, which sets beam geometry from LilyPond
 * instead. A component added here is a statement that the font decides that measurement.
 *
 * @param repeatBarlineDotSeparationSs gap between a repeat barline and its dots
 * @param legerLineThicknessSs         stroke width of a leger line
 * @param tieMidpointThicknessSs       thickness of a tie at its widest point
 */
public record EngravingDefaults(
    double repeatBarlineDotSeparationSs,
    double legerLineThicknessSs,
    double tieMidpointThicknessSs
) {}
