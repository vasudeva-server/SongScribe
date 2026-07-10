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

import module java.desktop;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import org.jspecify.annotations.Nullable;

/**
 * Derives a {@link StaffExtents.Profile} — an element's inner edge — from the real outline of a
 * {@link Shape}, the way LilyPond builds a grob's skyline from its stencil's FreeType outline
 * ({@code stencil-integral.cc} {@code add_named_glyph_segments}) rather than from its bounding box.
 * <p>
 * A glyph whose inner edge is not flat — the accent's wedge — clears its neighbours by its outline,
 * not by the corners of a box it barely fills. Anything with a genuinely flat inner edge should use
 * {@link StaffExtents.Profile#flat} instead of paying for this.
 */
public final class ShapeProfile {

    /**
     * Flattening tolerance for the outline's curves, in staff spaces — an order of magnitude below
     * the smallest padding the profile is ever compared against (a script's 0.1 ss horizon padding),
     * so flattening never decides a placement.
     * <p>
     * It buys accuracy only on an outline's <em>curves</em>. The accent's long straight arm — the
     * only part of it any real support meets — is one path segment and stays exact at any tolerance,
     * while its rounded cap and tip are what a finer tolerance would dice into dozens of slivers.
     * Every one of those slivers would then be walked for every reservation under the glyph, on
     * every line: at 0.001 ss the profile costs 18 segments and 12 µs per line of accented notes,
     * at 0.01 ss it costs 5 and under 2 µs, and the placements agree to 0.003 ss.
     */
    private static final double FLATNESS_SS = 0.01;

    private ShapeProfile() {
    }

    /**
     * The inner edge of {@code shape} as seen from the staff — its lower boundary when the element
     * sits above the staff, its upper boundary when below — expressed as offsets from the shape's
     * inner bounding edge, growing outward, with x measured from the shape's left edge.
     * <p>
     * This is the edge that decides where the element itself may sit: it is what
     * {@link StaffExtents#clearance} walks against the reservations beneath it.
     *
     * @param above true for an element above the staff (its lower boundary faces the staff)
     */
    public static StaffExtents.Profile innerEdge(Shape shape, boolean above) {
        return boundary(shape, above, FLATNESS_SS);
    }

    /**
     * The outer edge of {@code shape} — the boundary facing <em>away</em> from the staff — expressed
     * as offsets from the shape's outer bounding edge, growing inward, with x measured from the
     * shape's left edge.
     * <p>
     * This is the edge the element presents to whatever stacks outside it, and it is what a
     * non-rectangular element must <em>reserve</em> if its neighbours are to nestle against its real
     * silhouette rather than the corners of its box. A round staccato dot reserved this way lets an
     * accent seat closer, exactly where the dot's outline has already fallen away from its box top.
     * <p>
     * The two edges are the same walk seen from opposite sides: a shape's lower boundary, with
     * offsets measured up from the box's bottom, is the inner edge of an element above the staff and
     * the outer edge of one below it. Hence the mirrored delegation rather than a second envelope
     * walk.
     *
     * @param above true for an element above the staff (its upper boundary faces away from the staff)
     */
    public static StaffExtents.Profile outerEdge(Shape shape, boolean above) {
        return boundary(shape, !above, FLATNESS_SS);
    }

    /**
     * {@link #outerEdge(Shape, boolean)} at an explicit flattening tolerance.
     * <p>
     * A reserved edge affords a looser tolerance than a placed one, and the caller owns the choice.
     * Flattening a convex outline replaces each arc by a chord that lies <em>inside</em> it, so a
     * reservation built this way can only ever under-reserve — the error is one-sided, bounded by
     * {@code flatnessSs}, and always in the direction of letting a neighbour sit closer. A placed
     * edge has no such guarantee, which is why {@link #innerEdge} keeps the tighter
     * {@link #FLATNESS_SS} to itself.
     * <p>
     * Every chord is a reservation the whole line must then be scanned against, so this is the knob
     * that trades a fraction of a pixel for real time: reserving Bravura's staccato dot at 0.01 ss
     * costs 8 chords, at 0.02 ss it costs 4, and the accent above it moves 0.0056 ss.
     *
     * @param flatnessSs the outline's flattening tolerance, in staff spaces
     */
    public static StaffExtents.Profile outerEdge(Shape shape, boolean above, double flatnessSs) {
        return boundary(shape, !above, flatnessSs);
    }

    /**
     * The shape's lower boundary ({@code lower}) or upper boundary, as offsets from the corresponding
     * bounding edge, growing away from it.
     * <p>
     * The boundary is exact, not sampled: between two consecutive vertex x-coordinates of a simple
     * outline exactly one edge lies on the envelope, so evaluating that edge at both ends captures
     * the envelope's every breakpoint. Curves are flattened first, at {@code flatnessSs}.
     */
    private static StaffExtents.Profile boundary(Shape shape, boolean lower, double flatnessSs) {
        var edges = flattenToEdges(shape, flatnessSs);

        // A shape of zero width, or one drawn only from vertical strokes, has no edge that can
        // govern an envelope over an interval, so it has no boundary to speak of. Fail here rather
        // than hand StaffExtents.Profile an empty segment list.
        if (edges.isEmpty()) {
            throw new IllegalArgumentException("shape has no non-vertical outline edges");
        }

        var bounds = shape.getBounds2D();
        var breakXs = distinctSortedXs(edges);
        var runs = new ArrayList<Run>();
        Edge currentEdge = null;

        // One segment per run of intervals governed by the same outline edge. The envelope over such
        // a run *is* that edge, so extending the run rather than emitting a segment per breakpoint
        // is exact — and it collapses the wedge's long straight arm, which the opposite boundary's
        // vertices would otherwise chop into dozens of collinear pieces.
        for (var i = 0; i < breakXs.size() - 1; i++) {
            var startXSs = breakXs.get(i);
            var endXSs = breakXs.get(i + 1);
            var governing = governingEdge(edges, (startXSs + endXSs) / 2.0, lower);

            // No edge spans this gap: the outline is disjoint across x, which a glyph outline is not.
            if (governing == null) {
                continue;
            }

            if (governing != currentEdge) {
                runs.add(new Run(startXSs));
                currentEdge = governing;
            }

            var run = runs.getLast();
            run.endXSs = endXSs;
            run.startYSs = governing.yAt(run.startXSs);
            run.endYSs = governing.yAt(endXSs);
        }

        return buildProfile(runs, bounds, lower);
    }

    /**
     * Converts governed runs of the outline into a {@link StaffExtents.Profile}, measuring offsets
     * from the bounding edge the boundary touches.
     * <p>
     * Flattening can leave the polyline's extreme a hair inside the true bounding edge, which would
     * give the profile a non-zero minimum and nudge an element that ought not to move at all. The
     * offsets are renormalized so the minimum is exactly zero — which is what "offset from the
     * bounding edge" means. The shift is uniform, so a reserved outer edge can only under-reserve
     * and a placed inner edge can only sit further from its supports, never closer.
     */
    private static StaffExtents.Profile buildProfile(
        List<Run> runs, Rectangle2D bounds, boolean lower) {

        var minOffsetSs = Double.MAX_VALUE;

        for (var run : runs) {
            minOffsetSs = Math.min(minOffsetSs, offsetSs(run.startYSs, bounds, lower));
            minOffsetSs = Math.min(minOffsetSs, offsetSs(run.endYSs, bounds, lower));
        }

        var segments = new ArrayList<StaffExtents.Profile.Segment>(runs.size());
        var originXSs = bounds.getMinX();

        for (var run : runs) {
            segments.add(new StaffExtents.Profile.Segment(
                run.startXSs - originXSs,
                run.endXSs - originXSs,
                offsetSs(run.startYSs, bounds, lower) - minOffsetSs,
                offsetSs(run.endYSs, bounds, lower) - minOffsetSs));
        }

        return new StaffExtents.Profile(List.copyOf(segments));
    }

    /** A maximal run of the envelope governed by a single outline edge. */
    private static final class Run {

        private final double startXSs;
        private double endXSs;
        private double startYSs;
        private double endYSs;

        private Run(double startXSs) {
            this.startXSs = startXSs;
        }
    }

    /**
     * How far {@code ySs} lies from the bounding edge the boundary touches: the box's bottom for the
     * lower boundary, its top for the upper. Either way the result is non-negative and zero where the
     * outline touches its box.
     */
    private static double offsetSs(double ySs, Rectangle2D bounds, boolean lower) {
        var offsetSs = lower ? bounds.getMaxY() - ySs : ySs - bounds.getMinY();

        // Flattening can push a point a hair past the true bound; a negative offset is meaningless.
        return Math.max(offsetSs, 0.0);
    }

    /**
     * The edge lying on the requested envelope at {@code xSs}: the lowest edge there for the lower
     * boundary, the highest for the upper.
     */
    private static @Nullable Edge governingEdge(
        List<Edge> edges, double xSs, boolean lower) {

        Edge governing = null;
        var extremeYSs = 0.0;

        for (var edge : edges) {
            if (!edge.spans(xSs)) {
                continue;
            }

            var ySs = edge.yAt(xSs);

            if (governing == null || (lower ? ySs > extremeYSs : ySs < extremeYSs)) {
                governing = edge;
                extremeYSs = ySs;
            }
        }

        return governing;
    }

    /**
     * The edges' endpoint x-coordinates, sorted and deduplicated. Adjacent edges of a path share a
     * vertex exactly — the same {@code double}, not two independently rounded ones — so equality is
     * the right dedup test here.
     */
    private static List<Double> distinctSortedXs(List<Edge> edges) {
        var xs = new TreeSet<Double>();

        for (var edge : edges) {
            xs.add(edge.x0Ss());
            xs.add(edge.x1Ss());
        }

        return List.copyOf(xs);
    }

    /**
     * Flattens {@code shape} into the non-vertical edges of its outline. Vertical edges are dropped:
     * they contribute their endpoints as breakpoints but can never govern an envelope over an
     * interval of positive width.
     */
    private static List<Edge> flattenToEdges(Shape shape, double flatnessSs) {
        var edges = new ArrayList<Edge>();
        var iterator = shape.getPathIterator(null, flatnessSs);
        var coords = new double[6];
        var subpathStartXSs = 0.0;
        var subpathStartYSs = 0.0;
        var currentXSs = 0.0;
        var currentYSs = 0.0;

        while (!iterator.isDone()) {
            switch (iterator.currentSegment(coords)) {
                case PathIterator.SEG_MOVETO -> {
                    subpathStartXSs = coords[0];
                    subpathStartYSs = coords[1];
                    currentXSs = coords[0];
                    currentYSs = coords[1];
                }
                case PathIterator.SEG_LINETO -> {
                    addEdge(edges, currentXSs, currentYSs, coords[0], coords[1]);
                    currentXSs = coords[0];
                    currentYSs = coords[1];
                }
                case PathIterator.SEG_CLOSE -> {
                    addEdge(edges, currentXSs, currentYSs, subpathStartXSs, subpathStartYSs);
                    currentXSs = subpathStartXSs;
                    currentYSs = subpathStartYSs;
                }
                default -> throw new IllegalStateException(
                    "flattened path iterator yielded a curve segment");
            }

            iterator.next();
        }

        return edges;
    }

    private static void addEdge(
        List<Edge> edges, double x0Ss, double y0Ss, double x1Ss, double y1Ss) {

        if (x0Ss == x1Ss) {
            return;
        }

        edges.add(x0Ss < x1Ss
            ? new Edge(x0Ss, y0Ss, x1Ss, y1Ss)
            : new Edge(x1Ss, y1Ss, x0Ss, y0Ss));
    }

    /** One non-vertical outline edge, left endpoint first. */
    private record Edge(double x0Ss, double y0Ss, double x1Ss, double y1Ss) {

        boolean spans(double xSs) {
            return xSs >= x0Ss && xSs <= x1Ss;
        }

        double yAt(double xSs) {
            var t = Math.clamp((xSs - x0Ss) / (x1Ss - x0Ss), 0.0, 1.0);
            return y0Ss + t * (y1Ss - y0Ss);
        }
    }
}
