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

package songscribe.smufl;

/**
 * Where a glyph's ink reaches, in staff spaces with Y-down (screen) convention, measured
 * from the pen origin the glyph draws from.
 *
 * <p>This is the extent of the ink itself, not of the space the glyph occupies in a run —
 * for that see {@link SMuFLMetadata#advanceWidthSs}. Nothing here assumes the box contains the
 * origin.
 */
public record BBox(double leftSs, double topSs, double rightSs, double bottomSs) {

    public BBox {
        if (leftSs > rightSs) {
            throw new IllegalArgumentException(
                "leftSs (%s) must be <= rightSs (%s)".formatted(leftSs, rightSs));
        }

        if (topSs > bottomSs) {
            throw new IllegalArgumentException(
                "topSs (%s) must be <= bottomSs (%s)".formatted(topSs, bottomSs));
        }
    }

    public double widthSs() {
        return rightSs - leftSs;
    }

    public double heightSs() {
        return bottomSs - topSs;
    }

    public BBox translateXSs(double dxSs) {
        return new BBox(leftSs + dxSs, topSs, rightSs + dxSs, bottomSs);
    }

    public BBox union(BBox other) {
        return new BBox(
            Math.min(leftSs, other.leftSs),
            Math.min(topSs, other.topSs),
            Math.max(rightSs, other.rightSs),
            Math.max(bottomSs, other.bottomSs));
    }

    static BBox fromSMuFL(double swXSs, double swYSs, double neXSs, double neYSs) {
        return new BBox(swXSs, -neYSs, neXSs, -swYSs);
    }
}
