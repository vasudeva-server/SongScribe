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

public class TupletInterval extends Interval {

    private int grade;
    private int verticalPosition;

    public TupletInterval(int start, int end, int grade) {
        super(start, end, null);
        this.grade = grade;
        this.verticalPosition = 0;
    }

    public int getGrade() {
        return grade;
    }

    public void setGrade(int grade) {
        this.grade = grade;
    }

    public int getVerticalPosition() {
        return verticalPosition;
    }

    public void setVerticalPosition(int verticalPosition) {
        this.verticalPosition = verticalPosition;
    }

    public boolean isVerticallyAdjusted() {
        return verticalPosition != 0;
    }

    @Override
    public TupletInterval copyRange(int newStart, int newEnd) {
        var copy = new TupletInterval(newStart, newEnd, grade);
        copy.setVerticalPosition(verticalPosition);
        return copy;
    }
}
