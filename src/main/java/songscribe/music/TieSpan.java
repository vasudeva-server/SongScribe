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
package songscribe.music;

/**
 * A typed span representing a tie between two notes.
 * <p>
 * All computed tie geometry lives in {@code LayoutResult.TieLayout}; this class
 * carries no additional fields beyond the start/end note indices inherited from
 * {@link Span}.
 */
public class TieSpan extends Span {

    public TieSpan(int start, int end) {
        super(start, end, null);
    }

    @Override
    public TieSpan copyRange(int newStart, int newEnd) {
        return new TieSpan(newStart, newEnd);
    }
}
