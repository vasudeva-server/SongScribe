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

package songscribe.dom;

/**
 * The non-interactive mark that depicts the song's tempo at the right edge of the first
 * line's staff header.
 * <p>
 * It carries no state of its own — {@link LineElement} already provides the position and the
 * user offsets, and nothing can set an offset on this mark because it is never selectable.
 * Its sole purpose is to be a stable identity key in {@code LayoutResult}'s
 * {@code decorationLayouts} map, one instance per {@link Song} (see
 * {@link Song#getTempoMarkElement()}).
 * <p>
 * The tempo it depicts is read from {@link Song#getTempo()} at layout and paint time rather
 * than stored here, so the mark cannot go stale when {@code Song.tempoDidChange} mutates the
 * live {@link Tempo} in place.
 */
public final class SongTempoMark extends LineElement {

    /**
     * Always {@code 0}. The mark's drawn extent is derived from the song's current
     * {@link Tempo} by the stacker, never cached on the element.
     */
    @Override
    public double getContentWidthPx() {
        return 0;
    }

    /**
     * Always {@code 0}, for the same reason as {@link #getContentWidthPx()}.
     */
    @Override
    public double getContentHeightPx() {
        return 0;
    }
}
