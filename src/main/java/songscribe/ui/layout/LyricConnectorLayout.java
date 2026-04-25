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

package songscribe.ui.layout;

/**
 * Line-level lyric span connector (a hyphen between syllables or a melisma extender line).
 * <p>
 * Coordinates are in staff spaces, relative to the line's left edge. Hyphens are drawn
 * centered within the span; extender lines run horizontally from {@code startXSs} to
 * {@code endXSs}. The renderer reads Y from
 * {@link CompositionLayoutMetrics#verseYSsInLine(int)}.
 *
 * @param startXSs   left edge of the connector in staff spaces
 * @param endXSs     right edge of the connector in staff spaces
 * @param verseIndex 1-based verse number
 * @param kind       connector kind (hyphen or melisma extender)
 */
public record LyricConnectorLayout(double startXSs, double endXSs, int verseIndex, Kind kind) {

    public enum Kind {
        HYPHEN,
        EXTENDER
    }
}
