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
import java.util.function.DoubleBinaryOperator;

import songscribe.dom.Direction;

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
        double xStartSs, double xEndSs, double yStartSs, double yEndSs, double slopeSs)
        implements LinearRun {

        /** {@code slopeSs} is derived; a query evaluates the building millions of times per line. */
        Building(Chord chord) {
            this(chord.xStartSs(), chord.xEndSs(), chord.yStartSs(), chord.yEndSs(),
                chord.slopeSs());
        }

        boolean isFlat() {
            return slopeSs == 0.0;
        }

        @Override
        public double startValueSs() {
            return yStartSs;
        }

        /**
         * The building's Y at {@code xSs}, holding its endpoint value beyond either end. That
         * clamp is what gives a padded building its flat horizontal extension (see
         * {@link StaffExtents#clearance}).
         */
        double heightSs(double xSs) {
            return valueAt(xSs);
        }
    }

    /**
     * A linear run with a precomputed slope: a reservation's, carrying an absolute Y, or a profile
     * segment's, carrying an offset from a bounding edge. The arithmetic of reading one is the same
     * for both, so {@link Building#heightSs} and {@link Profile.Segment#offsetSs} share it here.
     */
    private sealed interface LinearRun permits Building, Profile.Segment {

        double xStartSs();

        double xEndSs();

        /** The run's value at {@link #xStartSs()}. */
        double startValueSs();

        /**
         * The run's slope, from {@link Chord#slopeSs}: non-zero only when
         * {@code xEndSs() > xStartSs()}, which is what lets {@link #valueAt} clamp without checking
         * the interval's orientation.
         */
        double slopeSs();

        /**
         * Returns the run's value at {@code xSs}, holding its endpoint value beyond either end.
         *
         * @param xSs the X to read the run at, in the same frame as {@link #xStartSs()}
         * @return the run's value at {@code xSs}, clamped to its ends
         */
        default double valueAt(double xSs) {
            var slopeSs = slopeSs();
            var startValueSs = startValueSs();

            if (slopeSs == 0.0) {
                return startValueSs;
            }

            var xStartSs = xStartSs();
            return startValueSs + Math.clamp(xSs - xStartSs, 0.0, xEndSs() - xStartSs) * slopeSs;
        }
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
            double slopeSs) implements LinearRun {

            /**
             * {@code slopeSs} is derived, never supplied: this overwrites whatever a caller passes,
             * so a segment whose slope contradicts its endpoints cannot exist. It is stored rather
             * than recomputed because a query evaluates the segment millions of times per line.
             */
            public Segment {
                slopeSs = new Chord(xStartSs, xEndSs, yOffsetStartSs, yOffsetEndSs).slopeSs();
            }

            public Segment(
                double xStartSs, double xEndSs, double yOffsetStartSs, double yOffsetEndSs) {

                this(xStartSs, xEndSs, yOffsetStartSs, yOffsetEndSs, 0.0);
            }

            boolean isFlat() {
                return slopeSs == 0.0;
            }

            /**
             * Returns the segment's offset at its start.
             *
             * @return {@link #yOffsetStartSs()}
             */
            @Override
            public double startValueSs() {
                return yOffsetStartSs;
            }

            /** The offset at {@code localXSs} (relative to the element's left edge), end-clamped. */
            double offsetSs(double localXSs) {
                return valueAt(localXSs);
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
     * Reserves vertical space over a footprint.
     * <p>
     * Appends an exact {@code [xStart, xEnd]} rectangle; no merging with existing reservations is
     * needed because {@link #yGet} already resolves the extreme value across overlapping
     * rectangles.
     *
     * @param direction {@link Direction#UP} to reserve above-staff space, {@link Direction#DOWN}
     *                  for below-staff
     * @param footprint the horizontal range to reserve, in staff-space units
     * @param ySs       the Y extent to reserve in staff-space units
     */
    public void ySet(Direction direction, Footprint footprint, double ySs) {
        var xSs = footprint.xSs();

        addBuilding(direction,
            new Chord(clampToLine(xSs), clampToLine(xSs + footprint.widthSs()), ySs, ySs));
    }

    /**
     * Reserves a sloped vertical extent: {@code chord}, clamped to the line. Used to reserve a
     * curve — a tie arc — as a run of chords, which tracks the curve far more closely than the flat
     * steps an equal number of {@link #ySet} calls would lay down.
     *
     * @param direction {@link Direction#UP} to reserve above-staff space, {@link Direction#DOWN}
     *                  for below-staff
     * @param chord     the run to reserve, in staff-space units
     */
    public void ySetSloped(Direction direction, Chord chord) {
        addBuilding(direction, new Chord(clampToLine(chord.xStartSs()),
            clampToLine(chord.xEndSs()), chord.yStartSs(), chord.yEndSs()));
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
     * @param direction {@link Direction#UP} to reserve above-staff space, {@link Direction#DOWN}
     *                  for below-staff
     * @param xSs       the element's left edge, which {@code profile} x-values are relative to
     * @param profile   the element's outer edge, from {@link ShapeProfile#outerEdge}
     * @param edgeYSs   the element's outer bounding edge (its top above the staff, its bottom
     *                  below)
     */
    public void ySetProfile(Direction direction, double xSs, Profile profile, double edgeYSs) {
        var above = direction.isUp();

        for (var segment : profile.segments()) {
            // Inward is toward the staff: larger Y above it, smaller Y below.
            var yStartSs = above
                ? edgeYSs + segment.yOffsetStartSs()
                : edgeYSs - segment.yOffsetStartSs();
            var yEndSs = above
                ? edgeYSs + segment.yOffsetEndSs()
                : edgeYSs - segment.yOffsetEndSs();

            addBuilding(direction, new Chord(clampToLine(xSs + segment.xStartSs()),
                clampToLine(xSs + segment.xEndSs()), yStartSs, yEndSs));
        }
    }

    private void addBuilding(Direction direction, Chord chord) {
        var building = new Building(chord);

        if (direction.isUp()) {
            aboveBuildings.add(building);
        }
        else {
            belowBuildings.add(building);
        }
    }

    /**
     * Queries the current vertical extent over a footprint.
     * <p>
     * Above the staff: returns the minimum Y reached by any building overlapping the range (the
     * highest occupied point, Y-down). Below: the maximum. Because a building is linear, its
     * extreme over the clipped sub-range is at one of that sub-range's two endpoints.
     * <p>
     * A range with no overlapping reservation reports {@code 0.0} — the middle staff line. That is
     * not a sentinel but the identity for the {@code min}/{@code max} every caller folds it into;
     * a caller that must tell "nothing is reserved here" apart from "something is reserved exactly
     * at the middle line" wants {@link #clearance} instead.
     *
     * @param direction {@link Direction#UP} to query above-staff extent, {@link Direction#DOWN}
     *                  for below-staff
     * @param footprint the horizontal range to query, in staff-space units
     * @return the extreme Y value across the queried range, in staff-space units
     */
    public double yGet(Direction direction, Footprint footprint) {
        var xSs = footprint.xSs();
        var xStartSs = clampToLine(xSs);
        var xEndSs = clampToLine(xSs + footprint.widthSs());
        var buildings = buildingsOn(direction);
        var outermost = outermost(direction);
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
                candidateYSs = outermost.applyAsDouble(
                    building.heightSs(clippedStartSs), building.heightSs(clippedEndSs));
            }

            extremeYSs = hasOverlap ? outermost.applyAsDouble(extremeYSs, candidateYSs) : candidateYSs;
            hasOverlap = true;
        }

        return extremeYSs;
    }

    /**
     * The inner edge an element must sit at to clear every reservation under its {@code profile} by
     * the padding, or {@link Support#present} {@code == false} when nothing lies under it.
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
     * <strong>The horizon padding widens the buildings, never the profile.</strong> That is
     * what LilyPond does — {@code internal_distance} pads {@code dim}, the support skyline, and
     * notes that padding {@code other} as well would merely double the padding (skyline.cc:544).
     * The distinction is invisible for a flat profile but decides the sloped case: widening the
     * support lets it reach the profile's zero-offset end, so an element whose inner edge slopes
     * away from a support it fully covers — an accent over a notehead — still seats exactly where
     * its bounding box would have. Widening the profile instead would let it slip closer.
     * <p>
     * Unlike LilyPond, the widened building keeps its endpoint height rather than tapering back to
     * {@code -infinity} over a further horizon padding ({@code Skyline::padded}); nothing here
     * places an element in that taper's reach.
     *
     * @param direction {@link Direction#UP} to query above-staff space, {@link Direction#DOWN}
     *                  for below-staff
     * @param xSs       the element's left edge, which {@code profile} x-values are relative to
     * @param profile   the element's inner edge
     * @param padding   the gap the element keeps from its supports and how far it looks for them
     * @return the padded inner edge, or {@link Support#present} {@code == false} when no
     *     reservation lies under the horizon-padded profile
     */
    public Support clearance(
        Direction direction, double xSs, Profile profile, SupportPadding padding) {

        var buildings = buildingsOn(direction);
        var outermost = outermost(direction);
        var horizonPaddingSs = padding.horizonPaddingSs();
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

                var contact = new Contact(building, segment, xSs,
                    Math.max(paddedStartSs, segmentStartSs), Math.min(paddedEndSs, segmentEndSs));
                var candidateYSs = contact.extremeYSs(direction);

                extremeYSs = hasOverlap ? outermost.applyAsDouble(extremeYSs, candidateYSs) : candidateYSs;
                hasOverlap = true;
            }
        }

        if (!hasOverlap) {
            return NO_SUPPORT;
        }

        var paddingSs = padding.paddingSs();
        return new Support(true,
            direction.isUp() ? extremeYSs - paddingSs : extremeYSs + paddingSs);
    }

    /**
     * One building against one profile segment, over the X range where the horizon-padded
     * building and the segment overlap.
     *
     * @param building         the reservation
     * @param segment          the profile segment
     * @param profileOriginXSs the element's left edge, which the segment's x-values are relative to
     * @param overlapStartSs   the overlap's start X
     * @param overlapEndSs     the overlap's end X
     */
    private record Contact(
        Building building, Profile.Segment segment, double profileOriginXSs,
        double overlapStartSs, double overlapEndSs) {

        /**
         * The extreme of {@code building(x) ± profile(x)} over the overlap — the inner edge this
         * one building forces on this one profile segment, before padding.
         * <p>
         * Above the staff the element's edge lies {@code offset} <em>above</em> its inner edge, so
         * the building's demand at x is {@code height(x) + offset(x)} and the binding x is the
         * minimum; below, the edge lies {@code offset} below, giving {@code height(x) - offset(x)}
         * and a maximum. Both summands are linear except where the building's own ends interrupt it
         * (the flat extension the horizon padding added), so evaluating the four breakpoints is
         * exact.
         *
         * @param direction the side of the staff the element sits on
         * @return the binding inner edge Y, unpadded
         */
        double extremeYSs(Direction direction) {
            if (building.isFlat() && segment.isFlat()) {
                var offsetSs = segment.yOffsetStartSs();
                return direction.isUp()
                    ? building.yStartSs() + offsetSs
                    : building.yStartSs() - offsetSs;
            }

            var outermost = outermost(direction);
            var extremeYSs = demandAt(direction, overlapStartSs);
            extremeYSs = outermost.applyAsDouble(extremeYSs, demandAt(direction, overlapEndSs));

            // A flat building holds one height across the whole overlap, so the sum is linear
            // there and the two ends already bound it. Only a sloped building — a tie chord — kinks
            // the sum where its own ends interrupt the flat extension the horizon padding added.
            if (building.isFlat()) {
                return extremeYSs;
            }

            var buildingStartSs = building.xStartSs();

            if (buildingStartSs > overlapStartSs && buildingStartSs < overlapEndSs) {
                extremeYSs =
                    outermost.applyAsDouble(extremeYSs, demandAt(direction, buildingStartSs));
            }

            var buildingEndSs = building.xEndSs();

            if (buildingEndSs > overlapStartSs && buildingEndSs < overlapEndSs) {
                extremeYSs =
                    outermost.applyAsDouble(extremeYSs, demandAt(direction, buildingEndSs));
            }

            return extremeYSs;
        }

        /**
         * The inner edge the building demands of the segment at one X.
         *
         * @param direction the side of the staff the element sits on
         * @param xSs       the X, within the overlap
         * @return the building's height moved outward by the segment's offset at {@code xSs}
         */
        private double demandAt(Direction direction, double xSs) {
            var heightYSs = building.heightSs(xSs);
            var offsetSs = segment.offsetSs(xSs - profileOriginXSs);
            return direction.isUp() ? heightYSs + offsetSs : heightYSs - offsetSs;
        }
    }

    /**
     * The reservations on one side of the staff.
     *
     * @param direction the side
     * @return the live list of that side's buildings
     */
    private List<Building> buildingsOn(Direction direction) {
        return direction.isUp() ? aboveBuildings : belowBuildings;
    }

    /**
     * The fold that picks the more outward of two Ys on one side of the staff: the smaller above
     * (Y-down), the larger below.
     *
     * @param direction the side
     * @return {@link Math#min} above the staff, {@link Math#max} below
     */
    private static DoubleBinaryOperator outermost(Direction direction) {
        return direction.isUp() ? Math::min : Math::max;
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
