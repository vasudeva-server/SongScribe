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
 * The back-to-back and rest rules below are <em>not reachable from the editor today</em>, and that
 * is deliberate rather than an oversight. {@code MusicEditOperations.isHairpinEligibleSpan}
 * requires a hairpin's end to be a pitched note, so it never lands on a rest, and
 * {@code resolveHairpinAction} blocks an opposite-type neighbour while {@code Line.addHairpin}
 * absorbs a same-type one, so two hairpins never share an element. Both configurations are
 * nonetheless ordinary engraving practice — back-to-back hairpins appear in 25 files of the ABC
 * corpus and rest-bounded ones in two — so the geometry is kept ready for the editor work that
 * will produce them (refs #743). The unit tests drive it directly.
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

        var x1Ss = leftEndpointSs(hairpin, line, columnsByElement, anchorColumn, anchorIndex);
        var x2Ss = rightEndpointSs(hairpin, line, columnsByElement, endColumn, endIndex);

        // Last-resort guard for the degenerate case. LilyPond instead warns "crescendo too small"
        // and collapses the width to 0 (hairpin.cc:293-299); extending rightward keeps the wedge
        // visible. The spacing floor in HorizontalSpacingCalculator makes this rare.
        if (x2Ss - x1Ss < Hairpin.MINIMUM_LENGTH_SS) {
            x2Ss = x1Ss + Hairpin.MINIMUM_LENGTH_SS;
        }

        return new Endpoints(x1Ss, x2Ss, spanColumns);
    }

    /**
     * The left tip, applying the first matching rule in {@code Hairpin::print}'s precedence order:
     * a back-to-back hairpin ending on the anchor column, then a text dynamic on the element
     * before the anchor, then the anchor column's own origin.
     */
    private static double leftEndpointSs(
        Hairpin hairpin,
        Line line,
        Map<StaffElement, ElementColumn> columnsByElement,
        ElementColumn anchorColumn,
        int anchorIndex
    ) {
        if (hasOtherHairpin(hairpin, line, (anchorBound, endBound) -> endBound.isAt(anchorIndex))) {
            // hairpin.cc:257 — x_points[d] = e.center() - d * padding / 3, with d == LEFT == -1.
            return anchorColumn.getNoteheadCenterXSs() + Hairpin.BACK_TO_BACK_PADDING_SS;
        }

        var previousDynamic = dynamicAt(line, anchorIndex - 1);

        if (previousDynamic != null) {
            var previousColumn = columnsByElement.get(line.getElement(anchorIndex - 1));

            if (previousColumn != null) {
                // hairpin.cc:218-220 — x_points[d] = e[-d] - d * padding: the dynamic's right
                // edge plus padding. Deliberately unclamped, so this may fall left of the anchor.
                return dynamicLeftEdgeSs(previousColumn, previousDynamic)
                    + dynamicWidthSs(previousDynamic) + Hairpin.BOUND_PADDING_SS;
            }
        }

        return anchorColumn.getXSs();
    }

    /**
     * The right tip, mirroring {@link #leftEndpointSs} with the extra rest rule: LilyPond ends a
     * hairpin at a rest's left edge rather than past its glyph.
     */
    private static double rightEndpointSs(
        Hairpin hairpin,
        Line line,
        Map<StaffElement, ElementColumn> columnsByElement,
        ElementColumn endColumn,
        int endIndex
    ) {
        if (hasOtherHairpin(hairpin, line, (anchorBound, endBound) -> anchorBound.isAt(endIndex))) {
            return endColumn.getNoteheadCenterXSs() - Hairpin.BACK_TO_BACK_PADDING_SS;
        }

        var nextDynamic = dynamicAt(line, endIndex + 1);

        if (nextDynamic != null) {
            var nextColumn = columnsByElement.get(line.getElement(endIndex + 1));

            if (nextColumn != null) {
                return dynamicLeftEdgeSs(nextColumn, nextDynamic) - Hairpin.BOUND_PADDING_SS;
            }
        }

        if (endColumn.isRest()) {
            // hairpin.cc:268-271 — x_points[RIGHT] = e[LEFT].
            return endColumn.getLeftEdgeXSs();
        }

        return endColumn.getXSs() + endColumn.getNoteheadWidthSs();
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
     * The text dynamic attached to element {@code index} of {@code line}, or {@code null} when
     * that index is off either end of the line or its element carries no dynamic.
     */
    private static @Nullable DynamicAttachment dynamicAt(Line line, int index) {
        if (index < 0 || index >= line.elementCount()) {
            return null;
        }

        return line.getElement(index).findAttachment(DynamicAttachment.class);
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
        return dynamic.getContentWidthSs();
    }
}
