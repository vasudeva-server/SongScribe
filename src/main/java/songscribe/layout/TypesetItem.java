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
 * One drawable piece of a decoration's typeset content, positioned in staff spaces relative to
 * the content's top-left corner.
 */
public sealed interface TypesetItem permits GlyphItem, TextItem {

    double xSs();

    /**
     * Offset from the content's top edge down to this item's drawing origin: the baseline for
     * text, the glyph origin for a glyph.
     */
    double baselineOffsetSs();
}
