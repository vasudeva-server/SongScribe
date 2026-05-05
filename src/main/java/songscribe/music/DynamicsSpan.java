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

public class DynamicsSpan extends Span {

    private double x1ShiftSs;
    private double x2ShiftSs;
    private double yShiftSs;

    public DynamicsSpan(int start, int end) {
        super(start, end, null);
        x1ShiftSs = 0;
        x2ShiftSs = 0;
        yShiftSs = 0;
    }

    public double getX1ShiftSs() {
        return x1ShiftSs;
    }

    public void setX1ShiftSs(double x1Shift) {
        x1ShiftSs = x1Shift;
    }

    public double getX2ShiftSs() {
        return x2ShiftSs;
    }

    public void setX2ShiftSs(double x2Shift) {
        x2ShiftSs = x2Shift;
    }

    public double getYShiftSs() {
        return yShiftSs;
    }

    public void setYShiftSs(double yShift) {
        yShiftSs = yShift;
    }

    @Override
    public DynamicsSpan copyRange(int newStart, int newEnd) {
        var copy = new DynamicsSpan(newStart, newEnd);
        copy.setX1ShiftSs(x1ShiftSs);
        copy.setX2ShiftSs(x2ShiftSs);
        copy.setYShiftSs(yShiftSs);
        return copy;
    }
}
