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

public class DynamicsInterval extends Interval {

    private double x1Shift;
    private double x2Shift;
    private double yShift;

    public DynamicsInterval(int start, int end) {
        super(start, end, null);
        this.x1Shift = 0;
        this.x2Shift = 0;
        this.yShift = 0;
    }

    public double getX1Shift() {
        return x1Shift;
    }

    public void setX1Shift(double x1Shift) {
        this.x1Shift = x1Shift;
    }

    public double getX2Shift() {
        return x2Shift;
    }

    public void setX2Shift(double x2Shift) {
        this.x2Shift = x2Shift;
    }

    public double getYShift() {
        return yShift;
    }

    public void setYShift(double yShift) {
        this.yShift = yShift;
    }

    @Override
    public DynamicsInterval copyRange(int newStart, int newEnd) {
        var copy = new DynamicsInterval(newStart, newEnd);
        copy.setX1Shift(x1Shift);
        copy.setX2Shift(x2Shift);
        copy.setYShift(yShift);
        return copy;
    }
}
