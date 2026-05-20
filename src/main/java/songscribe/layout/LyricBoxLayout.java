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
 * Per-element lyric text placement produced by the layout pass and consumed by the renderer.
 * <p>
 * Coordinates are in staff spaces, relative to the line's left edge. The box's X is the
 * left edge of the rendered syllable (after centering against the owning note column); the
 * renderer reads the Y from {@link SongLayoutMetrics#verseYSsInLine(int)}.
 *
 * @param xSs        left edge of the syllable text in staff spaces
 * @param widthSs    rendered width of the syllable text in staff spaces
 * @param verseIndex 1-based verse number
 * @param text       syllable text
 */
public record LyricBoxLayout(double xSs, double widthSs, int verseIndex, String text) {}
