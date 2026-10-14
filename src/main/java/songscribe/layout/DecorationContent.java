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

package songscribe.layout;

/**
 * The typeset content of one above-staff decoration, positioned and measured once by the layout
 * pass that placed it. The renderer resolves no font, measures no advance and computes no position
 * of its own, so the drawn ink cannot disagree with the box layout measured.
 * <p>
 * Measurements are in staff spaces. Content is rebuilt by every layout pass and holds no cache.
 */
public sealed interface DecorationContent permits AttributionContent, MetronomeContent {}
