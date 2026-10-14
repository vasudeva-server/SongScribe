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
 * A straight run from {@code (xStartSs, yStartSs)} to {@code (xEndSs, yEndSs)}, in staff spaces:
 * one chord of a sloped reservation, or of a profile's edge.
 */
public record Chord(double xStartSs, double xEndSs, double yStartSs, double yEndSs) {

    public double slopeSs() {
        var spanSs = xEndSs - xStartSs;

        if (spanSs <= 0.0 || yStartSs == yEndSs) {
            return 0.0;
        }

        return (yEndSs - yStartSs) / spanSs;
    }
}
