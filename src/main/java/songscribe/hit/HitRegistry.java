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

package songscribe.hit;

import java.awt.Shape;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Every clickable area of one staff line, built once per line at layout time and
 * immutable thereafter. It is the one structure that answers hits for every kind of
 * notation, in place of a separate hit tester per kind.
 *
 * <p>Coordinates are layout space: X in line-local staff spaces, Y in staff spaces
 * relative to the staff midline, positive downward. A click point is converted into
 * that space once, by the caller, before querying.
 *
 * <h2>How overlapping regions resolve</h2>
 *
 * <p>Rule: of all regions whose shape contains the point, the highest priority wins.
 * Equal priorities are broken by <b>smallest bounding-box area</b>. {@link HitPriority}
 * declares the order. So a click that lands inside an ending's box and a tie's bounding box
 * but on neither a notehead nor an articulation resolves to the tie, the higher of the two.
 *
 * <p>The area tiebreak is why a note head sitting inside an ending's box needs no
 * hand-ordering of registrations: a note head already outranks an ending. The tiebreak
 * matters within a band — two articulations whose boxes overlap resolve to the smaller one.
 * Insertion order carries no meaning at all.
 *
 * @see HitPriority
 */
public final class HitRegistry {

    /** The registry for a line with nothing clickable on it. Every query comes back empty. */
    public static final HitRegistry EMPTY = new HitRegistry(List.of());

    private final List<HitRegion> regions;

    private HitRegistry(List<HitRegion> regions) {
        this.regions = regions;
    }

    /**
     * @return a new, empty builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Resolves a click point against every region.
     *
     * @param xSs       X in line-local staff spaces
     * @param layoutYSs Y in staff spaces relative to the staff midline, positive downward
     * @return the target of the highest-priority region containing the point, smallest
     *         bounding-box area breaking a priority tie, or {@code null} if no region
     *         contains it
     */
    public @Nullable HitTarget hitTest(double xSs, double layoutYSs) {
        var region = hitRegion(xSs, layoutYSs);

        if (region == null) {
            return null;
        }

        return region.target();
    }

    /**
     * Resolves a click point against every region, as {@link #hitTest} does, and returns the
     * region that wins rather than only its target.
     *
     * @param xSs       X in line-local staff spaces
     * @param layoutYSs Y in staff spaces relative to the staff midline, positive downward
     * @return the region whose target {@link #hitTest} returns, or {@code null} if no region
     *         contains the point
     */
    public @Nullable HitRegion hitRegion(double xSs, double layoutYSs) {
        HitRegion best = null;
        var bestArea = 0.0;

        for (var region : regions) {
            var shape = region.shapeSs();

            if (!shape.contains(xSs, layoutYSs)) {
                continue;
            }

            var bounds = shape.getBounds2D();
            var area = bounds.getWidth() * bounds.getHeight();

            if (best != null && !winsOver(region, area, best, bestArea)) {
                continue;
            }

            best = region;
            bestArea = area;
        }

        return best;
    }

    /**
     * @return every registered region, unmodifiable and in unspecified order
     */
    public List<HitRegion> regions() {
        return regions;
    }

    // Higher priority wins; equal priority is broken by smaller bounding-box area.
    private static boolean winsOver(HitRegion region, double area, HitRegion best, double bestArea) {
        var rank = region.priority().rank();
        var bestRank = best.priority().rank();

        if (rank != bestRank) {
            return rank > bestRank;
        }

        return area < bestArea;
    }

    /**
     * Accumulates regions for one line. Regions may be added in any order; the registry
     * resolves by priority and area, never by insertion order.
     */
    public static final class Builder {

        private final List<HitRegion> regions = new ArrayList<>();

        /**
         * Registers one clickable area. How it resolves against overlapping areas follows from
         * {@code target}.
         *
         * @param shapeSs the clickable area in layout space
         * @param target  what a click inside {@code shapeSs} addresses
         * @return this builder, for chaining
         */
        public Builder add(Shape shapeSs, HitTarget target) {
            regions.add(new HitRegion(shapeSs, target));
            return this;
        }

        /**
         * @return an immutable registry holding everything added so far, or
         *         {@link #EMPTY} if nothing was added
         */
        public HitRegistry build() {
            if (regions.isEmpty()) {
                return EMPTY;
            }

            return new HitRegistry(List.copyOf(regions));
        }
    }
}
