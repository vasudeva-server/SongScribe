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

import org.jspecify.annotations.Nullable;

import songscribe.dom.DynamicAttachment;
import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.dom.Span;
import songscribe.dom.SpanBound;
import songscribe.dom.StaffElement;

/**
 * Resolves the drawn horizontal endpoints of a {@link Hairpin}, a port of the
 * {@code for (const auto d : {LEFT, RIGHT})} loop in LilyPond's {@code Hairpin::print}
 * ({@code lily/hairpin.cc:184-290}).
 * <p>
 * {@code Hairpin} is a DOM type and may not depend on {@code songscribe.layout}; the endpoint
 * rules need {@link ElementColumn} geometry, so they live here rather than on the span. The
 * padding values themselves are properties of LilyPond's {@code Hairpin} grob and stay on
 * {@link Hairpin}.
 * <p>
 * The rules are <em>assignments, not clamps</em>. LilyPond takes the endpoint straight from the
 * neighbour's extent with no clamp back to the note column, so a wedge preceded by a text dynamic
 * legitimately starts left of its anchor column's origin: the wedge runs from just clear of the
 * {@code p} to the end note, which is the intended appearance.
 * <p>
 * Every rule below is reachable from the editor. Back-to-back hairpins and rest-bounded ones
 * became reachable when issue #743 landed ({@code MusicEditOperations.resolveHairpinAction}, see
 * {@code docs/hairpin-editing.md}), and a text dynamic may now sit on a hairpin's <em>own</em>
 * bound — its anchor or end element — as well as on the element outside it (issue #744). So
 * {@code f<}, {@code >p} and {@code <f>} are all shapes this class has to place.
 * <p>
 * LilyPond's remaining bound rule, padding away from a non-musical bound ({@code hairpin.cc:283},
 * {@code Item::is_non_musical}), is <em>not</em> ported. It exists because LilyPond bounds spanners
 * on {@code NoteColumn}s that may be barlines; a SongScribe bound is always a note, and no hairpin
 * in the corpus is bounded by a barline. Do not add it speculatively.
 */
public final class HairpinEndpoints {

    private HairpinEndpoints() {
    }

    /**
     * The resolved absolute X positions of a hairpin's left and right tips, in staff spaces,
     * together with the columns the tips were resolved against.
     * <p>
     * {@code spanColumns} rides along because resolving it is the first thing {@link #compute}
     * does, and every caller needs the anchor's staff position to stack the wedge. Handing it back
     * spares each of them a second identical lookup and a second null check.
     *
     * @param x1Ss        left (anchor-side) tip
     * @param x2Ss        right (end-side) tip
     * @param spanColumns the hairpin's anchor and end elements with their resolved columns
     */
    public record Endpoints(double x1Ss, double x2Ss, ElementColumn.SpanColumns spanColumns) {

        /** The drawn width of the wedge in staff spaces. */
        public double widthSs() {
            return x2Ss - x1Ss;
        }
    }

    /**
     * Computes the drawn endpoints of {@code hairpin} within {@code line}.
     *
     * @param hairpin          the hairpin to place
     * @param line             the line being laid out, used to resolve the hairpin's own indices
     *                         and to look for neighbouring hairpins
     * @param columnsByElement element-to-column lookup for that line
     * @return the resolved endpoints, or {@code null} when the hairpin cannot be placed in this
     *         line — either endpoint unresolvable to a column, or to a position in this line
     */
    public static @Nullable Endpoints compute(
        Hairpin hairpin,
        Line line,
        Map<StaffElement, ElementColumn> columnsByElement
    ) {
        var spanColumns = ElementColumn.resolveSpan(hairpin, columnsByElement);

        if (spanColumns == null) {
            return null;
        }

        // Resolve through the line, never through Hairpin.getAnchorElementIndex(): that answers
        // from whichever line the endpoint belongs to and returns -1 when unpositioned, which
        // would turn into silently wrong arithmetic below rather than a rejection here.
        if (!(line.anchorIndexOf(hairpin) instanceof SpanBound.At(var anchorIndex))
            || !(line.endIndexOf(hairpin) instanceof SpanBound.At(var endIndex))) {
            return null;
        }

        var anchorColumn = spanColumns.anchorColumn();
        var endColumn = spanColumns.endColumn();

        var leftTip = leftEndpointSs(hairpin, line, columnsByElement, anchorColumn, anchorIndex);
        var rightTip = rightEndpointSs(hairpin, line, columnsByElement, endColumn, endIndex);

        var x1Ss = leftTip.xSs();
        var x2Ss = rightTip.xSs();

        if (rightTip.placedByDynamic()) {
            // Widening moves the right tip and nothing else, so here it would drive the wedge
            // straight back under the glyph the padding just cleared. LilyPond does not widen at
            // all — it warns and collapses a negative width (hairpin.cc:292-298) — so do only that
            // much. This does move a dynamic-placed tip, but only out of a negative width.
            x2Ss = Math.max(x2Ss, x1Ss);
        } else if (x2Ss - x1Ss < Hairpin.MINIMUM_LENGTH_SS) {
            // LilyPond warns "crescendo too small" and collapses to 0; extending rightward keeps
            // the wedge visible. Safe even when a dynamic placed the *left* tip: widening moves
            // the right tip away from that glyph, and a dynamic anywhere on the right would have
            // placed the right tip instead, taking the branch above. The spacing floor in
            // HorizontalSpacingCalculator.hairpinReservationFloorSs makes this rare.
            x2Ss = x1Ss + Hairpin.MINIMUM_LENGTH_SS;
        }

        return new Endpoints(x1Ss, x2Ss, spanColumns);
    }

    /** Which end of the wedge is being resolved — LilyPond's {@code d} in {@code x_points[d]}. */
    private enum Side {
        LEFT,
        RIGHT
    }

    /**
     * A resolved tip.
     * <p>
     * Only the <em>right</em> tip's {@code placedByDynamic} is consulted, by the minimum-length
     * rule in {@link #compute} — widening moves the right tip alone, so the left tip's provenance
     * cannot change the outcome. The flag rides on both sides because one helper resolves both;
     * dropping it from the left would stop the two mirror methods mirroring for no gain.
     */
    private record Tip(double xSs, boolean placedByDynamic) {
    }

    /**
     * The tip a text dynamic on element {@code index} would impose on {@code side} of a wedge, or
     * {@code null} when that element carries no dynamic or has no column in this line.
     * <p>
     * Applies LilyPond's {@code x_points[d] = e[-d] - d * padding} ({@code hairpin.cc:218-220}):
     * on the left, the dynamic's right edge plus padding; on the right, its left edge minus
     * padding. Those edges are the glyph's <em>advance</em> box, not its ink — LilyPond's
     * {@code e} is the {@code DynamicText} grob extent, which is the advance width. See
     * {@link #dynamicAdvanceLeftEdgeSs}. Deliberately unclamped — the result may legitimately
     * fall outside the hairpin's own columns.
     * <p>
     * When the caller asks about the hairpin's own bound this re-looks-up a column {@link #compute}
     * already holds as {@code spanColumns.anchorColumn()} / {@code endColumn()}. That is
     * deliberate: one helper serving both the own-bound and the adjacent-element callers is worth
     * one map lookup on the rare shapes that have a dynamic on a bound at all — the common case
     * returns before the lookup. Do not split it into two variants.
     */
    private static @Nullable Tip dynamicTipSs(
        Line line,
        Map<StaffElement, ElementColumn> columnsByElement,
        int index,
        Side side
    ) {
        // Range-guarded here so callers may pass anchorIndex - 1 and endIndex + 1 unchecked.
        if (!line.hasIndex(index)) {
            return null;
        }

        var element = line.getElement(index);
        var dynamic = element.findAttachment(DynamicAttachment.class);

        if (dynamic == null) {
            return null;
        }

        var column = columnsByElement.get(element);

        if (column == null) {
            return null;
        }

        var offsetSs = side == Side.LEFT
            ? dynamicLeftTipOffsetSs(element, dynamic)
            : dynamicRightTipOffsetSs(element, dynamic);

        return new Tip(column.getXSs() + offsetSs, true);
    }

    /**
     * The left tip, applying the first matching rule in {@code Hairpin::print}'s precedence order:
     * a text dynamic on the anchor element itself, then a back-to-back hairpin ending on the anchor
     * column, then a text dynamic on the element before the anchor, then the anchor column's own
     * origin.
     * <p>
     * The own-bound rule outranks back-to-back because LilyPond tests
     * {@code has_interface<Text_interface> (b)} ({@code hairpin.cc:216}) before its
     * {@code adjacent-spanners} scan ({@code hairpin.cc:222}).
     */
    private static Tip leftEndpointSs(
        Hairpin hairpin,
        Line line,
        Map<StaffElement, ElementColumn> columnsByElement,
        ElementColumn anchorColumn,
        int anchorIndex
    ) {
        // Own bound first: in <f> both this and the back-to-back rule apply, and LilyPond checks
        // the bound's text interface (hairpin.cc:216) before the adjacent-spanners scan (:222), so
        // the wedge clears the glyph rather than stopping at the notehead center. Do not reorder.
        var ownBound = dynamicTipSs(line, columnsByElement, anchorIndex, Side.LEFT);

        if (ownBound != null) {
            return ownBound;
        }

        if (hasOtherHairpin(hairpin, line, (anchorBound, endBound) -> endBound.isAt(anchorIndex))) {
            // hairpin.cc:257 — x_points[d] = e.center() - d * padding / 3, with d == LEFT == -1.
            return new Tip(
                anchorColumn.getNoteheadCenterXSs() + Hairpin.BACK_TO_BACK_PADDING_SS, false);
        }

        var previous = dynamicTipSs(line, columnsByElement, anchorIndex - 1, Side.LEFT);

        if (previous != null) {
            return previous;
        }

        return new Tip(anchorColumn.getXSs(), false);
    }

    /**
     * The right tip, mirroring {@link #leftEndpointSs} with the extra rest rule: LilyPond ends a
     * hairpin at a rest's left edge rather than past its glyph. The precedence is a text dynamic on
     * the end element itself, then a back-to-back hairpin anchored on the end column, then a text
     * dynamic on the element after the end, then the rest rule, then past the notehead.
     * <p>
     * As on the left, the own-bound rule outranks back-to-back per {@code hairpin.cc:216} vs
     * {@code :222}.
     */
    private static Tip rightEndpointSs(
        Hairpin hairpin,
        Line line,
        Map<StaffElement, ElementColumn> columnsByElement,
        ElementColumn endColumn,
        int endIndex
    ) {
        // Own bound first, for the same reason as on the left. It also outranks the rest rule
        // below, which is LilyPond's ordering; that combination is unreachable from the editor
        // because DynamicMarkingAction extends NoteOnlyAction, so a rest never carries a dynamic.
        var ownBound = dynamicTipSs(line, columnsByElement, endIndex, Side.RIGHT);

        if (ownBound != null) {
            return ownBound;
        }

        if (hasOtherHairpin(hairpin, line, (anchorBound, endBound) -> anchorBound.isAt(endIndex))) {
            return new Tip(
                endColumn.getNoteheadCenterXSs() - Hairpin.BACK_TO_BACK_PADDING_SS, false);
        }

        var next = dynamicTipSs(line, columnsByElement, endIndex + 1, Side.RIGHT);

        if (next != null) {
            return next;
        }

        if (endColumn.isRest()) {
            // hairpin.cc:268-271 — x_points[RIGHT] = e[LEFT].
            return new Tip(endColumn.getLeftEdgeXSs(), false);
        }

        return new Tip(endColumn.getXSs() + endColumn.getNoteheadWidthSs(), false);
    }

    /**
     * Whether some hairpin other than {@code hairpin} on {@code line} satisfies {@code matches} —
     * LilyPond's "adjacent-spanners" test, which asks whether a neighbouring hairpin hangs on the
     * same column.
     * <p>
     * Asks for the first match rather than collecting every one, so this short-circuits and
     * allocates nothing. Taking only the first is safe: each side queries the <em>opposite</em>
     * bound — the left tip asks who <em>ends</em> on the anchor — so {@code hairpin} could only
     * satisfy its own query by having its two bounds on one element, which no span does. The
     * identity check remains because the rule is "a neighbouring hairpin", not "any hairpin".
     */
    private static boolean hasOtherHairpin(
        Hairpin hairpin, Line line, Span.IndexPredicate matches) {
        var neighbor = line.findFirstSpan(Hairpin.class, matches);

        return neighbor != null && neighbor != hairpin;
    }

    /**
     * The absolute X of a text dynamic's left edge in staff spaces. A dynamic is drawn centered
     * over its notehead, so this is the one formula that says where it starts; the hairpin
     * endpoint rules and {@code StructuralStacker.stackTextDynamics} both read it from here.
     *
     * @param column  the column of the element the dynamic is attached to
     * @param dynamic the dynamic
     * @return absolute left edge X in staff spaces
     */
    public static double dynamicLeftEdgeSs(ElementColumn column, DynamicAttachment dynamic) {
        return NoteGeometry.centeredOverNoteheadXSs(
            column.getElement(), column.getXSs(), dynamicWidthSs(dynamic));
    }

    /**
     * The drawn width of a text dynamic in staff spaces, the companion of
     * {@link #dynamicLeftEdgeSs} so that a caller needing the right edge adds the two rather than
     * reaching for the attachment's own accessor.
     */
    public static double dynamicWidthSs(DynamicAttachment dynamic) {
        return dynamic.intrinsicWidthSs();
    }

    /**
     * The distance from a column's origin to the left edge of the advance box of a text dynamic
     * sitting on {@code element} — the glyph origin, recovered from the ink left edge by removing
     * the left side bearing.
     * <p>
     * A hairpin pads away from this box rather than from the ink box because that is the box
     * LilyPond measures: its {@code DynamicText} grob extent is the advance width (Emmentaler's
     * {@code f} declares {@code 1.468}, Bravura's {@code dynamicForte} {@code 1.456}), while the
     * ink of both overhangs it. Padding from the ink instead doubled the visible gap.
     * <p>
     * The glyph itself stays centered by its <em>ink</em> box (see {@link #dynamicLeftEdgeSs}), so
     * this changes only where a neighbouring wedge stops, never where the dynamic is drawn.
     * <p>
     * Expressed as an offset from the column origin rather than an absolute X so that
     * {@code HorizontalSpacingCalculator}, which builds its springs before any column has a
     * resolved position, can reserve room for the same pullback this class will later apply.
     */
    private static double advanceLeftEdgeOffsetSs(
        StaffElement element,
        DynamicAttachment dynamic
    ) {
        return NoteGeometry.getNoteheadCenterXSs(element)
            - dynamicWidthSs(dynamic) / 2.0
            - dynamic.getLeftSideBearingSs();
    }

    /**
     * The distance from a column's origin to the left tip a wedge takes when the dynamic on
     * {@code element} is what places it: past the advance box's right edge, plus the bound padding.
     */
    static double dynamicLeftTipOffsetSs(StaffElement element, DynamicAttachment dynamic) {
        return advanceLeftEdgeOffsetSs(element, dynamic)
            + dynamic.getAdvanceWidthSs()
            + Hairpin.BOUND_PADDING_SS;
    }

    /**
     * The distance from a column's origin to the right tip a wedge takes when the dynamic on
     * {@code element} is what places it: short of the advance box's left edge by the bound padding.
     * Normally negative — the tip stops left of the note's own origin.
     */
    static double dynamicRightTipOffsetSs(StaffElement element, DynamicAttachment dynamic) {
        return advanceLeftEdgeOffsetSs(element, dynamic) - Hairpin.BOUND_PADDING_SS;
    }

    /**
     * The absolute X of a text dynamic's advance box's left edge in staff spaces. See
     * {@link #advanceLeftEdgeOffsetSs} for what that box is and why a hairpin measures from it.
     */
    static double dynamicAdvanceLeftEdgeSs(ElementColumn column, DynamicAttachment dynamic) {
        return column.getXSs() + advanceLeftEdgeOffsetSs(column.getElement(), dynamic);
    }

    /**
     * The absolute X of a text dynamic's advance box's right edge in staff spaces, the companion of
     * {@link #dynamicAdvanceLeftEdgeSs}.
     */
    static double dynamicAdvanceRightEdgeSs(ElementColumn column, DynamicAttachment dynamic) {
        return dynamicAdvanceLeftEdgeSs(column, dynamic) + dynamic.getAdvanceWidthSs();
    }
}
