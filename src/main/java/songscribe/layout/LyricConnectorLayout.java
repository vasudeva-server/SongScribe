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
 * Line-level lyric span connector (a hyphen between syllables or a melisma extender line).
 * <p>
 * Coordinates are in staff spaces, relative to the line's left edge. {@link Kind#HYPHEN}
 * spans are drawn with one or more hyphens distributed across the gap; a
 * {@link Kind#DANGLING_HYPHEN} draws exactly one hyphen centered in the gap between the
 * syllable end and the next eligible element on the same line — except while the lyric
 * editor is open past that syllable, where the renderer engraves the chain up to the
 * edited element instead so it grows as the user hyphenates along the line;
 * {@link Kind#EXTENDER}
 * lines run horizontally from {@code startXSs} to {@code endXSs}; a
 * {@link Kind#DANGLING_EXTENDER} is an extender that reaches the end of the line without
 * a closing element — it is anchored to the right edge of the last eligible element on
 * the line rather than the line width. The renderer reads Y from
 * {@link LayoutResult#verseYSsInLine(int, LyricRenderMetrics)}.
 *
 * @param startXSs           left edge of the connector in staff spaces
 * @param endXSs             right edge of the connector in staff spaces
 * @param verseIndex         1-based verse number
 * @param kind               connector kind
 * @param sourceElementIndex index of the element that opened this connector within its line,
 *                           or {@link #NO_SOURCE_ELEMENT_INDEX} when the connector is a
 *                           leading continuation carried in from the previous line (no
 *                           source element on this line)
 */
public record LyricConnectorLayout(double startXSs, double endXSs, int verseIndex, Kind kind, int sourceElementIndex) {

    public static final int NO_SOURCE_ELEMENT_INDEX = -1;

    public enum Kind {
        HYPHEN,
        DANGLING_HYPHEN,
        EXTENDER,
        DANGLING_EXTENDER
    }
}
