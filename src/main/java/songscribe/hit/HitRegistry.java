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
 * Every clickable area of one staff line, immutable once built. Of all regions containing the
 * point, the highest {@link HitPriority} wins; equal priorities fall to the smallest
 * bounding-box area. Insertion order carries no meaning.
 *
 * <p>Coordinates are layout space: X in line-local staff spaces, Y in staff spaces relative to
 * the staff midline, positive downward. The caller converts a click point into that space.
 */
public final class HitRegistry {

    public static final HitRegistry EMPTY = new HitRegistry(List.of());

    private final List<HitRegion> regions;

    private HitRegistry(List<HitRegion> regions) {
        this.regions = regions;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return the target of the highest-priority region containing the point, or {@code null}
     *         if no region contains it
     */
    public @Nullable HitTarget hitTest(double xSs, double layoutYSs) {
        var region = hitRegion(xSs, layoutYSs);

        if (region == null) {
            return null;
        }

        return region.target();
    }

    /**
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

    public static final class Builder {

        private final List<HitRegion> regions = new ArrayList<>();

        public Builder add(Shape shapeSs, HitTarget target) {
            regions.add(new HitRegion(shapeSs, target));
            return this;
        }

        public HitRegistry build() {
            if (regions.isEmpty()) {
                return EMPTY;
            }

            return new HitRegistry(List.copyOf(regions));
        }
    }
}
