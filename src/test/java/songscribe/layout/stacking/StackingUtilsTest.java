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

package songscribe.layout.stacking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.CollisionRegion;
import songscribe.dom.LineElement;
import songscribe.dom.StaffElement;
import songscribe.dom.Trill;
import songscribe.layout.LayoutResult;
import songscribe.layout.StaffExtents;

class StackingUtilsTest extends UnitTest {

    @SuppressWarnings("NullAway")
    private static <T> T require(@Nullable T value, String description) {
        assertThat(value).describedAs(description).isNotNull();
        return value;
    }

    // Tolerance for floating-point oracle comparisons
    private static final double TOLERANCE = 1e-9;

    // A staff position that is strictly inside the staff (above the middle line,
    // but still below the top staff line threshold so anchorCeilingSs returns STAFF_TOP_Y_SS)
    private static final int WITHIN_STAFF_POSITION = 0;

    // A staff position at the top staff line (boundary — still uses ceiling formula)
    private static final int AT_TOP_STAFF_LINE_POSITION = StackingUtils.TOP_STAFF_LINE_POSITION;

    // A staff position one unit above the top staff line (strictly above — uses ceiling formula)
    private static final int ABOVE_TOP_STAFF_LINE_POSITION = StackingUtils.TOP_STAFF_LINE_POSITION - 2;

    // Margin used in stackAboveWithRegions tests
    private static final double REGION_MARGIN_SS = 0.5;

    // Region A: taller sub-region, drives elementYSs
    private static final double REGION_A_X_OFFSET_SS = 0.0;
    private static final double REGION_A_Y_OFFSET_SS = 0.0;
    private static final double REGION_A_WIDTH_SS = 4.0;
    private static final double REGION_A_HEIGHT_SS = 1.5;

    // Region B: shorter sub-region (would produce a higher elementYSs on its own)
    private static final double REGION_B_X_OFFSET_SS = 6.0;
    private static final double REGION_B_Y_OFFSET_SS = 0.5;
    private static final double REGION_B_WIDTH_SS = 4.0;
    private static final double REGION_B_HEIGHT_SS = 0.75;

    // Anchor X and total width for the multi-region test
    private static final double ELEMENT_X_SS = 1.0;
    private static final double ELEMENT_WIDTH_SS = 12.0;

    // Width of a staff line large enough that all test positions land within it
    private static final double LINE_WIDTH_SS = 100.0;

    // -------------------------------------------------------------------------
    // Row 5 — anchorCeilingSs(sp): within or below staff → STAFF_TOP_Y_SS
    // -------------------------------------------------------------------------

    @Test
    void testAnchorCeilingWithinStaffReturnsStaffTopY() {
        // sp > TOP_STAFF_LINE_POSITION: the note is within or below the staff,
        // so the anchor ceiling is pinned to the top staff line.
        var result = StackingUtils.anchorCeilingSs(WITHIN_STAFF_POSITION);
        assertThat(result).isEqualTo(StackingUtils.STAFF_TOP_Y_SS);
    }

    @Test
    void testAnchorCeilingBelowTopLineReturnsStaffTopYForPositivePosition() {
        // Any positive sp (below middle line) should also return STAFF_TOP_Y_SS.
        var belowMiddlePosition = 4;
        var result = StackingUtils.anchorCeilingSs(belowMiddlePosition);
        assertThat(result).isEqualTo(StackingUtils.STAFF_TOP_Y_SS);
    }

    // -------------------------------------------------------------------------
    // Row 6 — anchorCeilingSs(sp): at or above top staff line → formula result
    // -------------------------------------------------------------------------

    @Test
    void testAnchorCeilingAtTopStaffLineUsesNoteHeadFormula() {
        // sp == TOP_STAFF_LINE_POSITION: boundary that does NOT use STAFF_TOP_Y_SS;
        // result is noteHeadY - NOTE_HEAD_RADIUS_SS.
        var sp = AT_TOP_STAFF_LINE_POSITION;
        var expectedNoteHeadYSs = sp * StaffExtents.STAFF_POSITION_OFFSET_SS;
        var expectedCeilingSs = expectedNoteHeadYSs - StackingUtils.NOTE_HEAD_RADIUS_SS;

        var result = StackingUtils.anchorCeilingSs(sp);
        assertThat(result).isCloseTo(expectedCeilingSs, within(TOLERANCE));
    }

    @Test
    void testAnchorCeilingAboveTopStaffLineUsesNoteHeadFormula() {
        // sp < TOP_STAFF_LINE_POSITION: well above the staff — same formula.
        var sp = ABOVE_TOP_STAFF_LINE_POSITION;
        var expectedNoteHeadYSs = sp * StaffExtents.STAFF_POSITION_OFFSET_SS;
        var expectedCeilingSs = expectedNoteHeadYSs - StackingUtils.NOTE_HEAD_RADIUS_SS;

        var result = StackingUtils.anchorCeilingSs(sp);
        assertThat(result).isCloseTo(expectedCeilingSs, within(TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // Row 8 — stackAboveWithRegions: multi-region min-ceiling + per-region reserve
    // -------------------------------------------------------------------------

    @Test
    void testStackAboveWithRegionsPlacesElementAtMinCeilingAcrossRegions() {
        // Setup: empty extents (top[] = 0.0 by default) and staff position 0
        // so anchorSs = STAFF_TOP_Y_SS = -2.0.
        //
        // For each region: regionCeilingSs = min(0.0, -2.0) = -2.0 (anchor wins).
        //
        // Region A (no yOffset): regionYSs = -2.0 - REGION_MARGIN_SS - 0 - 1.5
        // Region B (yOffset=0.5): regionYSs = -2.0 - REGION_MARGIN_SS - 0.5 - 0.75
        //
        // elementYSs = min(regionYSs_A, regionYSs_B) = min(-4.0, -3.75) = -4.0
        //
        // Oracle: STAFF_TOP_Y_SS - REGION_MARGIN_SS - REGION_A_HEIGHT_SS
        var extents = new StaffExtents(LINE_WIDTH_SS);
        var regionA = new CollisionRegion(
            REGION_A_X_OFFSET_SS, REGION_A_Y_OFFSET_SS, REGION_A_WIDTH_SS, REGION_A_HEIGHT_SS);
        var regionB = new CollisionRegion(
            REGION_B_X_OFFSET_SS, REGION_B_Y_OFFSET_SS, REGION_B_WIDTH_SS, REGION_B_HEIGHT_SS);

        var element = mock(LineElement.class);
        var builder = new LayoutResult.Builder();

        StackingUtils.stackAboveWithRegions(
            extents,
            element,
            List.of(regionA, regionB),
            ELEMENT_X_SS, ELEMENT_WIDTH_SS, REGION_MARGIN_SS,
            WITHIN_STAFF_POSITION,
            builder);

        // Oracle: region A drives the result (it has the larger combined yOffset+height)
        var expectedElementYSs =
            StackingUtils.STAFF_TOP_Y_SS - REGION_MARGIN_SS - REGION_A_HEIGHT_SS;
        var layout = require(builder.build().getDecorationLayout(element), "decoration layout");
        assertThat(layout.ySs()).isCloseTo(expectedElementYSs, within(TOLERANCE));
    }

    @Test
    void testStackAboveWithRegionsReservesEachRegionIndependently() {
        // After stackAboveWithRegions, each sub-region should be independently reserved
        // in extents at its visual top. Region B, being shorter (higher yOffset),
        // should leave room for a later element to nestle under it where only region A
        // is present — i.e., the reservation at region B's x-range is shallower.
        //
        // elementYSs = -4.0 (from oracle above, margin=0.5)
        // regionA reservation top = elementYSs + yOffsetA = -4.0 + 0.0 = -4.0
        // regionB reservation top = elementYSs + yOffsetB = -4.0 + 0.5 = -3.5
        var extents = new StaffExtents(LINE_WIDTH_SS);
        var regionA = new CollisionRegion(
            REGION_A_X_OFFSET_SS, REGION_A_Y_OFFSET_SS, REGION_A_WIDTH_SS, REGION_A_HEIGHT_SS);
        var regionB = new CollisionRegion(
            REGION_B_X_OFFSET_SS, REGION_B_Y_OFFSET_SS, REGION_B_WIDTH_SS, REGION_B_HEIGHT_SS);

        var element = mock(LineElement.class);
        var builder = new LayoutResult.Builder();

        StackingUtils.stackAboveWithRegions(
            extents,
            element,
            List.of(regionA, regionB),
            ELEMENT_X_SS, ELEMENT_WIDTH_SS, REGION_MARGIN_SS,
            WITHIN_STAFF_POSITION,
            builder);

        var expectedElementYSs =
            StackingUtils.STAFF_TOP_Y_SS - REGION_MARGIN_SS - REGION_A_HEIGHT_SS;
        var expectedRegionATopSs = expectedElementYSs + REGION_A_Y_OFFSET_SS;
        var expectedRegionBTopSs = expectedElementYSs + REGION_B_Y_OFFSET_SS;

        // Sample at the center of each region (well within its x-range) to confirm
        // the per-region reservations are distinct.
        var regionAXSs = ELEMENT_X_SS + REGION_A_X_OFFSET_SS + REGION_A_WIDTH_SS / 2;
        var regionBXSs = ELEMENT_X_SS + REGION_B_X_OFFSET_SS + REGION_B_WIDTH_SS / 2;
        var sampleWidthSs = 0.1;

        assertThat(extents.yGet(true, regionAXSs, sampleWidthSs))
            .isCloseTo(expectedRegionATopSs, within(TOLERANCE));
        assertThat(extents.yGet(true, regionBXSs, sampleWidthSs))
            .isCloseTo(expectedRegionBTopSs, within(TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // Row 10 — stackAbove: STRUCTURAL_HORIZONTAL_MARGIN_SS applied to query and reserve
    // -------------------------------------------------------------------------

    // X position of element for margin tests
    private static final double MARGIN_ELEM_X_SS = 5.0;
    private static final double MARGIN_ELEM_WIDTH_SS = 3.0;
    private static final double MARGIN_ELEM_HEIGHT_SS = 1.0;
    private static final double MARGIN_ELEM_MARGIN_SS = 0.25;

    // Reservation height planted in the margin zone (must be negative/above STAFF_TOP_Y_SS
    // to be picked up by yGet min; here: just above STAFF_TOP_Y_SS so it beats the anchor)
    private static final double OBSTRUCTION_TOP_SS = StackingUtils.STAFF_TOP_Y_SS - 1.0;

    @Test
    void testStackAboveQueryExpandedByMarginOnLeft() {
        // Plant an obstruction at a position that is strictly outside the element's
        // own [xSs, xSs+widthSs] but inside [xSs-MARGIN, xSs+widthSs+MARGIN].
        // The obstruction is in the LEFT margin zone only:
        //   obstructionXSs = xSs - STRUCTURAL_HORIZONTAL_MARGIN_SS / 2  (well inside margin)
        // If the query is NOT widened, yGet sees the default top[] = 0 and the anchor
        // wins (STAFF_TOP_Y_SS), giving elementYSs = STAFF_TOP_Y_SS - marginSs - heightSs.
        // If the query IS widened, yGet picks up the obstruction (OBSTRUCTION_TOP_SS <
        // STAFF_TOP_Y_SS), so ceilingSs = OBSTRUCTION_TOP_SS and:
        //   expectedElementYSs = OBSTRUCTION_TOP_SS - MARGIN_ELEM_MARGIN_SS - MARGIN_ELEM_HEIGHT_SS

        var extents = new StaffExtents(LINE_WIDTH_SS);
        var obstructionXSs = MARGIN_ELEM_X_SS - StackingUtils.STRUCTURAL_HORIZONTAL_MARGIN_SS / 2;
        var obstructionWidthSs = 0.1;
        extents.ySet(true, obstructionXSs, obstructionWidthSs, OBSTRUCTION_TOP_SS);

        var element = mock(LineElement.class);
        var builder = new LayoutResult.Builder();

        var elementYSs = StackingUtils.stackAbove(
            extents,
            element,
            MARGIN_ELEM_X_SS, MARGIN_ELEM_WIDTH_SS, MARGIN_ELEM_HEIGHT_SS, MARGIN_ELEM_MARGIN_SS,
            WITHIN_STAFF_POSITION,
            builder);

        // Oracle: obstruction in margin zone is detected → ceiling = OBSTRUCTION_TOP_SS
        var expectedElementYSs =
            OBSTRUCTION_TOP_SS - MARGIN_ELEM_MARGIN_SS - MARGIN_ELEM_HEIGHT_SS;
        assertThat(elementYSs).isCloseTo(expectedElementYSs, within(TOLERANCE));
    }

    @Test
    void testStackAboveQueryExpandedByMarginOnRight() {
        // Same logic but plant obstruction in the RIGHT margin zone:
        //   obstructionXSs = xSs + widthSs + STRUCTURAL_HORIZONTAL_MARGIN_SS / 2
        var extents = new StaffExtents(LINE_WIDTH_SS);
        var obstructionXSs = MARGIN_ELEM_X_SS + MARGIN_ELEM_WIDTH_SS
            + StackingUtils.STRUCTURAL_HORIZONTAL_MARGIN_SS / 2;
        var obstructionWidthSs = 0.1;
        extents.ySet(true, obstructionXSs, obstructionWidthSs, OBSTRUCTION_TOP_SS);

        var element = mock(LineElement.class);
        var builder = new LayoutResult.Builder();

        var elementYSs = StackingUtils.stackAbove(
            extents,
            element,
            MARGIN_ELEM_X_SS, MARGIN_ELEM_WIDTH_SS, MARGIN_ELEM_HEIGHT_SS, MARGIN_ELEM_MARGIN_SS,
            WITHIN_STAFF_POSITION,
            builder);

        var expectedElementYSs =
            OBSTRUCTION_TOP_SS - MARGIN_ELEM_MARGIN_SS - MARGIN_ELEM_HEIGHT_SS;
        assertThat(elementYSs).isCloseTo(expectedElementYSs, within(TOLERANCE));
    }

    @Test
    void testStackAboveReservesAtElementBoundsNotExpandedBounds() {
        // The reserve (ySet) uses [xSs, widthSs] — NOT the expanded margin range.
        // Verify two things:
        // (a) The center of [xSs, xSs+widthSs] carries the expected reserved top.
        // (b) A position well to the LEFT of xSs - STRUCTURAL_HORIZONTAL_MARGIN_SS
        //     (several step-widths away, to avoid shared step-bucket aliasing) still
        //     shows the default top of 0 — confirming the reservation did not extend there.
        var extents = new StaffExtents(LINE_WIDTH_SS);
        var element = mock(LineElement.class);
        var builder = new LayoutResult.Builder();

        StackingUtils.stackAbove(
            extents,
            element,
            MARGIN_ELEM_X_SS, MARGIN_ELEM_WIDTH_SS, MARGIN_ELEM_HEIGHT_SS, MARGIN_ELEM_MARGIN_SS,
            WITHIN_STAFF_POSITION,
            builder);

        // Oracle: no pre-existing obstruction → ceiling = STAFF_TOP_Y_SS
        var expectedReservedTopSs =
            StackingUtils.STAFF_TOP_Y_SS - MARGIN_ELEM_MARGIN_SS - MARGIN_ELEM_HEIGHT_SS;

        // (a) Center of the reserved range sees the reservation
        var centerXSs = MARGIN_ELEM_X_SS + MARGIN_ELEM_WIDTH_SS / 2;
        assertThat(extents.yGet(true, centerXSs, 0.1))
            .isCloseTo(expectedReservedTopSs, within(TOLERANCE));

        // (b) Well to the left — clearly outside both the reservation and the margin zone.
        // MARGIN_ELEM_X_SS=5.0, STRUCTURAL_HORIZONTAL_MARGIN_SS=0.75 → expanded left=4.25.
        // Sampling at xSs=1.0 (step 1 in a 128-step/100-ss grid) is safely out of range.
        var farLeftXSs = 1.0;
        assertThat(extents.yGet(true, farLeftXSs, 0.1)).isEqualTo(0.0);
    }

    // -------------------------------------------------------------------------
    // Row 9 — isRangeCovered: covered, uncovered, and partial/wrong-end cases
    // -------------------------------------------------------------------------

    @Test
    void testIsRangeCoveredReturnsTrueWhenExactMatchExists() {
        // An existing RangeElement with the same anchor and end identity → covered.
        var startNote = mock(StaffElement.class);
        var endNote = mock(StaffElement.class);
        var trill = new Trill(startNote, endNote);

        assertThat(StackingUtils.isRangeCovered(startNote, endNote, List.of(trill))).isTrue();
    }

    @Test
    void testIsRangeCoveredReturnsFalseWhenNoElementsExist() {
        var startNote = mock(StaffElement.class);
        var endNote = mock(StaffElement.class);

        assertThat(StackingUtils.isRangeCovered(startNote, endNote, List.of())).isFalse();
    }

    @Test
    void testIsRangeCoveredReturnsFalseWhenEndElementDiffers() {
        // Existing element covers start→wrongEnd; start→endNote is uncovered.
        var startNote = mock(StaffElement.class);
        var endNote = mock(StaffElement.class);
        var wrongEnd = mock(StaffElement.class);
        var trill = new Trill(startNote, wrongEnd);

        assertThat(StackingUtils.isRangeCovered(startNote, endNote, List.of(trill))).isFalse();
    }

    @Test
    void testIsRangeCoveredReturnsFalseWhenStartElementDiffers() {
        // Existing element covers wrongStart→endNote; startNote→endNote is uncovered.
        var startNote = mock(StaffElement.class);
        var endNote = mock(StaffElement.class);
        var wrongStart = mock(StaffElement.class);
        var trill = new Trill(wrongStart, endNote);

        assertThat(StackingUtils.isRangeCovered(startNote, endNote, List.of(trill))).isFalse();
    }

    @Test
    void testIsRangeCoveredReturnsFalseWhenOnlyUnrelatedElementsExist() {
        // Multiple existing elements, none matching the queried span.
        var startNote = mock(StaffElement.class);
        var endNote = mock(StaffElement.class);
        var otherA = mock(StaffElement.class);
        var otherB = mock(StaffElement.class);
        var trill1 = new Trill(otherA, otherB);
        var trill2 = new Trill(startNote, otherB);  // right start, wrong end

        assertThat(StackingUtils.isRangeCovered(startNote, endNote, List.of(trill1, trill2)))
            .isFalse();
    }
}
