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
 * Exact-interval y-extent skyline for above/below-staff collision detection. Each reservation is
 * kept as an exact {@code [xStart, xEnd]} <em>building</em> rather than rasterized into steps, so
 * horizontal gaps can never be aliased away. All values are in staff-space units with Y-down
 * orientation (smaller Y = higher on the page).
 */
public class StaffExtents {

    /** A reservation: the linear segment {@code y(x)} over {@code [xStartSs, xEndSs]}; a sloped one is a chord of some curve. */
    private record Building(
        double xStartSs, double xEndSs, double yStartSs, double yEndSs, double slopeSs)
        implements LinearRun {

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

        double heightSs(double xSs) {
            return valueAt(xSs);
        }
    }

    private sealed interface LinearRun permits Building, Profile.Segment {

        double xStartSs();

        double xEndSs();

        double startValueSs();

        double slopeSs();

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
     * {@code >= 0} and grow outward, away from the staff.
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

        public record Segment(
            double xStartSs, double xEndSs, double yOffsetStartSs, double yOffsetEndSs,
            double slopeSs) implements LinearRun {

            /** {@code slopeSs} is derived, never supplied: this overwrites whatever a caller passes. */
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

            @Override
            public double startValueSs() {
                return yOffsetStartSs;
            }

            double offsetSs(double localXSs) {
                return valueAt(localXSs);
            }
        }

        public static Profile flat(double widthSs) {
            return new Profile(List.of(new Segment(0.0, widthSs, 0.0, 0.0)));
        }


    }

    /**
     * The two edges an element presents: {@code inner} faces the staff and decides where the element
     * may sit; {@code outer} faces away and is what the element reserves. They are independent —
     * the accent has a sloped inner edge and still reserves a flat outer one.
     */
    public record Profiles(Profile inner, Profile outer) {

        public static Profiles flat(double widthSs) {
            var flat = Profile.flat(widthSs);
            return new Profiles(flat, flat);
        }
    }

    /**
     * Absence needs its own flag, because {@code 0.0}, the middle staff line, is a legitimate edge.
     *
     * @param ySs meaningless when {@code present} is false
     */
    public record Support(boolean present, double ySs) {}

    private static final Support NO_SUPPORT = new Support(false, 0.0);

    private final List<Building> aboveBuildings = new ArrayList<>();
    private final List<Building> belowBuildings = new ArrayList<>();
    private final double lineWidthSs;

    public StaffExtents(double lineWidthSs) {
        this.lineWidthSs = lineWidthSs;
    }

    public void ySet(Direction direction, Footprint footprint, double ySs) {
        var xSs = footprint.xSs();

        addBuilding(direction,
            new Chord(clampToLine(xSs), clampToLine(xSs + footprint.widthSs()), ySs, ySs));
    }

    public void ySetSloped(Direction direction, Chord chord) {
        addBuilding(direction, new Chord(clampToLine(chord.xStartSs()),
            clampToLine(chord.xEndSs()), chord.yStartSs(), chord.yEndSs()));
    }

    /**
     * A non-rectangular element reserves its outline rather than its box. The profile's offsets
     * grow <em>inward</em>, so every chord lies at or inside the flat {@link #ySet} rectangle:
     * reserving a profile can only give space back, never take more.
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
     * A range with no overlapping reservation reports {@code 0.0}, the identity for the
     * {@code min}/{@code max} every caller folds it into; a caller that must tell "nothing
     * reserved" from "reserved exactly at the middle line" wants {@link #clearance} instead.
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
     * LilyPond's {@code Side_position_interface::aligned_side} distance query, evaluated exactly.
     * <p>
     * <strong>The horizon padding widens the buildings, never the profile.</strong> Widening the
     * profile instead would let an element whose inner edge slopes away from a support it fully
     * covers slip closer than its bounding box would seat it. Unlike LilyPond, the widened
     * building keeps its endpoint height rather than tapering back.
     *
     * @return {@link Support#present} {@code == false} when no reservation lies under the
     *     horizon-padded profile
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

    private record Contact(
        Building building, Profile.Segment segment, double profileOriginXSs,
        double overlapStartSs, double overlapEndSs) {

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

        private double demandAt(Direction direction, double xSs) {
            var heightYSs = building.heightSs(xSs);
            var offsetSs = segment.offsetSs(xSs - profileOriginXSs);
            return direction.isUp() ? heightYSs + offsetSs : heightYSs - offsetSs;
        }
    }

    private List<Building> buildingsOn(Direction direction) {
        return direction.isUp() ? aboveBuildings : belowBuildings;
    }

    private static DoubleBinaryOperator outermost(Direction direction) {
        return direction.isUp() ? Math::min : Math::max;
    }

    public void copyTopFrom(StaffExtents source) {
        aboveBuildings.clear();
        aboveBuildings.addAll(source.aboveBuildings);
    }

    private double clampToLine(double xSs) {
        return Math.clamp(xSs, 0.0, lineWidthSs);
    }
}
