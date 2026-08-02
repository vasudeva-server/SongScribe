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
 * immutable thereafter. Replaces the cascade of separate hit testers each kind of
 * notation used to carry.
 *
 * <p>Coordinates are layout space: X in line-local staff spaces, Y in staff spaces
 * relative to the staff midline, positive downward. A click point is converted into
 * that space once, by the caller, before querying.
 *
 * <h2>How overlapping regions resolve</h2>
 *
 * <pre>
 *   PRIORITY                         a click at ✱ resolves like this:
 *   (highest first)
 *   ─────────────────────────        ┌───────────── Ending ─────────────┐
 *   LYRIC                            │        ▲ articulation            │
 *   ARTICULATION / ATTACHMENT        │      ╭─┴──────────────╮ Tie bbox │
 *   ACCIDENTAL                       │      │    ✱           │          │
 *   ELEMENT                          │  ♯   ●   note head    │   ●      │
 *   SLIDE                            │      ╰────────────────╯          │
 *   HAIRPIN                          └──────────────────────────────────┘
 *   ENDING
 *   TIE                              ✱ is inside Ending and Tie only
 *   BEAM                             → highest priority containing it wins → TIE
 *   STAFF_LINE
 * </pre>
 *
 * <p>Rule: of all regions whose shape contains the point, the highest priority wins.
 * Equal priorities are broken by <b>smallest bounding-box area</b>.
 *
 * <p>The area tiebreak is why a note head sitting inside an ending's box needs no
 * hand-ordering of registrations: {@code ELEMENT} already outranks {@code ENDING}. The
 * tiebreak matters within a band — two articulations whose boxes overlap resolve to the
 * smaller one. Insertion order carries no meaning at all.
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
        return resolve(xSs, layoutYSs, false);
    }

    /**
     * Resolves a click point against the regions marked {@link HitRegion#hoverTestable()},
     * with the same priority and area rules as {@link #hitTest}.
     *
     * <p>This exists so the mouse-move path stays cheap. Pointer motion fires this on
     * every pixel, and the only thing hovering needs to know about is lyrics — which is
     * exactly what the old mouse-move code asked for, instead of running the whole
     * cascade. Filtering a full {@link #hitTest} afterwards would give the same answer
     * while doing all the work this method exists to avoid.
     *
     * @param xSs       X in line-local staff spaces
     * @param layoutYSs Y in staff spaces relative to the staff midline, positive downward
     * @return the resolved hover target, or {@code null} if no hover-testable region
     *         contains the point
     */
    public @Nullable HitTarget hitTestHover(double xSs, double layoutYSs) {
        return resolve(xSs, layoutYSs, true);
    }

    /**
     * @return every registered region, unmodifiable and in unspecified order
     */
    public List<HitRegion> regions() {
        return regions;
    }

    private @Nullable HitTarget resolve(double xSs, double layoutYSs, boolean hoverOnly) {
        HitRegion best = null;
        var bestArea = 0.0;

        for (var region : regions) {
            if (hoverOnly && !region.hoverTestable()) {
                continue;
            }

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

        if (best == null) {
            return null;
        }

        return best.target();
    }

    // Higher priority wins; equal priority is broken by smaller bounding-box area.
    private static boolean winsOver(HitRegion region, double area, HitRegion best, double bestArea) {
        if (region.priority() != best.priority()) {
            return region.priority() > best.priority();
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
         * Registers one clickable area.
         *
         * @param shapeSs       the clickable area in layout space
         * @param target        what a click inside {@code shapeSs} selects
         * @param priority      see {@link HitPriority}
         * @param hoverTestable whether the region takes part in {@link #hitTestHover}
         * @return this builder, for chaining
         */
        public Builder add(Shape shapeSs, HitTarget target, int priority, boolean hoverTestable) {
            regions.add(new HitRegion(shapeSs, target, priority, hoverTestable));
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
