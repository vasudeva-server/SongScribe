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

import java.awt.Shape;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import org.jspecify.annotations.Nullable;

/**
 * Derives a {@link StaffExtents.Profile} — an element's inner edge — from the real outline of a
 * {@link Shape} rather than from its bounding box. Anything with a genuinely flat inner edge
 * should use {@link StaffExtents.Profile#flat} instead of paying for this.
 */
public final class ShapeProfile {

    /**
     * Flattening tolerance for the outline's curves, in staff spaces — an order of magnitude below
     * the smallest padding the profile is ever compared against (a script's 0.1 ss horizon padding),
     * so flattening never decides a placement.
     */
    private static final double FLATNESS_SS = 0.01;

    private ShapeProfile() {
    }

    /**
     * The inner edge of {@code shape} as seen from the staff, as offsets from the shape's inner
     * bounding edge, growing outward, with x measured from the shape's left edge.
     */
    public static StaffExtents.Profile innerEdge(Shape shape, boolean above) {
        return boundary(shape, above, FLATNESS_SS);
    }

    /**
     * The outer edge of {@code shape} — the boundary facing <em>away</em> from the staff — as
     * offsets from the shape's outer bounding edge, growing inward, with x measured from the
     * shape's left edge. A non-rectangular element must <em>reserve</em> this so its neighbours
     * nestle against its real silhouette.
     */
    public static StaffExtents.Profile outerEdge(Shape shape, boolean above) {
        return boundary(shape, !above, FLATNESS_SS);
    }

    /**
     * A reserved edge affords a looser tolerance than a placed one, and the caller owns the choice.
     * Flattening a convex outline puts each chord <em>inside</em> the arc, so a reservation can only
     * under-reserve, by at most {@code flatnessSs}, letting a neighbour sit closer. A placed edge
     * has no such guarantee, which is why {@link #innerEdge} keeps the tighter {@link #FLATNESS_SS}.
     */
    public static StaffExtents.Profile outerEdge(Shape shape, boolean above, double flatnessSs) {
        return boundary(shape, !above, flatnessSs);
    }

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
     * The offsets are renormalized so the minimum is exactly zero, since flattening can leave the
     * extreme a hair inside the true bounding edge and nudge an element that ought not to move.
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

    private static final class Run {

        private final double startXSs;
        private double endXSs;
        private double startYSs;
        private double endYSs;

        private Run(double startXSs) {
            this.startXSs = startXSs;
        }
    }

    /** The result is non-negative and zero where the outline touches its box. */
    private static double offsetSs(double ySs, Rectangle2D bounds, boolean lower) {
        var offsetSs = lower ? bounds.getMaxY() - ySs : ySs - bounds.getMinY();

        // Flattening can push a point a hair past the true bound; a negative offset is meaningless.
        return Math.max(offsetSs, 0.0);
    }

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

    private static List<Double> distinctSortedXs(List<Edge> edges) {
        var xs = new TreeSet<Double>();

        for (var edge : edges) {
            xs.add(edge.x0Ss());
            xs.add(edge.x1Ss());
        }

        return List.copyOf(xs);
    }

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
        List<? super Edge> edges, double x0Ss, double y0Ss, double x1Ss, double y1Ss) {

        if (x0Ss == x1Ss) {
            return;
        }

        edges.add(x0Ss < x1Ss
            ? new Edge(x0Ss, y0Ss, x1Ss, y1Ss)
            : new Edge(x1Ss, y1Ss, x0Ss, y0Ss));
    }

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
