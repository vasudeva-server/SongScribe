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

import java.util.Map;
import java.util.OptionalDouble;

import songscribe.dom.DynamicAttachment;
import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.dom.Span;
import songscribe.dom.StaffElement;
import songscribe.engraving.HairpinMetrics;

/**
 * Resolves the drawn horizontal endpoints of a {@link Hairpin}, a port of the left/right loop in
 * LilyPond's {@code Hairpin::print}. {@code Hairpin} is a DOM type and may not depend on
 * {@code songscribe.layout}, so the endpoint rules live here.
 * <p>
 * Each tip is <em>assigned</em> from the first rule that applies, never clamped back to its note
 * column, so a wedge preceded by a text dynamic legitimately starts left of its anchor column's
 * origin. The one clamp is between the two tips, stated on {@link #compute}.
 * <p>
 * LilyPond's non-musical-bound padding rule is not ported: a SongScribe bound is always a note.
 * Do not add it speculatively.
 */
public final class HairpinEndpoints {

    private HairpinEndpoints() {
    }

    /**
     * @param x1Ss left (anchor-side) tip, absolute, in staff spaces
     * @param x2Ss right (end-side) tip
     */
    public record Endpoints(double x1Ss, double x2Ss, ElementColumn.SpanColumns spanColumns) {

        public double widthSs() {
            return x2Ss - x1Ss;
        }
    }

    /**
     * Computes the drawn endpoints of {@code hairpin} within {@code line}, by three rules.
     * <p>
     * <b>Tip precedence.</b> Each tip takes the first of these that applies, the right tip
     * mirroring the left:
     * <ol>
     *   <li>a text dynamic on the hairpin's own bound — its anchor for the left tip, its end for
     *       the right: the tip stops {@link HairpinMetrics#BOUND_PADDING_SS} clear of the dynamic's
     *       advance box, on the side facing the wedge;</li>
     *   <li>another hairpin meeting this one on that bound — one ending on the anchor, or one
     *       anchored on the end: the tip stops {@link HairpinMetrics#BACK_TO_BACK_PADDING_SS}
     *       from the bound's notehead center, on the wedge's side of it;</li>
     *   <li>a text dynamic on the element just outside the bound — before the anchor, or after the
     *       end: the tip stops {@link HairpinMetrics#BOUND_PADDING_SS} clear of its advance box;</li>
     *   <li>otherwise, the anchor column's origin for the left tip, and the end notehead's right
     *       edge for the right.</li>
     * </ol>
     * <p>
     * <b>Minimum length.</b> When a wedge comes out shorter than
     * {@link HairpinMetrics#MINIMUM_LENGTH_SS}, its right tip moves right until it is that long,
     * unless a dynamic placed the right tip.
     * <p>
     * <b>A dynamic-placed right tip.</b> A right tip a text dynamic placed (rule 1 or 3) is not
     * lengthened, since lengthening would drive it back under the glyph it clears. It moves only
     * when it falls left of the left tip, and then only onto it: the wedge has zero width.
     *
     * @param hairpin          the hairpin to place; its own bounds are read from
     *                         {@link Hairpin#range()}
     * @param line             the line being laid out, which serves only the neighbor lookups:
     *                         the elements just outside the bounds and the other hairpins
     * @param columnsByElement element-to-column lookup for that line, holding a column for every
     *                         element of it
     * @return the resolved endpoints
     * @invariant {@code x2Ss >= x1Ss}
     */
    public static Endpoints compute(
        Hairpin hairpin,
        Line line,
        Map<StaffElement, ElementColumn> columnsByElement
    ) {
        var range = hairpin.range();
        var anchor = hairpin.getAnchorElement();
        var end = hairpin.getEndElement();
        var anchorColumn = columnOf(columnsByElement, anchor);
        var endColumn = columnOf(columnsByElement, end);

        var x1Ss = leftEndpointXSs(hairpin, line, columnsByElement, anchorColumn, range.begin());
        var rightTip = rightEndpoint(hairpin, line, columnsByElement, endColumn, range.end());
        var x2Ss = rightTip.xSs();

        if (rightTip.placedByDynamic()) {
            // Widening moves the right tip and nothing else, so here it would drive the wedge
            // straight back under the glyph the padding just cleared. LilyPond does not widen at
            // all — it warns and collapses a negative width (hairpin.cc:292-298) — so do only that
            // much. This does move a dynamic-placed tip, but only out of a negative width.
            x2Ss = Math.max(x2Ss, x1Ss);
        } else if (x2Ss - x1Ss < HairpinMetrics.MINIMUM_LENGTH_SS) {
            // LilyPond warns "crescendo too small" and collapses to 0; extending rightward keeps
            // the wedge visible. Safe even when a dynamic placed the *left* tip: widening moves
            // the right tip away from that glyph, and a dynamic anywhere on the right would have
            // placed the right tip instead, taking the branch above. The spacing floor in
            // HorizontalSpacingCalculator.hairpinReservationFloorSs makes this rare.
            x2Ss = x1Ss + HairpinMetrics.MINIMUM_LENGTH_SS;
        }

        return new Endpoints(
            x1Ss, x2Ss, new ElementColumn.SpanColumns(anchor, end, anchorColumn, endColumn));
    }

    private static ElementColumn columnOf(
        Map<StaffElement, ElementColumn> columnsByElement, StaffElement element) {

        var column = columnsByElement.get(element);

        if (column == null) {
            throw new IllegalStateException("no column for " + element + " in the line being laid out");
        }

        return column;
    }

    private record RightTip(double xSs, boolean placedByDynamic) {
    }

    /**
     * The edges are the glyph's <em>advance</em> box, not its ink. Deliberately unclamped — the
     * result may legitimately fall outside the hairpin's own columns. Serves both the own-bound
     * and adjacent-element callers; do not split it into two variants. An index outside the line
     * yields empty, so callers may pass {@code anchorIndex - 1} and {@code endIndex + 1} unchecked.
     */
    private static OptionalDouble dynamicTipXSs(
        Line line,
        Map<StaffElement, ElementColumn> columnsByElement,
        int index,
        SpanEnd side
    ) {
        if (!line.hasIndex(index)) {
            return OptionalDouble.empty();
        }

        var element = line.getElement(index);
        var dynamic = element.findAttachment(DynamicAttachment.class);

        if (dynamic == null) {
            return OptionalDouble.empty();
        }

        var offsetSs = side == SpanEnd.LEFT
            ? dynamicLeftTipOffsetSs(element, dynamic)
            : dynamicRightTipOffsetSs(element, dynamic);

        return OptionalDouble.of(columnOf(columnsByElement, element).getXSs() + offsetSs);
    }

    private static double leftEndpointXSs(
        Hairpin hairpin,
        Line line,
        Map<StaffElement, ElementColumn> columnsByElement,
        ElementColumn anchorColumn,
        int anchorIndex
    ) {
        // Own bound first: in <f> both this and the back-to-back rule apply, and LilyPond checks
        // the bound's text interface (hairpin.cc:216) before the adjacent-spanners scan (:222), so
        // the wedge clears the glyph rather than stopping at the notehead center. Do not reorder.
        var ownBound = dynamicTipXSs(line, columnsByElement, anchorIndex, SpanEnd.LEFT);

        if (ownBound.isPresent()) {
            return ownBound.getAsDouble();
        }

        if (hasOtherHairpin(hairpin, line, Span.endingAt(anchorIndex))) {
            // hairpin.cc:257 — x_points[d] = e.center() - d * padding / 3, with d == LEFT == -1.
            return anchorColumn.getNoteheadCenterXSs() + HairpinMetrics.BACK_TO_BACK_PADDING_SS;
        }

        return dynamicTipXSs(line, columnsByElement, anchorIndex - 1, SpanEnd.LEFT)
            .orElse(anchorColumn.getXSs());
    }

    private static RightTip rightEndpoint(
        Hairpin hairpin,
        Line line,
        Map<StaffElement, ElementColumn> columnsByElement,
        ElementColumn endColumn,
        int endIndex
    ) {
        // Own bound first, for the same reason as on the left.
        var ownBound = dynamicTipXSs(line, columnsByElement, endIndex, SpanEnd.RIGHT);

        if (ownBound.isPresent()) {
            return new RightTip(ownBound.getAsDouble(), true);
        }

        if (hasOtherHairpin(hairpin, line, Span.anchoredAt(endIndex))) {
            return new RightTip(
                endColumn.getNoteheadCenterXSs() - HairpinMetrics.BACK_TO_BACK_PADDING_SS, false);
        }

        var next = dynamicTipXSs(line, columnsByElement, endIndex + 1, SpanEnd.RIGHT);

        if (next.isPresent()) {
            return new RightTip(next.getAsDouble(), true);
        }

        return new RightTip(endColumn.getXSs() + endColumn.getNoteheadWidthSs(), false);
    }

    /**
     * Taking only the first match is safe: each side queries the <em>opposite</em> bound.
     */
    private static boolean hasOtherHairpin(
        Hairpin hairpin, Line line, Span.RangePredicate matches) {
        var neighbor = line.spanRegistry().findFirstSpan(Hairpin.class, matches);

        return neighbor != null && neighbor != hairpin;
    }

    /**
     * The one formula for where a dynamic starts; the hairpin endpoint rules and
     * {@code StructuralStacker.stackTextDynamics} both read it from here.
     */
    public static double dynamicLeftEdgeSs(ElementColumn column, DynamicAttachment dynamic) {
        var host = new ElementAtColumn<>(column.getElement(), column.getXSs());

        return NoteGeometry.centeredOverNotehead(host, dynamicWidthSs(dynamic)).xSs();
    }

    public static double dynamicWidthSs(DynamicAttachment dynamic) {
        return dynamic.intrinsicWidthSs();
    }

    /**
     * A hairpin pads away from the advance box, not the ink box, because that is the box LilyPond
     * measures. An offset from the column origin rather than an absolute X, so
     * {@code HorizontalSpacingCalculator}, which builds its springs before columns have positions,
     * can reserve room for the same pullback.
     */
    private static double advanceLeftEdgeOffsetSs(
        StaffElement element,
        DynamicAttachment dynamic
    ) {
        return NoteGeometry.getNoteheadCenterXSs(element)
            - dynamicWidthSs(dynamic) / 2.0
            - dynamic.getLeftSideBearingSs();
    }

    static double dynamicLeftTipOffsetSs(StaffElement element, DynamicAttachment dynamic) {
        return advanceLeftEdgeOffsetSs(element, dynamic)
            + dynamic.getAdvanceWidthSs()
            + HairpinMetrics.BOUND_PADDING_SS;
    }

    static double dynamicRightTipOffsetSs(StaffElement element, DynamicAttachment dynamic) {
        return advanceLeftEdgeOffsetSs(element, dynamic) - HairpinMetrics.BOUND_PADDING_SS;
    }

    static double dynamicAdvanceLeftEdgeSs(ElementColumn column, DynamicAttachment dynamic) {
        return column.getXSs() + advanceLeftEdgeOffsetSs(column.getElement(), dynamic);
    }

}
