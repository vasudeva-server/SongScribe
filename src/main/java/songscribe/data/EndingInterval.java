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
package songscribe.data;

public class EndingInterval extends Interval {

    private int endingNumber;

    public EndingInterval(int start, int end, int endingNumber) {
        super(start, end, null);
        this.endingNumber = endingNumber;
    }

    public int getEndingNumber() {
        return endingNumber;
    }

    public void setEndingNumber(int endingNumber) {
        this.endingNumber = endingNumber;
    }

    @Override
    public EndingInterval copyRange(int newStart, int newEnd) {
        return new EndingInterval(newStart, newEnd, endingNumber);
    }
}
