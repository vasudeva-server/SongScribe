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

        /**
         * A width strictly below {@link Hairpin#MINIMUM_LENGTH_SS}, used to pin a dynamic-placed
         * tip at an exact, known-short distance from the other tip regardless of real glyph
         * metrics.
         */
        private static final double SUB_MINIMUM_WIDTH_SS = Hairpin.MINIMUM_LENGTH_SS / 2;

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

        // Widening moves the right tip and nothing else, so which tip a dynamic placed decides
        // whether the floor may still apply. The four tests below cover both sides of that split:
        // a dynamic on the left (whether the anchor's own or the note before it) leaves the floor
        // in force, because widening carries the right tip away from that glyph; a dynamic on the
        // right (the end's own or the note after it) suspends the floor, because widening would
        // drive the wedge straight back under the glyph the padding just cleared.

        @Test
        void testWideningIsMeasuredFromTheTipPulledBackByAPrecedingDynamic() {
            var line = lineWith(ElementType.CROTCHET, ElementType.CROTCHET, ElementType.CROTCHET);
            var dynamicHost = line.getElement(0);
            var anchor = line.getElement(1);
            var end = line.getElement(2);

            var dynamic = new DynamicAttachment(DynamicType.PIANO);
            dynamicHost.addAttachment(dynamic);

            var hairpin = new Crescendo(anchor, end);
            line.addSpan(hairpin);

            var dynamicHostColumn = columnAt(dynamicHost, 0.0);
            var expectedX1Ss =
                HairpinEndpoints.dynamicAdvanceRightEdgeSs(dynamicHostColumn, dynamic)
                    + Hairpin.BOUND_PADDING_SS;
            // The anchor sits exactly at the pulled-back tip and the end exactly
            // SUB_MINIMUM_WIDTH_SS past it, so the raw span width is pinned below the floor
            // regardless of the real glyph metrics behind expectedX1Ss. Measuring the widening
            // from the anchor's own origin instead of the pulled-back tip would leave the drawn
            // wedge short by exactly the pullback, which is what this pins.
            var anchorColumn = columnAt(anchor, expectedX1Ss);
            var endColumn =
                columnAt(end, expectedX1Ss + SUB_MINIMUM_WIDTH_SS - NOTEHEAD_WIDTH_SS);
            var columns = mapOf(dynamicHostColumn, anchorColumn, endColumn);

            var endpoints = HairpinEndpoints.compute(hairpin, line, columns);

            assertThat(endpoints).isNotNull();
            assertThat(endpoints.x1Ss()).isCloseTo(expectedX1Ss, within(TOLERANCE));
            assertThat(endpoints.widthSs())
                .as("a dynamic on the left leaves the floor in force, measured from the "
                    + "pulled-back tip")
                .isCloseTo(Hairpin.MINIMUM_LENGTH_SS, within(TOLERANCE));
        }

        @Test
        void testWideningStillAppliesWhenTheAnchorsOwnDynamicPlacedTheLeftTip() {
            var line = lineWith(ElementType.CROTCHET, ElementType.CROTCHET);
            var anchor = line.getElement(0);
            var end = line.getElement(1);

            var dynamic = new DynamicAttachment(DynamicType.FORTE);
            anchor.addAttachment(dynamic);

            var hairpin = new Crescendo(anchor, end);
            line.addSpan(hairpin);

            var anchorColumn = columnAt(anchor, 0.0);
            var expectedX1Ss =
                HairpinEndpoints.dynamicAdvanceRightEdgeSs(anchorColumn, dynamic)
                    + Hairpin.BOUND_PADDING_SS;
            // The end sits exactly SUB_MINIMUM_WIDTH_SS past the pulled-back tip, so the raw width
            // is below the floor whatever the real glyph metrics are.
            var endColumn =
                columnAt(end, expectedX1Ss + SUB_MINIMUM_WIDTH_SS - NOTEHEAD_WIDTH_SS);
            var columns = mapOf(anchorColumn, endColumn);

            var endpoints = HairpinEndpoints.compute(hairpin, line, columns);

            assertThat(endpoints).isNotNull();
            assertThat(endpoints.x1Ss())
                .as("the left tip stays where the anchor's own dynamic put it")
                .isCloseTo(expectedX1Ss, within(TOLERANCE));
            assertThat(endpoints.widthSs())
                .as("an f< whose notes are too close still draws a visible wedge — widening "
                    + "moves the right tip away from the glyph, never back under it")
                .isCloseTo(Hairpin.MINIMUM_LENGTH_SS, within(TOLERANCE));
        }

        @Test
        void testWideningIsSkippedWhenAFollowingDynamicPlacedTheRightTip() {
            var line = lineWith(ElementType.CROTCHET, ElementType.CROTCHET, ElementType.CROTCHET);
            var anchor = line.getElement(0);
            var end = line.getElement(1);
            var dynamicHost = line.getElement(2);

            var dynamic = new DynamicAttachment(DynamicType.PIANO);
            dynamicHost.addAttachment(dynamic);

            var hairpin = new Crescendo(anchor, end);
            line.addSpan(hairpin);

            var dynamicHostColumn = columnAt(dynamicHost, WIDE_SPACING_SS);
            var expectedX2Ss =
                HairpinEndpoints.dynamicAdvanceLeftEdgeSs(dynamicHostColumn, dynamic)
                    - Hairpin.BOUND_PADDING_SS;
            // The anchor sits exactly SUB_MINIMUM_WIDTH_SS before the pushed-back tip, so the raw
            // width is below the floor whatever the real glyph metrics are.
            var anchorColumn = columnAt(anchor, expectedX2Ss - SUB_MINIMUM_WIDTH_SS);
            var endColumn = columnAt(end, expectedX2Ss - SUB_MINIMUM_WIDTH_SS / 2);
            var columns = mapOf(anchorColumn, endColumn, dynamicHostColumn);

            var endpoints = HairpinEndpoints.compute(hairpin, line, columns);

            assertThat(endpoints).isNotNull();
            assertThat(endpoints.x2Ss())
                .as("the right tip stays clear of the following dynamic rather than being widened "
                    + "back under it")
                .isCloseTo(expectedX2Ss, within(TOLERANCE));
            assertThat(endpoints.widthSs())
                .as("the constructed width sits below the floor, precisely to prove it is skipped")
                .isCloseTo(SUB_MINIMUM_WIDTH_SS, within(TOLERANCE));
        }

        @Test
        void testRightTipPlacedByOwnBoundDynamicBelowMinimumIsNotWidened() {
            var line = lineWith(ElementType.CROTCHET, ElementType.CROTCHET);
            var anchor = line.getElement(0);
            var end = line.getElement(1);

            var dynamic = new DynamicAttachment(DynamicType.FORTE);
            end.addAttachment(dynamic);

            var hairpin = new Crescendo(anchor, end);
            line.addSpan(hairpin);

            var endColumn = columnAt(end, WIDE_SPACING_SS);
            var expectedX2Ss =
                HairpinEndpoints.dynamicAdvanceLeftEdgeSs(endColumn, dynamic)
                    - Hairpin.BOUND_PADDING_SS;
            // The anchor sits exactly SUB_MINIMUM_WIDTH_SS before the dynamic-placed right tip, so
            // the raw width is pinned below the floor regardless of the real glyph metrics.
            var anchorColumn = columnAt(anchor, expectedX2Ss - SUB_MINIMUM_WIDTH_SS);
            var columns = mapOf(anchorColumn, endColumn);

            var endpoints = HairpinEndpoints.compute(hairpin, line, columns);

            assertThat(endpoints).isNotNull();
            assertThat(endpoints.widthSs())
                .as("the constructed width sits below the floor, precisely to prove it is skipped")
                .isLessThan(Hairpin.MINIMUM_LENGTH_SS);
            assertThat(endpoints.x2Ss())
                .as("a dynamic-placed tip below the floor is not widened back under the glyph")
                .isCloseTo(expectedX2Ss, within(TOLERANCE));
        }

        @Test
        void testDynamicPlacedTipsGivingNegativeWidthClampToEqualTips() {
            var line = lineWith(ElementType.CROTCHET, ElementType.CROTCHET);
            var anchor = line.getElement(0);
            var end = line.getElement(1);

            var dynamic = new DynamicAttachment(DynamicType.FORTE);
            end.addAttachment(dynamic);

            var hairpin = new Crescendo(anchor, end);
            line.addSpan(hairpin);

            var endColumn = columnAt(end, WIDE_SPACING_SS);
            var expectedX2Ss =
                HairpinEndpoints.dynamicAdvanceLeftEdgeSs(endColumn, dynamic)
                    - Hairpin.BOUND_PADDING_SS;
            // The anchor sits to the right of the dynamic-placed right tip, so the naive width is
            // negative.
            var anchorColumn = columnAt(anchor, expectedX2Ss + WIDE_SPACING_SS);
            var columns = mapOf(anchorColumn, endColumn);

            var endpoints = HairpinEndpoints.compute(hairpin, line, columns);

            assertThat(endpoints).isNotNull();
            assertThat(endpoints.x2Ss())
                .as("a negative dynamic-placed width clamps to the left tip rather than going negative")
                .isCloseTo(endpoints.x1Ss(), within(TOLERANCE));
        }

        @Test
        void testBackToBackShortSpanWithNoDynamicStillWidensToMinimumLength() {
            var line = lineWith(ElementType.CROTCHET, ElementType.CROTCHET, ElementType.CROTCHET);
            var first = line.getElement(0);
            var shared = line.getElement(1);
            var last = line.getElement(2);

            var crescendo = new Crescendo(first, shared);
            var diminuendo = new Diminuendo(shared, last);
            line.addSpan(crescendo);
            line.addSpan(diminuendo);

            var firstColumn = columnAt(first, 0.0);
            var sharedColumn = columnAt(shared, NARROW_SPACING_SS);
            var lastColumn = columnAt(last, 2 * NARROW_SPACING_SS);
            var columns = mapOf(firstColumn, sharedColumn, lastColumn);

            var crescendoEndpoints = HairpinEndpoints.compute(crescendo, line, columns);

            assertThat(crescendoEndpoints).isNotNull();
            assertThat(crescendoEndpoints.widthSs())
                .as("a back-to-back span with no dynamic on either bound still meets the floor")
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
            var expectedX1Ss =
                HairpinEndpoints.dynamicAdvanceRightEdgeSs(dynamicHostColumn, dynamic)
                    + Hairpin.BOUND_PADDING_SS;
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
                HairpinEndpoints.dynamicAdvanceLeftEdgeSs(dynamicHostColumn, dynamic)
                    - Hairpin.BOUND_PADDING_SS;
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

    @Nested
    class DynamicOnOwnBound {

        @Test
        void testDynamicOnAnchorPullsX1ToGlyphRightEdgePlusPadding() {
            var line = lineWith(ElementType.CROTCHET, ElementType.CROTCHET);
            var anchor = line.getElement(0);
            var end = line.getElement(1);

            var dynamic = new DynamicAttachment(DynamicType.FORTE);
            anchor.addAttachment(dynamic);

            var hairpin = new Crescendo(anchor, end);
            line.addSpan(hairpin);

            var anchorColumn = columnAt(anchor, 0.0);
            var endColumn = columnAt(end, WIDE_SPACING_SS);
            var columns = mapOf(anchorColumn, endColumn);

            var endpoints = HairpinEndpoints.compute(hairpin, line, columns);

            assertThat(endpoints).isNotNull();
            var expectedX1Ss =
                HairpinEndpoints.dynamicAdvanceRightEdgeSs(anchorColumn, dynamic)
                    + Hairpin.BOUND_PADDING_SS;
            assertThat(endpoints.x1Ss()).isCloseTo(expectedX1Ss, within(TOLERANCE));
            // Direction: the own-bound rule beats the anchor column's own origin, not merely lands
            // near it.
            assertThat(endpoints.x1Ss()).isNotCloseTo(anchorColumn.getXSs(), within(TOLERANCE));
        }

        @Test
        void testDynamicOnEndPullsX2ToGlyphLeftEdgeMinusPadding() {
            var line = lineWith(ElementType.CROTCHET, ElementType.CROTCHET);
            var anchor = line.getElement(0);
            var end = line.getElement(1);

            var dynamic = new DynamicAttachment(DynamicType.PIANO);
            end.addAttachment(dynamic);

            var hairpin = new Crescendo(anchor, end);
            line.addSpan(hairpin);

            var anchorColumn = columnAt(anchor, 0.0);
            var endColumn = columnAt(end, WIDE_SPACING_SS);
            var columns = mapOf(anchorColumn, endColumn);

            var endpoints = HairpinEndpoints.compute(hairpin, line, columns);

            assertThat(endpoints).isNotNull();
            var expectedX2Ss =
                HairpinEndpoints.dynamicAdvanceLeftEdgeSs(endColumn, dynamic)
                    - Hairpin.BOUND_PADDING_SS;
            assertThat(endpoints.x2Ss()).isCloseTo(expectedX2Ss, within(TOLERANCE));
            // Direction: the own-bound rule beats the end notehead's own right edge, not merely
            // lands near it.
            assertThat(endpoints.x2Ss()).isNotCloseTo(
                endColumn.getXSs() + endColumn.getNoteheadWidthSs(), within(TOLERANCE));
        }

        /**
         * The {@code <f>} shape: two opposite-type hairpins sharing one element, with a text
         * dynamic on that shared element. See {@code BackToBackHairpins} above for the same shape
         * with no dynamic, where the shared notehead's center is the correct expectation instead.
         */
        @Test
        void testBackToBackWithDynamicOnSharedElementClearsGlyphOnBothSides() {
            var line = lineWith(ElementType.CROTCHET, ElementType.CROTCHET, ElementType.CROTCHET);
            var first = line.getElement(0);
            var shared = line.getElement(1);
            var last = line.getElement(2);

            var dynamic = new DynamicAttachment(DynamicType.FORTE);
            shared.addAttachment(dynamic);

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

            var expectedCrescendoX2Ss =
                HairpinEndpoints.dynamicAdvanceLeftEdgeSs(sharedColumn, dynamic)
                    - Hairpin.BOUND_PADDING_SS;
            var expectedDiminuendoX1Ss =
                HairpinEndpoints.dynamicAdvanceRightEdgeSs(sharedColumn, dynamic)
                    + Hairpin.BOUND_PADDING_SS;

            assertThat(crescendoEndpoints.x2Ss()).isCloseTo(expectedCrescendoX2Ss, within(TOLERANCE));
            assertThat(diminuendoEndpoints.x1Ss())
                .isCloseTo(expectedDiminuendoX1Ss, within(TOLERANCE));
            // Explicitly not the plain back-to-back split: the own-bound rule outranks it
            // (hairpin.cc:216 before :222), so both wedges clear the glyph rather than stopping at
            // the shared notehead's center.
            assertThat(crescendoEndpoints.x2Ss()).isNotCloseTo(
                sharedColumn.getNoteheadCenterXSs() - Hairpin.BACK_TO_BACK_PADDING_SS,
                within(TOLERANCE));
            assertThat(diminuendoEndpoints.x1Ss()).isNotCloseTo(
                sharedColumn.getNoteheadCenterXSs() + Hairpin.BACK_TO_BACK_PADDING_SS,
                within(TOLERANCE));
        }

        @Test
        void testOwnBoundBeatsAdjacentDynamicOnLeftTip() {
            var line = lineWith(ElementType.CROTCHET, ElementType.CROTCHET, ElementType.CROTCHET);
            var before = line.getElement(0);
            var anchor = line.getElement(1);
            var end = line.getElement(2);

            var beforeDynamic = new DynamicAttachment(DynamicType.PIANO);
            before.addAttachment(beforeDynamic);
            var ownDynamic = new DynamicAttachment(DynamicType.FORTE);
            anchor.addAttachment(ownDynamic);

            var hairpin = new Crescendo(anchor, end);
            line.addSpan(hairpin);

            var beforeColumn = columnAt(before, 0.0);
            var anchorColumn = columnAt(anchor, WIDE_SPACING_SS);
            var endColumn = columnAt(end, 2 * WIDE_SPACING_SS);
            var columns = mapOf(beforeColumn, anchorColumn, endColumn);

            var endpoints = HairpinEndpoints.compute(hairpin, line, columns);

            assertThat(endpoints).isNotNull();
            var expectedX1Ss =
                HairpinEndpoints.dynamicAdvanceRightEdgeSs(anchorColumn, ownDynamic)
                    + Hairpin.BOUND_PADDING_SS;
            var adjacentRuleX1Ss =
                HairpinEndpoints.dynamicAdvanceRightEdgeSs(beforeColumn, beforeDynamic)
                    + Hairpin.BOUND_PADDING_SS;
            assertThat(endpoints.x1Ss())
                .as("the anchor's own dynamic must win over the one before it")
                .isCloseTo(expectedX1Ss, within(TOLERANCE));
            assertThat(endpoints.x1Ss()).isNotCloseTo(adjacentRuleX1Ss, within(TOLERANCE));
        }

        @Test
        void testOwnBoundBeatsAdjacentDynamicOnRightTip() {
            var line = lineWith(ElementType.CROTCHET, ElementType.CROTCHET, ElementType.CROTCHET);
            var anchor = line.getElement(0);
            var end = line.getElement(1);
            var after = line.getElement(2);

            var ownDynamic = new DynamicAttachment(DynamicType.FORTE);
            end.addAttachment(ownDynamic);
            var afterDynamic = new DynamicAttachment(DynamicType.PIANO);
            after.addAttachment(afterDynamic);

            var hairpin = new Crescendo(anchor, end);
            line.addSpan(hairpin);

            var anchorColumn = columnAt(anchor, 0.0);
            var endColumn = columnAt(end, WIDE_SPACING_SS);
            var afterColumn = columnAt(after, 2 * WIDE_SPACING_SS);
            var columns = mapOf(anchorColumn, endColumn, afterColumn);

            var endpoints = HairpinEndpoints.compute(hairpin, line, columns);

            assertThat(endpoints).isNotNull();
            var expectedX2Ss =
                HairpinEndpoints.dynamicAdvanceLeftEdgeSs(endColumn, ownDynamic)
                    - Hairpin.BOUND_PADDING_SS;
            var adjacentRuleX2Ss =
                HairpinEndpoints.dynamicAdvanceLeftEdgeSs(afterColumn, afterDynamic)
                    - Hairpin.BOUND_PADDING_SS;
            assertThat(endpoints.x2Ss())
                .as("the end's own dynamic must win over the one after it")
                .isCloseTo(expectedX2Ss, within(TOLERANCE));
            assertThat(endpoints.x2Ss()).isNotCloseTo(adjacentRuleX2Ss, within(TOLERANCE));
        }

        @Test
        void testDynamicsOnBothBoundsPlaceBothTipsIndependently() {
            // The f<p shape. Every other test in this class leaves one bound bare, so a lookup
            // that read the end element where it meant the anchor (or the reverse) would still
            // land on the one dynamic present and pass. Here the two bounds carry different
            // glyphs at different positions, so a swap moves both tips to the wrong place.
            var line = lineWith(ElementType.CROTCHET, ElementType.CROTCHET);
            var anchor = line.getElement(0);
            var end = line.getElement(1);

            var anchorDynamic = new DynamicAttachment(DynamicType.FORTE);
            anchor.addAttachment(anchorDynamic);
            var endDynamic = new DynamicAttachment(DynamicType.PIANO);
            end.addAttachment(endDynamic);

            var hairpin = new Crescendo(anchor, end);
            line.addSpan(hairpin);

            var anchorColumn = columnAt(anchor, 0.0);
            var endColumn = columnAt(end, WIDE_SPACING_SS);
            var columns = mapOf(anchorColumn, endColumn);

            var endpoints = HairpinEndpoints.compute(hairpin, line, columns);

            assertThat(endpoints).isNotNull();
            assertThat(endpoints.x1Ss())
                .as("the left tip clears the anchor's own forte")
                .isCloseTo(
                    HairpinEndpoints.dynamicAdvanceRightEdgeSs(anchorColumn, anchorDynamic)
                        + Hairpin.BOUND_PADDING_SS,
                    within(TOLERANCE));
            assertThat(endpoints.x2Ss())
                .as("the right tip stops short of the end's own piano")
                .isCloseTo(
                    HairpinEndpoints.dynamicAdvanceLeftEdgeSs(endColumn, endDynamic)
                        - Hairpin.BOUND_PADDING_SS,
                    within(TOLERANCE));
        }

        @Test
        void testDynamicOnAnUnpositionedNeighbourFallsBackToTheDefaultRule() {
            // A dynamic sitting on an element the caller supplied no column for cannot say where
            // a tip goes, so the rule must decline rather than reach for a position it does not
            // have. This is the branch a cross-line neighbour would take.
            var line = lineWith(ElementType.CROTCHET, ElementType.CROTCHET, ElementType.CROTCHET);
            var unpositioned = line.getElement(0);
            var anchor = line.getElement(1);
            var end = line.getElement(2);

            unpositioned.addAttachment(new DynamicAttachment(DynamicType.PIANO));

            var hairpin = new Crescendo(anchor, end);
            line.addSpan(hairpin);

            var anchorColumn = columnAt(anchor, WIDE_SPACING_SS);
            var endColumn = columnAt(end, 2 * WIDE_SPACING_SS);
            // Deliberately no column for the element carrying the dynamic.
            var columns = mapOf(anchorColumn, endColumn);

            var endpoints = HairpinEndpoints.compute(hairpin, line, columns);

            assertThat(endpoints).isNotNull();
            assertThat(endpoints.x1Ss())
                .as("with no column for the dynamic's element, the left tip falls back to the "
                    + "anchor column's own origin")
                .isCloseTo(anchorColumn.getXSs(), within(TOLERANCE));
        }
    }
}
