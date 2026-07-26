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
import songscribe.dom.StaffElement.Direction;
import songscribe.dom.Trill;
import songscribe.layout.LayoutResult;
import songscribe.layout.StaffExtents;
import songscribe.engraving.Staff;
import songscribe.engraving.LineThickness;

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

    // A staff position at the bottom staff line (boundary — still uses floor formula)
    private static final int AT_BOTTOM_STAFF_LINE_POSITION = StackingUtils.BOTTOM_STAFF_LINE_POSITION;

    // A staff position one unit below the bottom staff line (strictly below — uses floor formula)
    private static final int BELOW_BOTTOM_STAFF_LINE_POSITION = StackingUtils.BOTTOM_STAFF_LINE_POSITION + 2;

    // An interior staff line (even position, not the top/bottom line) — staccatoAnchor*
    // uses STACCATO_ON_LINE_DISTANCE_SS here instead of the fixed staff-line anchor.
    private static final int INTERIOR_LINE_STAFF_POSITION = WITHIN_STAFF_POSITION;

    // A space between staff lines (odd position) — staccatoAnchor* uses
    // STACCATO_BETWEEN_LINES_DISTANCE_SS here.
    private static final int INTERIOR_SPACE_STAFF_POSITION = -1;

    // The outer space, one position inside the top/bottom staff line — still a space, so
    // staccatoAnchor* uses STACCATO_BETWEEN_LINES_DISTANCE_SS, not the fixed staff-line anchor.
    private static final int OUTER_SPACE_ABOVE_STAFF_POSITION = StackingUtils.TOP_STAFF_LINE_POSITION + 1;
    private static final int OUTER_SPACE_BELOW_STAFF_POSITION = StackingUtils.BOTTOM_STAFF_LINE_POSITION - 1;

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
        var expectedNoteHeadYSs = sp * Staff.STAFF_POSITION_OFFSET_SS;
        var expectedCeilingSs = expectedNoteHeadYSs - StackingUtils.NOTE_HEAD_RADIUS_SS;

        var result = StackingUtils.anchorCeilingSs(sp);
        assertThat(result).isCloseTo(expectedCeilingSs, within(TOLERANCE));
    }

    @Test
    void testAnchorCeilingAboveTopStaffLineUsesNoteHeadFormula() {
        // sp < TOP_STAFF_LINE_POSITION: well above the staff — same formula.
        var sp = ABOVE_TOP_STAFF_LINE_POSITION;
        var expectedNoteHeadYSs = sp * Staff.STAFF_POSITION_OFFSET_SS;
        var expectedCeilingSs = expectedNoteHeadYSs - StackingUtils.NOTE_HEAD_RADIUS_SS;

        var result = StackingUtils.anchorCeilingSs(sp);
        assertThat(result).isCloseTo(expectedCeilingSs, within(TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // Ink-edge constants: STAFF_TOP_INK_Y_SS / STAFF_BOT_INK_Y_SS pad to the outer edge of the
    // outer staff line's ink, half a staff-line thickness beyond the centerlines.
    // -------------------------------------------------------------------------

    @Test
    void testStaffInkEdgesLieHalfALineThicknessBeyondTheCenterlines() {
        // Pin the constants' concrete values, not just their symbols: the top ink edge sits above
        // its centerline (more negative) and the bottom ink edge below its centerline (more
        // positive), each by half the staff line's thickness. A wrong-sign definition padding
        // inward would move production and every clamp test together and stay green — this is the
        // one assertion that catches that.
        var halfThicknessSs = LineThickness.STAFF_LINE_SS / 2.0;

        assertThat(StackingUtils.STAFF_TOP_INK_Y_SS)
            .describedAs("top ink edge sits half a line thickness above the top centerline")
            .isCloseTo(StackingUtils.STAFF_TOP_Y_SS - halfThicknessSs, within(TOLERANCE));
        assertThat(StackingUtils.STAFF_BOT_INK_Y_SS)
            .describedAs("bottom ink edge sits half a line thickness below the bottom centerline")
            .isCloseTo(StackingUtils.STAFF_BOT_Y_SS + halfThicknessSs, within(TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // anchorFloorSs(sp): within or above staff → STAFF_BOT_Y_SS
    // -------------------------------------------------------------------------

    @Test
    void testAnchorFloorWithinStaffReturnsStaffBotY() {
        // sp < BOTTOM_STAFF_LINE_POSITION: the note is within or above the staff,
        // so the anchor floor is pinned to the bottom staff line.
        var result = StackingUtils.anchorFloorSs(WITHIN_STAFF_POSITION);
        assertThat(result).isEqualTo(StackingUtils.STAFF_BOT_Y_SS);
    }

    @Test
    void testAnchorFloorAboveTopLineReturnsStaffBotYForNegativePosition() {
        // Any negative sp (above middle line) should also return STAFF_BOT_Y_SS.
        var aboveMiddlePosition = -4;
        var result = StackingUtils.anchorFloorSs(aboveMiddlePosition);
        assertThat(result).isEqualTo(StackingUtils.STAFF_BOT_Y_SS);
    }

    // -------------------------------------------------------------------------
    // anchorFloorSs(sp): at or below bottom staff line → formula result
    // -------------------------------------------------------------------------

    @Test
    void testAnchorFloorAtBottomStaffLineUsesNoteHeadFormula() {
        // sp == BOTTOM_STAFF_LINE_POSITION: boundary that does NOT use STAFF_BOT_Y_SS;
        // result is noteHeadY + NOTE_HEAD_RADIUS_SS.
        var sp = AT_BOTTOM_STAFF_LINE_POSITION;
        var expectedNoteHeadYSs = sp * Staff.STAFF_POSITION_OFFSET_SS;
        var expectedFloorSs = expectedNoteHeadYSs + StackingUtils.NOTE_HEAD_RADIUS_SS;

        var result = StackingUtils.anchorFloorSs(sp);
        assertThat(result).isCloseTo(expectedFloorSs, within(TOLERANCE));
    }

    @Test
    void testAnchorFloorBelowBottomStaffLineUsesNoteHeadFormula() {
        // sp > BOTTOM_STAFF_LINE_POSITION: well below the staff — same formula.
        var sp = BELOW_BOTTOM_STAFF_LINE_POSITION;
        var expectedNoteHeadYSs = sp * Staff.STAFF_POSITION_OFFSET_SS;
        var expectedFloorSs = expectedNoteHeadYSs + StackingUtils.NOTE_HEAD_RADIUS_SS;

        var result = StackingUtils.anchorFloorSs(sp);
        assertThat(result).isCloseTo(expectedFloorSs, within(TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // staccatoAnchorCeilingSs(sp) / staccatoAnchorFloorSs(sp)
    // -------------------------------------------------------------------------

    @Test
    void testStaccatoAnchorCeilingOnInteriorLineUsesOnLineDistance() {
        var sp = INTERIOR_LINE_STAFF_POSITION;
        var noteHeadYSs = sp * Staff.STAFF_POSITION_OFFSET_SS;
        var expectedCeilingSs = noteHeadYSs - StackingUtils.STACCATO_ON_LINE_DISTANCE_SS;

        var result = StackingUtils.staccatoAnchorCeilingSs(sp);
        assertThat(result).isCloseTo(expectedCeilingSs, within(TOLERANCE));
    }

    @Test
    void testStaccatoAnchorCeilingInSpaceUsesBetweenLinesDistance() {
        var sp = INTERIOR_SPACE_STAFF_POSITION;
        var noteHeadYSs = sp * Staff.STAFF_POSITION_OFFSET_SS;
        var expectedCeilingSs = noteHeadYSs - StackingUtils.STACCATO_BETWEEN_LINES_DISTANCE_SS;

        var result = StackingUtils.staccatoAnchorCeilingSs(sp);
        assertThat(result).isCloseTo(expectedCeilingSs, within(TOLERANCE));
    }

    @Test
    void testStaccatoAnchorCeilingInOuterSpaceUsesBetweenLinesDistance() {
        // The outer space is still a space, not the staff line itself, so it does not
        // fall back to the fixed staff-line anchor.
        var sp = OUTER_SPACE_ABOVE_STAFF_POSITION;
        var noteHeadYSs = sp * Staff.STAFF_POSITION_OFFSET_SS;
        var expectedCeilingSs = noteHeadYSs - StackingUtils.STACCATO_BETWEEN_LINES_DISTANCE_SS;

        var result = StackingUtils.staccatoAnchorCeilingSs(sp);
        assertThat(result).isCloseTo(expectedCeilingSs, within(TOLERANCE));
    }

    @Test
    void testStaccatoAnchorCeilingAtTopStaffLineDelegatesToAnchorCeiling() {
        var sp = AT_TOP_STAFF_LINE_POSITION;
        var result = StackingUtils.staccatoAnchorCeilingSs(sp);
        assertThat(result).isCloseTo(StackingUtils.anchorCeilingSs(sp), within(TOLERANCE));
    }

    @Test
    void testStaccatoAnchorCeilingAboveTopStaffLineDelegatesToAnchorCeiling() {
        var sp = ABOVE_TOP_STAFF_LINE_POSITION;
        var result = StackingUtils.staccatoAnchorCeilingSs(sp);
        assertThat(result).isCloseTo(StackingUtils.anchorCeilingSs(sp), within(TOLERANCE));
    }

    @Test
    void testStaccatoAnchorFloorOnInteriorLineUsesOnLineDistance() {
        var sp = INTERIOR_LINE_STAFF_POSITION;
        var noteHeadYSs = sp * Staff.STAFF_POSITION_OFFSET_SS;
        var expectedFloorSs = noteHeadYSs + StackingUtils.STACCATO_ON_LINE_DISTANCE_SS;

        var result = StackingUtils.staccatoAnchorFloorSs(sp);
        assertThat(result).isCloseTo(expectedFloorSs, within(TOLERANCE));
    }

    @Test
    void testStaccatoAnchorFloorInSpaceUsesBetweenLinesDistance() {
        var sp = INTERIOR_SPACE_STAFF_POSITION;
        var noteHeadYSs = sp * Staff.STAFF_POSITION_OFFSET_SS;
        var expectedFloorSs = noteHeadYSs + StackingUtils.STACCATO_BETWEEN_LINES_DISTANCE_SS;

        var result = StackingUtils.staccatoAnchorFloorSs(sp);
        assertThat(result).isCloseTo(expectedFloorSs, within(TOLERANCE));
    }

    @Test
    void testStaccatoAnchorFloorInOuterSpaceUsesBetweenLinesDistance() {
        var sp = OUTER_SPACE_BELOW_STAFF_POSITION;
        var noteHeadYSs = sp * Staff.STAFF_POSITION_OFFSET_SS;
        var expectedFloorSs = noteHeadYSs + StackingUtils.STACCATO_BETWEEN_LINES_DISTANCE_SS;

        var result = StackingUtils.staccatoAnchorFloorSs(sp);
        assertThat(result).isCloseTo(expectedFloorSs, within(TOLERANCE));
    }

    @Test
    void testStaccatoAnchorFloorAtBottomStaffLineDelegatesToAnchorFloor() {
        var sp = AT_BOTTOM_STAFF_LINE_POSITION;
        var result = StackingUtils.staccatoAnchorFloorSs(sp);
        assertThat(result).isCloseTo(StackingUtils.anchorFloorSs(sp), within(TOLERANCE));
    }

    @Test
    void testStaccatoAnchorFloorBelowBottomStaffLineDelegatesToAnchorFloor() {
        var sp = BELOW_BOTTOM_STAFF_LINE_POSITION;
        var result = StackingUtils.staccatoAnchorFloorSs(sp);
        assertThat(result).isCloseTo(StackingUtils.anchorFloorSs(sp), within(TOLERANCE));
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
    // placeAndReserve: shared placement core used by stackAtAnchor/stackAtCenter. Reserve-edge/
    // return convention.
    // -------------------------------------------------------------------------

    private static final double PLACE_AND_RESERVE_X_SS = 5.0;
    private static final double PLACE_AND_RESERVE_WIDTH_SS = 1.0;
    private static final double PLACE_AND_RESERVE_HEIGHT_SS = 1.5;
    private static final double PLACE_AND_RESERVE_BOUND_SS = -2.0;
    private static final double PLACE_AND_RESERVE_MARGIN_SS = 0.3;

    @Test
    void testPlaceAndReserveAboveReturnsTopEdgeAndReservesIt() {
        var extents = new StaffExtents(LINE_WIDTH_SS);
        var element = mock(LineElement.class);
        var builder = new LayoutResult.Builder();

        var returnedYSs = StackingUtils.placeAndReserve(Direction.UP, extents, element,
            PLACE_AND_RESERVE_X_SS, PLACE_AND_RESERVE_WIDTH_SS, PLACE_AND_RESERVE_HEIGHT_SS,
            PLACE_AND_RESERVE_BOUND_SS, PLACE_AND_RESERVE_MARGIN_SS, builder);

        // Above: element top = bound - margin - height; the return value is that same top edge.
        var expectedTopYSs = PLACE_AND_RESERVE_BOUND_SS - PLACE_AND_RESERVE_MARGIN_SS
            - PLACE_AND_RESERVE_HEIGHT_SS;
        assertThat(returnedYSs).isCloseTo(expectedTopYSs, within(TOLERANCE));

        var layout = require(builder.build().getDecorationLayout(element), "decoration layout");
        assertThat(layout.ySs()).isCloseTo(expectedTopYSs, within(TOLERANCE));

        // Reserved at the top (outer) edge.
        assertThat(extents.yGet(true, PLACE_AND_RESERVE_X_SS, PLACE_AND_RESERVE_WIDTH_SS))
            .isCloseTo(expectedTopYSs, within(TOLERANCE));
    }

    @Test
    void testPlaceAndReserveBelowReturnsBottomEdgeAndReservesIt() {
        var extents = new StaffExtents(LINE_WIDTH_SS);
        var element = mock(LineElement.class);
        var builder = new LayoutResult.Builder();

        var boundSs = -PLACE_AND_RESERVE_BOUND_SS; // mirror to a positive (below-staff) bound
        var returnedYSs = StackingUtils.placeAndReserve(Direction.DOWN, extents, element,
            PLACE_AND_RESERVE_X_SS, PLACE_AND_RESERVE_WIDTH_SS, PLACE_AND_RESERVE_HEIGHT_SS,
            boundSs, PLACE_AND_RESERVE_MARGIN_SS, builder);

        // Below: element top = bound + margin; the return value is the bottom (outer) edge.
        var expectedTopYSs = boundSs + PLACE_AND_RESERVE_MARGIN_SS;
        var expectedBottomYSs = expectedTopYSs + PLACE_AND_RESERVE_HEIGHT_SS;
        assertThat(returnedYSs).isCloseTo(expectedBottomYSs, within(TOLERANCE));

        var layout = require(builder.build().getDecorationLayout(element), "decoration layout");
        assertThat(layout.ySs()).isCloseTo(expectedTopYSs, within(TOLERANCE));

        assertThat(extents.yGet(false, PLACE_AND_RESERVE_X_SS, PLACE_AND_RESERVE_WIDTH_SS))
            .isCloseTo(expectedBottomYSs, within(TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // placeAndReserveClamped: LilyPond's aligned_side — max(realEdge + padding, staffEdge +
    // staffPadding) outward. Covers both the support-driven case and the staff-padding clamp.
    // -------------------------------------------------------------------------

    private static final double CLAMPED_X_SS = 5.0;
    private static final double CLAMPED_WIDTH_SS = 1.0;
    private static final double CLAMPED_HEIGHT_SS = 1.5;
    private static final double CLAMPED_PADDING_SS = 0.20;
    private static final double CLAMPED_STAFF_PADDING_SS = 0.25;

    // A real reservation far enough outward that it — not the staff clamp — wins the max.
    private static final double CLAMPED_SUPPORT_ABOVE_SS = -5.0;
    private static final double CLAMPED_SUPPORT_BELOW_SS = 5.0;

    @Test
    void testPlaceAndReserveClampedIsolatedElementClampsToStaffPadding() {
        // No real reservation under the footprint: only the staff clamp applies, so the isolated
        // articulation clears the staff line by exactly staffPaddingSs.
        var extents = new StaffExtents(LINE_WIDTH_SS);
        var element = mock(LineElement.class);
        var builder = new LayoutResult.Builder();

        var returnedYSs = StackingUtils.placeAndReserveClamped(Direction.UP, extents, element,
            CLAMPED_X_SS, CLAMPED_WIDTH_SS, CLAMPED_HEIGHT_SS,
            StaffExtents.Profiles.flat(CLAMPED_WIDTH_SS),
            CLAMPED_PADDING_SS, CLAMPED_STAFF_PADDING_SS,
            StackingUtils.SCRIPT_HORIZON_PADDING_SS, builder);

        var expectedTopYSs =
            StackingUtils.STAFF_TOP_INK_Y_SS - CLAMPED_STAFF_PADDING_SS - CLAMPED_HEIGHT_SS;
        assertThat(returnedYSs).isCloseTo(expectedTopYSs, within(TOLERANCE));

        var clearanceFromStaffSs = StackingUtils.STAFF_TOP_INK_Y_SS - returnedYSs;
        assertThat(clearanceFromStaffSs)
            .describedAs("isolated articulation must clear the staff by staffPaddingSs + height")
            .isCloseTo(CLAMPED_STAFF_PADDING_SS + CLAMPED_HEIGHT_SS, within(TOLERANCE));
    }

    @Test
    void testPlaceAndReserveClampedRealSupportOverridesStaffPaddingClamp() {
        // A real reservation more outward than the staff clamp wins: the element clears the real
        // support's outer edge by paddingSs, edge-to-edge — not the staff clamp.
        var extents = new StaffExtents(LINE_WIDTH_SS);
        extents.ySet(true, CLAMPED_X_SS, CLAMPED_WIDTH_SS, CLAMPED_SUPPORT_ABOVE_SS);
        var element = mock(LineElement.class);
        var builder = new LayoutResult.Builder();

        var returnedYSs = StackingUtils.placeAndReserveClamped(Direction.UP, extents, element,
            CLAMPED_X_SS, CLAMPED_WIDTH_SS, CLAMPED_HEIGHT_SS,
            StaffExtents.Profiles.flat(CLAMPED_WIDTH_SS),
            CLAMPED_PADDING_SS, CLAMPED_STAFF_PADDING_SS,
            StackingUtils.SCRIPT_HORIZON_PADDING_SS, builder);

        var expectedTopYSs = CLAMPED_SUPPORT_ABOVE_SS - CLAMPED_PADDING_SS - CLAMPED_HEIGHT_SS;
        assertThat(returnedYSs).isCloseTo(expectedTopYSs, within(TOLERANCE));
    }

    @Test
    void testPlaceAndReserveClampedBelowIsolatedElementClampsToStaffPadding() {
        var extents = new StaffExtents(LINE_WIDTH_SS);
        var element = mock(LineElement.class);
        var builder = new LayoutResult.Builder();

        var returnedYSs = StackingUtils.placeAndReserveClamped(Direction.DOWN, extents, element,
            CLAMPED_X_SS, CLAMPED_WIDTH_SS, CLAMPED_HEIGHT_SS,
            StaffExtents.Profiles.flat(CLAMPED_WIDTH_SS),
            CLAMPED_PADDING_SS, CLAMPED_STAFF_PADDING_SS,
            StackingUtils.SCRIPT_HORIZON_PADDING_SS, builder);

        var expectedBottomYSs =
            StackingUtils.STAFF_BOT_INK_Y_SS + CLAMPED_STAFF_PADDING_SS + CLAMPED_HEIGHT_SS;
        assertThat(returnedYSs).isCloseTo(expectedBottomYSs, within(TOLERANCE));
    }

    @Test
    void testPlaceAndReserveClampedBelowRealSupportOverridesStaffPaddingClamp() {
        var extents = new StaffExtents(LINE_WIDTH_SS);
        extents.ySet(false, CLAMPED_X_SS, CLAMPED_WIDTH_SS, CLAMPED_SUPPORT_BELOW_SS);
        var element = mock(LineElement.class);
        var builder = new LayoutResult.Builder();

        var returnedYSs = StackingUtils.placeAndReserveClamped(Direction.DOWN, extents, element,
            CLAMPED_X_SS, CLAMPED_WIDTH_SS, CLAMPED_HEIGHT_SS,
            StaffExtents.Profiles.flat(CLAMPED_WIDTH_SS),
            CLAMPED_PADDING_SS, CLAMPED_STAFF_PADDING_SS,
            StackingUtils.SCRIPT_HORIZON_PADDING_SS, builder);

        var expectedBottomYSs =
            CLAMPED_SUPPORT_BELOW_SS + CLAMPED_PADDING_SS + CLAMPED_HEIGHT_SS;
        assertThat(returnedYSs).isCloseTo(expectedBottomYSs, within(TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // stackStaccato: quantize-position — a collision-pushed dot center landing exactly on a staff
    // line, within the quantize zone, is snapped one further half-space outward to a space.
    // -------------------------------------------------------------------------

    private static final double QUANTIZE_X_SS = 5.0;
    private static final double QUANTIZE_WIDTH_SS = 1.0;
    private static final double QUANTIZE_HEIGHT_SS = 1.0;
    private static final double QUANTIZE_MARGIN_SS = 0.3;
    // Unused in this test: staffPosition = 0 is well within the staff, so the edge-clamp branch
    // (the only branch that consults staffPaddingSs) never runs.
    private static final double QUANTIZE_STAFF_PADDING_SS = 0.25;
    private static final int QUANTIZE_STAFF_POSITION = 0;

    // With staffPosition = 0 (the middle line), staccatoAnchorCeilingSs(0) = -1.5 (a space), so the
    // uncollided ideal never needs quantizing. A real reservation this far out forces the collision
    // branch to win, pushing the pre-quantize center to exactly -2.0 ss — the top staff line —
    // which is inside StackingUtils.STACCATO_QUANTIZE_ZONE_SS (2.5 ss).
    private static final double QUANTIZE_COLLISION_SUPPORT_SS = -1.2;

    @Test
    void testStackStaccatoQuantizesCollisionPushedCenterOffStaffLine() {
        var extents = new StaffExtents(LINE_WIDTH_SS);
        extents.ySet(true, QUANTIZE_X_SS, QUANTIZE_WIDTH_SS, QUANTIZE_COLLISION_SUPPORT_SS);
        var element = mock(LineElement.class);
        var builder = new LayoutResult.Builder();

        // A flat reservation profile: this asserts where the dot is placed, not what it reserves.
        var returnedYSs = StackingUtils.stackStaccato(Direction.UP, extents, element,
            QUANTIZE_X_SS, QUANTIZE_WIDTH_SS, QUANTIZE_HEIGHT_SS,
            StaffExtents.Profile.flat(QUANTIZE_WIDTH_SS), QUANTIZE_MARGIN_SS,
            QUANTIZE_STAFF_PADDING_SS, StackingUtils.SCRIPT_HORIZON_PADDING_SS,
            QUANTIZE_STAFF_POSITION, builder);

        // Pre-quantize center would land at -2.0 (a staff line); quantized, it snaps one further
        // half-space outward to -2.5 (a space), so the dot's top edge is at -2.5 - height/2 = -3.0.
        var expectedQuantizedCenterSs = -2.5;
        var expectedTopYSs = expectedQuantizedCenterSs - QUANTIZE_HEIGHT_SS / 2.0;
        assertThat(returnedYSs)
            .describedAs("collision-pushed center on a staff line snaps outward to the next space")
            .isCloseTo(expectedTopYSs, within(TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // stackStaccato over a *sloped* support (a tie arc's chord): the horizon padding must dilate the
    // reservation, not widen the query. The two agree exactly against every flat support, so this is
    // the only shape of case that can tell them apart.
    // -------------------------------------------------------------------------

    private static final double SLOPED_X_SS = 10.0;
    private static final double SLOPED_WIDTH_SS = 1.0;
    private static final double SLOPED_HEIGHT_SS = 1.0;
    private static final double SLOPED_MARGIN_SS = 0.3;
    private static final double SLOPED_STAFF_PADDING_SS = 0.25;

    // A space strictly inside the staff, so stackStaccato takes its center-anchored branch (the only
    // one that ever consulted yGetExpanded) rather than the staff-edge clamp.
    private static final int SLOPED_STAFF_POSITION = -1;

    // The chord begins just right of the dot's footprint — inside SCRIPT_HORIZON_PADDING_SS, so both
    // semantics see it — and climbs steeply away from the staff as it recedes. That is a tie arc's
    // shape near its endpoint, where its slope is greatest.
    private static final double SLOPED_CHORD_GAP_SS = 0.05;
    private static final double SLOPED_CHORD_WIDTH_SS = 0.95;
    private static final double SLOPED_CHORD_NEAR_Y_SS = -3.0;
    private static final double SLOPED_CHORD_FAR_Y_SS = -4.0;

    private static double slopedChordStartXSs() {
        return SLOPED_X_SS + SLOPED_WIDTH_SS + SLOPED_CHORD_GAP_SS;
    }

    private static double stackStaccatoOverSlopedChord() {
        var extents = new StaffExtents(LINE_WIDTH_SS);
        extents.ySetSloped(true, slopedChordStartXSs(),
            slopedChordStartXSs() + SLOPED_CHORD_WIDTH_SS,
            SLOPED_CHORD_NEAR_Y_SS, SLOPED_CHORD_FAR_Y_SS);

        return StackingUtils.stackStaccato(Direction.UP, extents, mock(LineElement.class),
            SLOPED_X_SS, SLOPED_WIDTH_SS, SLOPED_HEIGHT_SS,
            StaffExtents.Profile.flat(SLOPED_WIDTH_SS), SLOPED_MARGIN_SS,
            SLOPED_STAFF_PADDING_SS, StackingUtils.SCRIPT_HORIZON_PADDING_SS,
            SLOPED_STAFF_POSITION, new LayoutResult.Builder());
    }

    /** The dot's top Y for a given support edge, once margin, centering and quantization are applied. */
    private static double dotTopYForSupportSs(double supportYSs) {
        var centerYSs = supportYSs - SLOPED_MARGIN_SS - SLOPED_HEIGHT_SS / 2.0;

        // The pushed center lands well beyond STACCATO_QUANTIZE_ZONE_SS (2.5 ss), so it is not
        // snapped and the raw sub-tenth-of-a-staff-space difference survives into the placement.
        // Inside the zone, quantization would round both semantics to the same space and hide it.
        assertThat(Math.abs(centerYSs)).isGreaterThan(StackingUtils.STACCATO_QUANTIZE_ZONE_SS);

        return centerYSs - SLOPED_HEIGHT_SS / 2.0;
    }

    @Test
    void testStaccatoOverASlopedSupportSeesTheChordsEndpointNotItsInterior() {
        // LilyPond's Skyline::padded extends each building flat by the horizon padding, holding its
        // endpoint height. The chord starts right of the dot, so over the dot's whole footprint the
        // dilated chord sits at its near endpoint.
        var expectedTopYSs = dotTopYForSupportSs(SLOPED_CHORD_NEAR_Y_SS);

        assertThat(stackStaccatoOverSlopedChord())
            .describedAs("the dot must rest on the dilated chord's endpoint height")
            .isCloseTo(expectedTopYSs, within(TOLERANCE));
    }

    @Test
    void testWideningTheQueryWouldSeatTheStaccatoFurtherOut() {
        // What the old yGetExpanded did: read the chord's *interior*, out to one horizon padding
        // beyond the dot's own footprint, where the chord has already climbed away from the staff.
        var chordSlopeSs =
            (SLOPED_CHORD_FAR_Y_SS - SLOPED_CHORD_NEAR_Y_SS) / SLOPED_CHORD_WIDTH_SS;
        var queryEdgeXSs = SLOPED_X_SS + SLOPED_WIDTH_SS + StackingUtils.SCRIPT_HORIZON_PADDING_SS;
        var interiorYSs = SLOPED_CHORD_NEAR_Y_SS
            + (queryEdgeXSs - slopedChordStartXSs()) * chordSlopeSs;

        // The two really do differ here — otherwise this test proves nothing about the one above.
        assertThat(interiorYSs).isLessThan(SLOPED_CHORD_NEAR_Y_SS);

        // Query-widening is the more conservative of the two: it pushes the dot further from the
        // staff. Migrating to clearance lets the dot sit closer, bounded by |chordSlope| x horizon.
        var widenedTopYSs = dotTopYForSupportSs(interiorYSs);
        var placedTopYSs = stackStaccatoOverSlopedChord();

        assertThat(placedTopYSs)
            .describedAs("the dot must NOT be pushed out by the chord's interior")
            .isGreaterThan(widenedTopYSs);

        assertThat(placedTopYSs - widenedTopYSs)
            .describedAs("and the gap is bounded by the chord's slope across the horizon")
            .isLessThanOrEqualTo(
                Math.abs(chordSlopeSs) * StackingUtils.SCRIPT_HORIZON_PADDING_SS + TOLERANCE);
    }

    @Test
    void testStaccatoWithNoSupportSitsAtItsIdealAnchor() {
        // `clearance` reports absence explicitly. `yGet` used to report it as 0.0 — the middle staff
        // line — which only ever behaved because a dot's ideal anchor is further out than its margin.
        var returnedYSs = StackingUtils.stackStaccato(Direction.UP,
            new StaffExtents(LINE_WIDTH_SS), mock(LineElement.class),
            SLOPED_X_SS, SLOPED_WIDTH_SS, SLOPED_HEIGHT_SS,
            StaffExtents.Profile.flat(SLOPED_WIDTH_SS), SLOPED_MARGIN_SS,
            SLOPED_STAFF_PADDING_SS, StackingUtils.SCRIPT_HORIZON_PADDING_SS,
            SLOPED_STAFF_POSITION, new LayoutResult.Builder());

        var idealCenterSs = StackingUtils.staccatoAnchorCeilingSs(SLOPED_STAFF_POSITION);

        assertThat(returnedYSs)
            .describedAs("an unsupported dot sits exactly at its note-relative anchor")
            .isCloseTo(idealCenterSs - SLOPED_HEIGHT_SS / 2.0, within(TOLERANCE));
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
