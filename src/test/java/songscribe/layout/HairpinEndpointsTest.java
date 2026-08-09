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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Crescendo;
import songscribe.dom.Diminuendo;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.DynamicAttachment.DynamicType;
import songscribe.dom.ElementType;
import songscribe.dom.Hairpin;
import songscribe.dom.StaffElement;

/**
 * Tests for {@link HairpinEndpoints#compute}, one nested class per endpoint rule. Columns are
 * synthetic ({@link ElementColumnTestHelper}), positioned by hand so each rule's contribution is
 * isolated from the others.
 */
class HairpinEndpointsTest extends UnitTest {

    private static final double TOLERANCE = 1e-9;

    /** A uniform notehead width for synthetic columns, distinct from any real glyph metric. */
    private static final double NOTEHEAD_WIDTH_SS = 1.0;

    /** Wide enough that no test's span is mistaken for the minimum-length degenerate case. */
    private static final double WIDE_SPACING_SS = 20.0;

    /**
     * Far enough from a dynamic's pullback that the "direction" assertions hold regardless of the
     * exact SMuFL metrics behind {@code DynamicAttachment.getContentWidthSs()}.
     */
    private static final double FAR_XSS = 100.0;

    private static ElementColumn columnAt(StaffElement element, double xSs) {
        var column = ElementColumnTestHelper.columnAt(element, xSs);
        column.setNoteheadWidthSs(NOTEHEAD_WIDTH_SS);
        return column;
    }

    private static ElementColumn columnAt(StaffElement element, double xSs, double leftExtentSs) {
        var column = ElementColumnTestHelper.columnAt(element, xSs, leftExtentSs);
        column.setNoteheadWidthSs(NOTEHEAD_WIDTH_SS);
        return column;
    }

    private static Map<StaffElement, ElementColumn> mapOf(ElementColumn... columns) {
        var columnsByElement = new HashMap<StaffElement, ElementColumn>();

        for (var column : columns) {
            columnsByElement.put(column.getElement(), column);
        }

        return columnsByElement;
    }

    @Nested
    class DefaultEndpoints {

        @Test
        void testNoNeighboursUsesAnchorOriginAndEndNoteheadRightEdge() {
            var line = lineWith(ElementType.CROTCHET, ElementType.CROTCHET);
            var anchor = line.getElement(0);
            var end = line.getElement(1);
            var hairpin = new Crescendo(anchor, end);
            line.addSpan(hairpin);

            var anchorColumn = columnAt(anchor, 0.0);
            var endColumn = columnAt(end, WIDE_SPACING_SS);
            var columns = mapOf(anchorColumn, endColumn);

            var endpoints = HairpinEndpoints.compute(hairpin, line, columns);

            assertThat(endpoints).isNotNull();
            assertThat(endpoints.x1Ss()).isCloseTo(anchorColumn.getXSs(), within(TOLERANCE));
            assertThat(endpoints.x2Ss()).isCloseTo(
                endColumn.getXSs() + endColumn.getNoteheadWidthSs(), within(TOLERANCE));
        }
    }

    /**
     * This is a geometry test, so it drives {@link HairpinEndpoints} directly rather than through
     * the editor. See {@code songscribe.ui.HairpinActionStateTest} for the editor path that
     * produces a hairpin ending on a rest, and {@code docs/hairpin-editing.md} for the rules.
     */
    @Nested
    class RestAsEndElement {

        /** Nonzero so the rest's left edge is distinguishable from its own origin. */
        private static final double REST_LEFT_EXTENT_SS = -0.5;

        @Test
        void testRestEndUsesRestLeftEdgeNotNoteheadRightEdge() {
            var line = lineWith(ElementType.CROTCHET, ElementType.CROTCHET_REST);
            var anchor = line.getElement(0);
            var end = line.getElement(1);
            var hairpin = new Crescendo(anchor, end);
            line.addSpan(hairpin);

            var anchorColumn = columnAt(anchor, 0.0);
            var endColumn = columnAt(end, WIDE_SPACING_SS, REST_LEFT_EXTENT_SS);
            var columns = mapOf(anchorColumn, endColumn);

            var endpoints = HairpinEndpoints.compute(hairpin, line, columns);

            assertThat(endpoints).isNotNull();
            assertThat(endpoints.x2Ss()).isCloseTo(endColumn.getLeftEdgeXSs(), within(TOLERANCE));
            // Sanity: distinct from the default (non-rest) rule, so the rest branch really fired.
            assertThat(endpoints.x2Ss()).isNotCloseTo(
                endColumn.getXSs() + endColumn.getNoteheadWidthSs(), within(TOLERANCE));
        }
    }

    /**
     * This is a geometry test, so it builds the shared-element configuration on the model
     * directly rather than through the editor. See {@code songscribe.ui.HairpinActionStateTest}
     * for the editor path that produces back-to-back hairpins, and {@code docs/hairpin-editing.md}
     * for the rules.
     */
    @Nested
    class BackToBackHairpins {

        @Test
        void testSharedElementLandsBothSidesOnNoteheadCenterMinusPadding() {
            var line = lineWith(ElementType.CROTCHET, ElementType.CROTCHET, ElementType.CROTCHET);
            var first = line.getElement(0);
            var shared = line.getElement(1);
            var last = line.getElement(2);

            var crescendo = new Crescendo(first, shared);
            var diminuendo = new Diminuendo(shared, last);
            line.addSpan(crescendo);
            line.addSpan(diminuendo);

            var firstColumn = columnAt(first, 0.0);
            var sharedColumn = columnAt(shared, WIDE_SPACING_SS);
            var lastColumn = columnAt(last, 2 * WIDE_SPACING_SS);
            var columns = mapOf(firstColumn, sharedColumn, lastColumn);

            var crescendoEndpoints = HairpinEndpoints.compute(crescendo, line, columns);
            var diminuendoEndpoints = HairpinEndpoints.compute(diminuendo, line, columns);

            assertThat(crescendoEndpoints).isNotNull();
            assertThat(diminuendoEndpoints).isNotNull();
            assertThat(crescendoEndpoints.x2Ss()).isCloseTo(
                sharedColumn.getNoteheadCenterXSs() - Hairpin.BACK_TO_BACK_PADDING_SS,
                within(TOLERANCE));
            assertThat(diminuendoEndpoints.x1Ss()).isCloseTo(
                sharedColumn.getNoteheadCenterXSs() + Hairpin.BACK_TO_BACK_PADDING_SS,
                within(TOLERANCE));
            // The two tips must not meet, or the wedges would touch at the shared notehead.
            assertThat(crescendoEndpoints.x2Ss()).isLessThan(diminuendoEndpoints.x1Ss());
        }
    }

    /**
     * {@code compute} answers {@code null} when the hairpin has no geometry in this line. Both
     * ways that can happen are driven here, because a change that dereferenced instead of bailing
     * would throw during layout rather than quietly skipping an unplaceable wedge.
     */
    @Nested
    class UnplaceableHairpin {

        @Test
        void testMissingColumnForAnEndpointYieldsNoEndpoints() {
            var line = lineWith(ElementType.CROTCHET, ElementType.CROTCHET);
            var anchor = line.getElement(0);
            var end = line.getElement(1);
            var hairpin = new Crescendo(anchor, end);
            line.addSpan(hairpin);

            // Only the anchor has a column — the end note was never laid out.
            var columns = mapOf(columnAt(anchor, 0.0));

            assertThat(HairpinEndpoints.compute(hairpin, line, columns)).isNull();
        }

        @Test
        void testEndpointsBelongingToAnotherLineYieldNoEndpoints() {
            var line = lineWith(ElementType.CROTCHET, ElementType.CROTCHET);
            var foreignAnchor = new StaffElement(ElementType.CROTCHET);
            var foreignEnd = new StaffElement(ElementType.CROTCHET);
            var hairpin = new Crescendo(foreignAnchor, foreignEnd);
            line.addSpan(hairpin);

            // Columns exist for the foreign elements, so the span resolves to columns; what fails
            // is resolving the bounds to positions in *this* line.
            var columns = mapOf(columnAt(foreignAnchor, 0.0), columnAt(foreignEnd, WIDE_SPACING_SS));

            assertThat(HairpinEndpoints.compute(hairpin, line, columns)).isNull();
        }
    }

    @Nested
    class MinimumLengthGuard {

        /** Narrower than {@link Hairpin#MINIMUM_LENGTH_SS}, so the guard must extend x2Ss. */
        private static final double NARROW_SPACING_SS = 0.1;

        @Test
        void testDegenerateSpanIsExtendedToMinimumLength() {
            var line = lineWith(ElementType.CROTCHET, ElementType.CROTCHET);
            var anchor = line.getElement(0);
            var end = line.getElement(1);
            var hairpin = new Crescendo(anchor, end);
            line.addSpan(hairpin);

            var anchorColumn = columnAt(anchor, 0.0);
            var endColumn = columnAt(end, NARROW_SPACING_SS);
            var columns = mapOf(anchorColumn, endColumn);

            var endpoints = HairpinEndpoints.compute(hairpin, line, columns);

            assertThat(endpoints).isNotNull();
            assertThat(endpoints.x2Ss() - endpoints.x1Ss())
                .as("the degenerate span must be extended to the minimum drawn length")
                .isCloseTo(Hairpin.MINIMUM_LENGTH_SS, within(TOLERANCE));
            assertThat(endpoints.x2Ss()).isCloseTo(
                endpoints.x1Ss() + Hairpin.MINIMUM_LENGTH_SS, within(TOLERANCE));
        }

        @Test
        void testWideningIsMeasuredFromTheTipPulledBackByAPrecedingDynamic() {
            // The guard runs after the bound rules, so on a wedge preceded by a text dynamic it
            // must measure from the pulled-back left tip, not from the anchor's origin. Measuring
            // from the origin would leave the drawn wedge short of its minimum by exactly the
            // pullback, which is the bug this pins.
            var line = lineWith(ElementType.CROTCHET, ElementType.CROTCHET, ElementType.CROTCHET);
            var dynamicHost = line.getElement(0);
            var anchor = line.getElement(1);
            var end = line.getElement(2);

            var dynamic = new DynamicAttachment(DynamicType.PIANO);
            dynamicHost.addAttachment(dynamic);

            var hairpin = new Crescendo(anchor, end);
            line.addSpan(hairpin);

            // All three columns sit at the same X, so the span is degenerate on its own and the
            // only thing separating the two candidate measuring points is the pullback.
            var dynamicHostColumn = columnAt(dynamicHost, 0.0);
            var anchorColumn = columnAt(anchor, 0.0);
            var endColumn = columnAt(end, 0.0);
            var columns = mapOf(dynamicHostColumn, anchorColumn, endColumn);

            var endpoints = HairpinEndpoints.compute(hairpin, line, columns);

            assertThat(endpoints).isNotNull();
            // The bound rule really did move the left tip off the anchor's origin, so the two
            // candidate measuring points genuinely differ and the width assertion tells them apart.
            assertThat(endpoints.x1Ss()).isNotCloseTo(anchorColumn.getXSs(), within(TOLERANCE));
            assertThat(endpoints.widthSs())
                .as("the minimum length is measured from the pulled-back tip, not the anchor origin")
                .isCloseTo(Hairpin.MINIMUM_LENGTH_SS, within(TOLERANCE));
        }
    }

    @Nested
    class AdjacentTextDynamic {

        @Test
        void testDynamicBeforeAnchorPullsX1LeftOfAnchorOrigin() {
            var line = lineWith(ElementType.CROTCHET, ElementType.CROTCHET, ElementType.CROTCHET);
            var dynamicHost = line.getElement(0);
            var anchor = line.getElement(1);
            var end = line.getElement(2);

            var dynamic = new DynamicAttachment(DynamicType.PIANO);
            dynamicHost.addAttachment(dynamic);

            var hairpin = new Crescendo(anchor, end);
            line.addSpan(hairpin);

            var dynamicHostColumn = columnAt(dynamicHost, 0.0);
            var anchorColumn = columnAt(anchor, FAR_XSS);
            var endColumn = columnAt(end, FAR_XSS + WIDE_SPACING_SS);
            var columns = mapOf(dynamicHostColumn, anchorColumn, endColumn);

            var endpoints = HairpinEndpoints.compute(hairpin, line, columns);

            assertThat(endpoints).isNotNull();
            var expectedX1Ss = HairpinEndpoints.dynamicLeftEdgeSs(dynamicHostColumn, dynamic)
                + HairpinEndpoints.dynamicWidthSs(dynamic) + Hairpin.BOUND_PADDING_SS;
            assertThat(endpoints.x1Ss()).isCloseTo(expectedX1Ss, within(TOLERANCE));
            // Direction: the pullback is an assignment from the dynamic's extent, not a clamp to the
            // anchor column, so x1Ss lands left of the anchor's own origin, not merely near it.
            assertThat(endpoints.x1Ss()).isLessThan(anchorColumn.getXSs());
        }

        @Test
        void testDynamicAfterEndPushesX2RightOfEndNoteheadEdge() {
            var line = lineWith(ElementType.CROTCHET, ElementType.CROTCHET, ElementType.CROTCHET);
            var anchor = line.getElement(0);
            var end = line.getElement(1);
            var dynamicHost = line.getElement(2);

            var dynamic = new DynamicAttachment(DynamicType.FORTE);
            dynamicHost.addAttachment(dynamic);

            var hairpin = new Crescendo(anchor, end);
            line.addSpan(hairpin);

            var anchorColumn = columnAt(anchor, 0.0);
            var endColumn = columnAt(end, WIDE_SPACING_SS);
            var dynamicHostColumn = columnAt(dynamicHost, FAR_XSS);
            var columns = mapOf(anchorColumn, endColumn, dynamicHostColumn);

            var endpoints = HairpinEndpoints.compute(hairpin, line, columns);

            assertThat(endpoints).isNotNull();
            var expectedX2Ss =
                HairpinEndpoints.dynamicLeftEdgeSs(dynamicHostColumn, dynamic) - Hairpin.BOUND_PADDING_SS;
            assertThat(endpoints.x2Ss()).isCloseTo(expectedX2Ss, within(TOLERANCE));
            // Direction: the push is an assignment from the dynamic's extent, not a clamp to the end
            // column, so x2Ss lands right of the end note's own notehead edge, not merely near it.
            assertThat(endpoints.x2Ss())
                .isGreaterThan(endColumn.getXSs() + endColumn.getNoteheadWidthSs());
        }

        @Test
        void testDynamicTwoElementsBeforeAnchorChangesNothing() {
            var line = lineWith(
                ElementType.CROTCHET, ElementType.CROTCHET, ElementType.CROTCHET, ElementType.CROTCHET);
            var farHost = line.getElement(0);
            var buffer = line.getElement(1);
            var anchor = line.getElement(2);
            var end = line.getElement(3);

            farHost.addAttachment(new DynamicAttachment(DynamicType.PIANO));

            var hairpin = new Crescendo(anchor, end);
            line.addSpan(hairpin);

            var anchorColumn = columnAt(anchor, 0.0);
            var endColumn = columnAt(end, WIDE_SPACING_SS);
            var columns = mapOf(
                columnAt(farHost, -2 * WIDE_SPACING_SS), columnAt(buffer, -WIDE_SPACING_SS),
                anchorColumn, endColumn);

            var endpoints = HairpinEndpoints.compute(hairpin, line, columns);

            assertThat(endpoints).isNotNull();
            assertThat(endpoints.x1Ss())
                .as("a dynamic two elements before the anchor must not move x1Ss")
                .isCloseTo(anchorColumn.getXSs(), within(TOLERANCE));
        }

        @Test
        void testDynamicTwoElementsAfterEndChangesNothing() {
            var line = lineWith(
                ElementType.CROTCHET, ElementType.CROTCHET, ElementType.CROTCHET, ElementType.CROTCHET);
            var anchor = line.getElement(0);
            var end = line.getElement(1);
            var buffer = line.getElement(2);
            var farHost = line.getElement(3);

            farHost.addAttachment(new DynamicAttachment(DynamicType.FORTE));

            var hairpin = new Crescendo(anchor, end);
            line.addSpan(hairpin);

            var anchorColumn = columnAt(anchor, 0.0);
            var endColumn = columnAt(end, WIDE_SPACING_SS);
            var columns = mapOf(
                anchorColumn, endColumn,
                columnAt(buffer, 2 * WIDE_SPACING_SS), columnAt(farHost, FAR_XSS));

            var endpoints = HairpinEndpoints.compute(hairpin, line, columns);

            assertThat(endpoints).isNotNull();
            assertThat(endpoints.x2Ss())
                .as("a dynamic two elements after the end must not move x2Ss")
                .isCloseTo(endColumn.getXSs() + endColumn.getNoteheadWidthSs(), within(TOLERANCE));
        }
    }
}
