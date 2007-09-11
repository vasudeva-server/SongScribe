/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package songscribe.data;

public final class TupletIntervalData {

    public static final String SEPARATOR = ",";

    private TupletIntervalData() {}

    public static int getGrade(Interval tupletInterval) {
        var data = tupletInterval.getData().split(SEPARATOR);
        return Integer.parseInt(data[0]);
    }

    public static void setGrade(Interval tupletInterval, int grade) {
        var data = tupletInterval.getData();
        var datas = (data != null) ? data.split(SEPARATOR) : new String[] {};

        if (datas.length < 2) {
            tupletInterval.setData(Integer.toString(grade));
        } else {
            tupletInterval.setData(grade + SEPARATOR + datas[1]);
        }
    }

    public static boolean isVerticalAdjusted(Interval tupletInterval) {
        var data = tupletInterval.getData().split(SEPARATOR);
        return data.length > 1;
    }

    public static int getVerticalPosition(Interval tupletInterval) {
        var data = tupletInterval.getData().split(SEPARATOR);
        return isVerticalAdjusted(tupletInterval)
            ? Integer.parseInt(data[1])
            : 0;
    }

    public static void setVerticalPosition(
        Interval tupletInterval,
        int position
    ) {
        var data = tupletInterval.getData().split(SEPARATOR);
        tupletInterval.setData(data[0] + SEPARATOR + position);
    }
}
