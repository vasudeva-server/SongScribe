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
 * for that see {@link SMuFLMetadata#advanceWidthSs}. The two differ, and which one a layout
 * wants depends on whether it is placing something against the glyph's edge or after it.
 *
 * <p>Both edges of each axis are stored rather than an edge and a size, because a glyph's
 * ink is routinely offset from the pen origin in either direction: {@code leftSs} is
 * negative for a glyph that draws to the left of where the pen sits, and {@code topSs} is
 * negative for the usual case of ink above the baseline. Nothing here assumes the box
 * contains the origin.
 *
 * @param leftSs   left edge, negative when the ink starts left of the pen origin
 * @param topSs    top edge, the smaller Y, negative when the ink rises above the origin
 * @param rightSs  right edge
 * @param bottomSs bottom edge, the larger Y
 * @throws IllegalArgumentException if {@code leftSs > rightSs} or {@code topSs > bottomSs}
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

    /** @return The distance from the left edge to the right edge. */
    public double widthSs() {
        return rightSs - leftSs;
    }

    /** @return The distance from the top edge to the bottom edge. */
    public double heightSs() {
        return bottomSs - topSs;
    }

    /**
     * Moves the box along X, which is what places a glyph at a pen position other than the
     * origin its metadata is measured from.
     *
     * @param dxSs distance to move right; negative moves left
     * @return a box of the same size, shifted by {@code dxSs}, with Y unchanged
     */
    public BBox translateXSs(double dxSs) {
        return new BBox(leftSs + dxSs, topSs, rightSs + dxSs, bottomSs);
    }

    /**
     * Combines two boxes into the one that covers both, which is how a run of glyphs
     * reports the extent of everything it drew.
     *
     * @param other the box to include
     * @return the smallest box containing both; equal to {@code this} when {@code this}
     *     already contains {@code other}
     */
    public BBox union(BBox other) {
        return new BBox(
            Math.min(leftSs, other.leftSs),
            Math.min(topSs, other.topSs),
            Math.max(rightSs, other.rightSs),
            Math.max(bottomSs, other.bottomSs));
    }

    /**
     * Converts the south-west / north-east corner pair SMuFL metadata states a box as,
     * flipping Y from the spec's Y-up convention to the screen Y-down convention the rest
     * of the application uses. The flip is why the spec's "south" corner supplies this
     * record's {@code bottomSs} by way of its negated Y rather than directly.
     *
     * @param swXSs bBoxSW x, the left edge in both conventions
     * @param swYSs bBoxSW y, the bottom edge measured upward
     * @param neXSs bBoxNE x, the right edge in both conventions
     * @param neYSs bBoxNE y, the top edge measured upward
     * @return the same box in Y-down convention
     */
    static BBox fromSMuFL(double swXSs, double swYSs, double neXSs, double neYSs) {
        return new BBox(swXSs, -neYSs, neXSs, -swYSs);
    }
}
