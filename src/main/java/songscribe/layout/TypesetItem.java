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
 * One drawable piece of a decoration's typeset content, positioned relative to the content's
 * top-left corner.
 * <p>
 * An item carries everything needed to draw it, so a renderer walking a list of items resolves no
 * font, measures no advance and decides no position on either axis. Both offsets are in staff
 * spaces and therefore zoom-invariant; the view transform supplies zoom.
 * <p>
 * The hierarchy is sealed so that a renderer dispatching over it can do so without a
 * {@code default} arm: a third kind of item fails to compile at every such switch rather than
 * falling through one of them at run time.
 */
public sealed interface TypesetItem permits GlyphItem, TextItem {

    /** Offset from the content's left edge, in staff spaces. */
    double xSs();

    /**
     * Offset from the content's top edge down to this item's drawing origin, in staff spaces —
     * the baseline for text, the glyph origin for a glyph.
     */
    double baselineOffsetSs();
}
