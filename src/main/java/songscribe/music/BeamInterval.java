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
 * A typed interval representing a beam group.
 * <p>
 * All computed beam geometry lives in {@code LayoutResult.BeamLayout}; this class
 * carries no additional fields beyond the start/end note indices inherited from
 * {@link Interval}.
 */
public class BeamInterval extends Interval {

    public BeamInterval(int start, int end) {
        super(start, end, null);
    }

    @Override
    public BeamInterval copyRange(int newStart, int newEnd) {
        return new BeamInterval(newStart, newEnd);
    }
}
