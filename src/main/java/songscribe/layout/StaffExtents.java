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

import java.util.ArrayList;
import java.util.List;

/**
 * Exact-interval y-extent skyline for above/below-staff collision detection.
 * <p>
 * Each reservation is kept as an exact {@code [xStart, xEnd]} <em>building</em> — a linear segment
 * {@code y(x)} over that range — rather than being rasterized into fixed-width steps, so horizontal
 * gaps between elements can never be aliased away regardless of staff line width, and a curved
 * reservation (a tie arc) can be stored as chords instead of flat steps. All values are in
 * staff-space units with Y-down orientation (smaller Y = higher on the page).
 * <p>
 * Typical usage:
 * <ol>
 *   <li>{@link #clearance} to find where an element must sit to clear what is already reserved</li>
 *   <li>{@link #ySet} to reserve the space the element occupies</li>
 * </ol>
 * {@link #yGet} answers the simpler question "how far out does anything under this footprint
 * reach", and is what line sizing and the anchored stackers use.
 * <p>
 * The exactness is the point, and it is what the fixed-width steps this replaced could not give:
 * a step that any part of an element touched was reserved across its whole width, so two elements
 * that merely shared a step collided though they never overlapped. Discretization cannot separate a
 * real overlap from a rounding artifact, and no step count fixes that — only exact intervals do.
 * <p>
 * The cost is that a query scans every reservation on the line rather than a fixed 128 cells, since
 * the buildings carry no spatial index. Measured negligible: a line holds a few hundred buildings,
 * each rejected by one interval comparison, and the extents are rebuilt per line. If a query ever
 * does show up in a profile, keep the lists sorted by {@code xStartSs} and binary-search to the
 * first candidate.
 */
public class StaffExtents {

    /**
     * A reservation: the linear segment {@code y(x)} running from {@code (xStartSs, yStartSs)} to
     * {@code (xEndSs, yEndSs)}. A flat building ({@code yStartSs == yEndSs}) is the ordinary
     * rectangle edge; a sloped one is a chord of some curve.
     */
    private record Building(
        double xStartSs, double xEndSs, double yStartSs, double yEndSs, double slopeSs) {

        /** {@code slopeSs} is derived; a query evaluates the building millions of times per line. */
        Building(double xStartSs, double xEndSs, double yStartSs, double yEndSs) {
            this(xStartSs, xEndSs, yStartSs, yEndSs,
                StaffExtents.slopeSs(xStartSs, xEndSs, yStartSs, yEndSs));
        }

        boolean isFlat() {
            return slopeSs == 0.0;
        }

        /**
         * The building's Y at {@code xSs}, holding its endpoint value beyond either end. That
         * clamp is what gives a padded building its flat horizontal extension (see
         * {@link StaffExtents#clearance}).
         */
        double heightSs(double xSs) {
            return valueAt(xSs, xStartSs, xEndSs, yStartSs, slopeSs);
        }
    }

    /**
     * The slope of a linear run, or zero where it is flat or degenerate. A non-zero slope therefore
     * implies {@code xEndSs > xStartSs}, which is what lets {@link #valueAt} clamp without checking
     * the interval's orientation.
     */
    private static double slopeSs(
        double xStartSs, double xEndSs, double yStartSs, double yEndSs) {

        var spanSs = xEndSs - xStartSs;

        if (spanSs <= 0.0 || yStartSs == yEndSs) {
            return 0.0;
        }

        return (yEndSs - yStartSs) / spanSs;
    }

    /**
     * The value at {@code xSs} of the linear run rising from {@code yStartSs} at {@code xStartSs} at
     * {@code slopeSs}, holding its endpoint value beyond either end.
     * <p>
     * A reservation's run carries an absolute Y and a profile segment's an offset from a bounding
     * edge, but the arithmetic is the same, so {@link Building#heightSs} and
     * {@link Profile.Segment#offsetSs} share it.
     */
    private static double valueAt(
        double xSs, double xStartSs, double xEndSs, double yStartSs, double slopeSs) {

        if (slopeSs == 0.0) {
            return yStartSs;
        }

        return yStartSs + Math.clamp(xSs - xStartSs, 0.0, xEndSs - xStartSs) * slopeSs;
    }

    /**
     * An element's inner edge — the boundary nearest the staff — as a piecewise-linear offset from
     * its own inner bounding edge, with x measured from the element's left edge. Offsets are
     * {@code >= 0} and grow outward, away from the staff, so a flat-bottomed element above the
     * staff and a flat-topped element below it share the same all-zero profile.
     * <p>
     * The segments run left to right and cover the element without gaps; {@link #clearance} relies
     * on that to stop walking them once one starts beyond the building it is testing.
     */
    public record Profile(List<Segment> segments) {

        public Profile {
            if (segments.isEmpty()) {
                throw new IllegalArgumentException("a profile needs at least one segment");
            }

            segments = List.copyOf(segments);
        }

        /** One linear run of an element's inner edge. */
        public record Segment(
            double xStartSs, double xEndSs, double yOffsetStartSs, double yOffsetEndSs,
            double slopeSs) {

            /**
             * {@code slopeSs} is derived, never supplied: this overwrites whatever a caller passes,
             * so a segment whose slope contradicts its endpoints cannot exist. It is stored rather
             * than recomputed because a query evaluates the segment millions of times per line.
             */
            public Segment {
                slopeSs = StaffExtents.slopeSs(xStartSs, xEndSs, yOffsetStartSs, yOffsetEndSs);
            }

            public Segment(
                double xStartSs, double xEndSs, double yOffsetStartSs, double yOffsetEndSs) {

                this(xStartSs, xEndSs, yOffsetStartSs, yOffsetEndSs, 0.0);
            }

            boolean isFlat() {
                return slopeSs == 0.0;
            }

            /** The offset at {@code localXSs} (relative to the element's left edge), end-clamped. */
            double offsetSs(double localXSs) {
                return valueAt(localXSs, xStartSs, xEndSs, yOffsetStartSs, slopeSs);
            }
        }

        /**
         * The profile of an element whose inner edge is its bounding edge — every element that is
         * not a wedge or a curve. {@link #clearance} against a flat profile reduces exactly to
         * {@link #yGet} plus the padding.
         */
        public static Profile flat(double widthSs) {
            return new Profile(List.of(new Segment(0.0, widthSs, 0.0, 0.0)));
        }

        /**
         * The inner edge's outward offset at {@code localXSs}, measured from the element's left
         * edge, holding the end value beyond either end. {@link #clearance} does not call this — it
         * needs each segment clipped against a building — but it is how the profile is read.
         */
        public double offsetSs(double localXSs) {
            var first = segments.getFirst();

            if (localXSs <= first.xStartSs()) {
                return first.yOffsetStartSs();
            }

            for (var segment : segments) {
                if (localXSs <= segment.xEndSs()) {
                    return segment.offsetSs(localXSs);
                }
            }

            return segments.getLast().yOffsetEndSs();
        }
    }

    /**
     * The two edges an element presents: {@code inner} faces the staff and decides where the element
     * may sit; {@code outer} faces away and is what the element reserves for whatever stacks outside
     * it.
     * <p>
     * They are independent — the accent has a sloped inner edge and still reserves a flat outer one —
     * which is why they travel together in one type rather than as two interchangeable arguments.
     *
     * @param inner the element's inner edge, from {@link ShapeProfile#innerEdge}
     * @param outer the element's outer edge, from {@link ShapeProfile#outerEdge}
     */
    public record Profiles(Profile inner, Profile outer) {

        /** Both edges flat: any element a neighbour may treat as a rectangle. */
        public static Profiles flat(double widthSs) {
            var flat = Profile.flat(widthSs);
            return new Profiles(flat, flat);
        }
    }

    /**
     * The result of a {@link #clearance} query: whether anything was reserved under the element's
     * profile at all, and if so the inner edge it must sit at.
     * <p>
     * Absence needs its own flag rather than a sentinel Y, because {@code 0.0} — the middle staff
     * line — is a perfectly legitimate edge for a real reservation.
     *
     * @param present true when at least one reservation lies under the profile
     * @param ySs     the element's inner edge (its bottom above the staff, its top below), already
     *                padded; meaningless when {@code present} is false
     */
    public record Support(boolean present, double ySs) {}

    private static final Support NO_SUPPORT = new Support(false, 0.0);

    private final List<Building> aboveBuildings = new ArrayList<>();
    private final List<Building> belowBuildings = new ArrayList<>();
    private final double lineWidthSs;

    /**
     * Creates a new StaffExtents with no reservations.
     *
     * @param lineWidthSs total width of the staff line in staff-space units
     */
    public StaffExtents(double lineWidthSs) {
        this.lineWidthSs = lineWidthSs;
    }

    /**
     * Reserves vertical space at the given horizontal range.
     * <p>
     * Appends an exact {@code [xStart, xEnd]} rectangle; no merging with existing reservations is
     * needed because {@link #yGet} already resolves the extreme value across overlapping
     * rectangles.
     *
     * @param above   true to reserve above-staff space, false for below-staff
     * @param xSs     horizontal start position in staff-space units
     * @param widthSs horizontal width in staff-space units
     * @param ySs     the Y extent to reserve in staff-space units
     */
    public void ySet(boolean above, double xSs, double widthSs, double ySs) {
        addBuilding(above, clampToLine(xSs), clampToLine(xSs + widthSs), ySs, ySs);
    }

    /**
     * Reserves a sloped vertical extent: the chord running from {@code (xStartSs, yStartSs)} to
     * {@code (xEndSs, yEndSs)}. Used to reserve a curve — a tie arc — as a run of chords, which
     * tracks the curve far more closely than the flat steps an equal number of {@link #ySet} calls
     * would lay down.
     *
     * @param above true to reserve above-staff space, false for below-staff
     */
    public void ySetSloped(
        boolean above, double xStartSs, double xEndSs, double yStartSs, double yEndSs) {

        addBuilding(above, clampToLine(xStartSs), clampToLine(xEndSs), yStartSs, yEndSs);
    }

    /**
     * Reserves an element's real silhouette: one chord per segment of its outer edge, laid along
     * {@code profile} from the element's outer bounding edge at {@code edgeYSs}.
     * <p>
     * This is what a non-rectangular element must reserve if its neighbours are to see its outline
     * rather than its box — LilyPond builds a script's skyline from its stencil
     * ({@code define-grobs.scm} {@code grob::always-vertical-skylines-from-stencil}) and keeps both an
     * upper and a lower one. A round staccato dot reserved this way lets the accent above it seat
     * where the dot's outline has already fallen away from its box top.
     * <p>
     * The profile's offsets grow <em>inward</em>, toward the staff, so every chord lies at or inside
     * the flat {@link #ySet} rectangle: reserving a profile can only give space back, never take
     * more. A {@link Profile#flat} profile reproduces {@link #ySet} exactly.
     *
     * @param above   true to reserve above-staff space, false for below-staff
     * @param xSs     the element's left edge, which {@code profile} x-values are relative to
     * @param profile the element's outer edge, from {@link ShapeProfile#outerEdge}
     * @param edgeYSs the element's outer bounding edge (its top above the staff, its bottom below)
     */
    public void ySetProfile(boolean above, double xSs, Profile profile, double edgeYSs) {
        for (var segment : profile.segments()) {
            // Inward is toward the staff: larger Y above it, smaller Y below.
            var yStartSs = above
                ? edgeYSs + segment.yOffsetStartSs()
                : edgeYSs - segment.yOffsetStartSs();
            var yEndSs = above
                ? edgeYSs + segment.yOffsetEndSs()
                : edgeYSs - segment.yOffsetEndSs();

            addBuilding(above, clampToLine(xSs + segment.xStartSs()),
                clampToLine(xSs + segment.xEndSs()), yStartSs, yEndSs);
        }
    }

    private void addBuilding(
        boolean above, double xStartSs, double xEndSs, double yStartSs, double yEndSs) {

        var building = new Building(xStartSs, xEndSs, yStartSs, yEndSs);

        if (above) {
            aboveBuildings.add(building);
        }
        else {
            belowBuildings.add(building);
        }
    }

    /**
     * Queries the current vertical extent at the given horizontal range.
     * <p>
     * For above ({@code above=true}): returns the minimum Y reached by any building overlapping the
     * range (the highest occupied point, Y-down). For below ({@code above=false}): the maximum.
     * Because a building is linear, its extreme over the clipped sub-range is at one of that
     * sub-range's two endpoints.
     * <p>
     * A range with no overlapping reservation reports {@code 0.0} — the middle staff line. That is
     * not a sentinel but the identity for the {@code min}/{@code max} every caller folds it into;
     * a caller that must tell "nothing is reserved here" apart from "something is reserved exactly
     * at the middle line" wants {@link #clearance} instead.
     *
     * @param above   true to query above-staff extent, false for below-staff
     * @param xSs     horizontal start position in staff-space units
     * @param widthSs horizontal width in staff-space units
     * @return the extreme Y value across the queried range, in staff-space units
     */
    public double yGet(boolean above, double xSs, double widthSs) {
        var xStartSs = clampToLine(xSs);
        var xEndSs = clampToLine(xSs + widthSs);
        var buildings = above ? aboveBuildings : belowBuildings;
        var extremeYSs = 0.0;
        var hasOverlap = false;

        for (var building : buildings) {
            if (building.xStartSs() > xEndSs || xStartSs > building.xEndSs()) {
                continue;
            }

            var candidateYSs = building.yStartSs();

            if (!building.isFlat()) {
                var clippedStartSs = Math.max(building.xStartSs(), xStartSs);
                var clippedEndSs = Math.min(building.xEndSs(), xEndSs);
                candidateYSs = combine(above,
                    building.heightSs(clippedStartSs), building.heightSs(clippedEndSs));
            }

            extremeYSs = hasOverlap ? combine(above, extremeYSs, candidateYSs) : candidateYSs;
            hasOverlap = true;
        }

        return extremeYSs;
    }

    /**
     * The inner edge an element must sit at to clear every reservation under its {@code profile} by
     * {@code paddingSs}, or {@link Support#present} {@code == false} when nothing lies under it.
     * <p>
     * This is LilyPond's {@code Side_position_interface::aligned_side} distance query
     * (side-position-interface.cc:355), evaluated exactly. LilyPond first merges the supports into
     * one envelope and walks it against the element's; we do not have to, because the max
     * distributes over the supports:
     * <pre>
     *   max_x ( max_i support_i(x) - profile(x) )  ==  max_i ( max_x ( support_i(x) - profile(x) ) )
     * </pre>
     * so each building can be intersected with each profile segment independently. Both are linear
     * on that intersection, so the extreme lies at a breakpoint — exactly as {@code
     * Skyline::internal_distance} (skyline.cc:617) evaluates only at segment endpoints. No sorting,
     * no merging, no sampling.
     * <p>
     * <strong>{@code horizonPaddingSs} widens the buildings, never the profile.</strong> That is
     * what LilyPond does — {@code internal_distance} pads {@code dim}, the support skyline, and
     * notes that padding {@code other} as well would merely double the padding (skyline.cc:544).
     * The distinction is invisible for a flat profile but decides the sloped case: widening the
     * support lets it reach the profile's zero-offset end, so an element whose inner edge slopes
     * away from a support it fully covers — an accent over a notehead — still seats exactly where
     * its bounding box would have. Widening the profile instead would let it slip closer.
     * <p>
     * Unlike LilyPond, the widened building keeps its endpoint height rather than tapering back to
     * {@code -infinity} over a further {@code horizonPaddingSs} ({@code Skyline::padded}); nothing
     * here places an element in that taper's reach.
     *
     * @param above            true to query above-staff space, false for below-staff
     * @param xSs              the element's left edge, which {@code profile} x-values are relative to
     * @param profile          the element's inner edge
     * @param paddingSs        the gap the element keeps from whatever its profile contacts
     * @param horizonPaddingSs how far beyond its own footprint the element looks for a support
     */
    public Support clearance(
        boolean above, double xSs, Profile profile, double paddingSs, double horizonPaddingSs) {

        var buildings = above ? aboveBuildings : belowBuildings;
        var segments = profile.segments();
        var profileStartSs = clampToLine(xSs + segments.getFirst().xStartSs());
        var profileEndSs = clampToLine(xSs + segments.getLast().xEndSs());
        var extremeYSs = 0.0;
        var hasOverlap = false;

        for (var building : buildings) {
            var paddedStartSs = clampToLine(building.xStartSs() - horizonPaddingSs);
            var paddedEndSs = clampToLine(building.xEndSs() + horizonPaddingSs);

            // Reject a distant building against the profile's whole span before walking its
            // segments. A line holds hundreds of reservations and a script's footprint covers a
            // handful; without this, a profile's segment count would multiply the entire line.
            if (paddedStartSs > profileEndSs || profileStartSs > paddedEndSs) {
                continue;
            }

            for (var segment : segments) {
                var segmentStartSs = clampToLine(xSs + segment.xStartSs());

                // Segments run left to right, so once one starts beyond the building, so do the rest.
                if (segmentStartSs > paddedEndSs) {
                    break;
                }

                var segmentEndSs = clampToLine(xSs + segment.xEndSs());

                if (segmentEndSs < paddedStartSs) {
                    continue;
                }

                var overlapStartSs = Math.max(paddedStartSs, segmentStartSs);
                var overlapEndSs = Math.min(paddedEndSs, segmentEndSs);
                var candidateYSs = extremeOverOverlap(
                    above, building, segment, xSs, overlapStartSs, overlapEndSs);

                extremeYSs = hasOverlap ? combine(above, extremeYSs, candidateYSs) : candidateYSs;
                hasOverlap = true;
            }
        }

        if (!hasOverlap) {
            return NO_SUPPORT;
        }

        return new Support(true, above ? extremeYSs - paddingSs : extremeYSs + paddingSs);
    }

    /**
     * The extreme of {@code building(x) ± profile(x)} over {@code [overlapStartSs, overlapEndSs]} —
     * the inner edge this one building forces on this one profile segment, before padding.
     * <p>
     * Above the staff the element's edge lies {@code offset} <em>above</em> its inner edge, so the
     * building's demand at x is {@code height(x) + offset(x)} and the binding x is the minimum;
     * below, the edge lies {@code offset} below, giving {@code height(x) - offset(x)} and a maximum.
     * Both summands are linear except where the building's own ends interrupt it (the flat
     * extension the horizon padding added), so evaluating the four breakpoints is exact.
     */
    private static double extremeOverOverlap(
        boolean above, Building building, Profile.Segment segment, double profileOriginXSs,
        double overlapStartSs, double overlapEndSs) {

        if (building.isFlat() && segment.isFlat()) {
            var offsetSs = segment.yOffsetStartSs();
            return above ? building.yStartSs() + offsetSs : building.yStartSs() - offsetSs;
        }

        var extremeYSs = demandAt(above, building, segment, profileOriginXSs, overlapStartSs);
        extremeYSs = combine(above, extremeYSs,
            demandAt(above, building, segment, profileOriginXSs, overlapEndSs));

        // A flat building holds one height across the whole overlap, so the sum is linear there and
        // the two ends already bound it. Only a sloped building — a tie chord — kinks the sum where
        // its own ends interrupt the flat extension the horizon padding added.
        if (building.isFlat()) {
            return extremeYSs;
        }

        var buildingStartSs = building.xStartSs();

        if (buildingStartSs > overlapStartSs && buildingStartSs < overlapEndSs) {
            extremeYSs = combine(above, extremeYSs,
                demandAt(above, building, segment, profileOriginXSs, buildingStartSs));
        }

        var buildingEndSs = building.xEndSs();

        if (buildingEndSs > overlapStartSs && buildingEndSs < overlapEndSs) {
            extremeYSs = combine(above, extremeYSs,
                demandAt(above, building, segment, profileOriginXSs, buildingEndSs));
        }

        return extremeYSs;
    }

    private static double demandAt(
        boolean above, Building building, Profile.Segment segment, double profileOriginXSs,
        double xSs) {

        var heightYSs = building.heightSs(xSs);
        var offsetSs = segment.offsetSs(xSs - profileOriginXSs);
        return above ? heightYSs + offsetSs : heightYSs - offsetSs;
    }

    private static double combine(boolean above, double leftYSs, double rightYSs) {
        return above ? Math.min(leftYSs, rightYSs) : Math.max(leftYSs, rightYSs);
    }

    /**
     * Copies the above-staff reservations from another {@code StaffExtents} instance. Used when
     * initializing a higher tier from a lower tier's reservations (e.g., structural layer
     * starts from note-attached layer's reservations).
     *
     * @param source the instance to copy above-staff reservations from
     */
    public void copyTopFrom(StaffExtents source) {
        aboveBuildings.clear();
        aboveBuildings.addAll(source.aboveBuildings);
    }

    private double clampToLine(double xSs) {
        return Math.clamp(xSs, 0.0, lineWidthSs);
    }
}
