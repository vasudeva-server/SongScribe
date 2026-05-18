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

package songscribe.ui.layout.stacking;

import java.util.List;

import songscribe.music.StaffElement;
import songscribe.smufl.SMuFLMetadata;
import songscribe.ui.layout.CollisionRegion;
import songscribe.ui.layout.LayoutResult;
import songscribe.ui.layout.LineElement;
import songscribe.ui.layout.RangeElement;
import songscribe.ui.layout.StaffExtents;

/**
 * Shared static helpers used by all stacking delegates.
 * <p>
 * Contains collision-aware placement methods ({@link #stackAbove},
 * {@link #stackAboveWithRegions}) and anchor ceiling calculations.
 */
public final class StackingUtils {

    // Note head height from SMuFL noteheadBlack bounding box (staff-space units)
    private static final double NOTE_HEAD_HEIGHT_SS = SMuFLMetadata.noteHeadHeightSs();

    static final double NOTE_HEAD_RADIUS_SS = NOTE_HEAD_HEIGHT_SS / 2.0;

    // Staff position of the top staff line (F5); positions <= this are at or above the staff
    private static final int TOP_STAFF_LINE_POSITION = -4;

    // Y coordinate of the top staff line in the middleLineY=0 coordinate system
    private static final double STAFF_TOP_Y_SS =
        TOP_STAFF_LINE_POSITION * StaffExtents.STAFF_POSITION_OFFSET_SS;

    // Horizontal collision margin for structural/system elements (collapses between adjacent elements)
    private static final double STRUCTURAL_HORIZONTAL_MARGIN_SS = 0.75; // 6px

    private StackingUtils() {
    }

    /**
     * Returns the anchor ceiling Y for a note, without consulting extents.
     * <p>
     * Notes within or below the staff anchor at the top staff line.
     * Notes at or above the top staff line anchor above the notehead.
     */
    public static double anchorCeilingSs(StaffElement note) {
        return anchorCeilingSs(note.getStaffPosition());
    }

    /**
     * Returns the anchor ceiling Y for the given staff position.
     */
    public static double anchorCeilingSs(int staffPosition) {
        if (staffPosition > TOP_STAFF_LINE_POSITION) {
            return STAFF_TOP_Y_SS;
        }

        var noteHeadYSs = staffPosition * StaffExtents.STAFF_POSITION_OFFSET_SS;
        return noteHeadYSs - NOTE_HEAD_RADIUS_SS;
    }

    /**
     * Places an element above the staff using anchored ceiling collision detection.
     * <p>
     * Uses the anchored ceiling (top staff line or notehead) as the reference point,
     * combined with existing extents reservations, to determine the highest clear Y.
     * Updates the extents and writes a {@link LayoutResult.DecorationLayout}.
     *
     * @return the computed top Y in staff-space units
     */
    public static double stackAbove(
        StaffExtents extents,
        LineElement element,
        double xSs, double widthSs, double heightSs, double marginSs,
        int staffPosition,
        LayoutResult.Builder builder) {

        // Query: expand by horizontal margin (collapses between adjacent elements)
        var queryXSs = xSs - STRUCTURAL_HORIZONTAL_MARGIN_SS;
        var queryWidthSs = widthSs + 2 * STRUCTURAL_HORIZONTAL_MARGIN_SS;
        var currentTopSs = extents.yGet(true, queryXSs, queryWidthSs);
        var anchorSs = anchorCeilingSs(staffPosition);
        var ceilingSs = Math.min(currentTopSs, anchorSs);

        // Position: bottom margin between this element's bottom and the ceiling
        var elementYSs = ceilingSs - marginSs - heightSs;

        // Reserve at element top. Upper tiers apply their own bottom margin
        // when they query, so each tier-to-tier gap = the upper element's margin.
        extents.ySet(true, xSs, widthSs, elementYSs);

        builder.putDecorationLayout(element,
            new LayoutResult.DecorationLayout(xSs, elementYSs, widthSs, heightSs, marginSs));

        return elementYSs;
    }

    /**
     * Places a composite element above the staff using sub-region collision detection.
     * <p>
     * Each sub-region independently queries the existing extents at its horizontal
     * range to find its own ceiling. The element is placed at the highest (furthest
     * from staff) position needed across all sub-regions. Each sub-region is then
     * reserved at its own visual bottom, allowing later elements to nestle into
     * the gaps between shorter and taller sub-regions.
     */
    public static void stackAboveWithRegions(
        StaffExtents extents,
        LineElement element,
        List<CollisionRegion> regions,
        double xSs, double widthSs, double marginSs,
        int staffPosition,
        LayoutResult.Builder builder
    ) {
        var anchorSs = anchorCeilingSs(staffPosition);
        var elementYSs = Double.MAX_VALUE;

        // Query phase: each sub-region finds its own ceiling independently.
        // The element Y is the min (highest on page) across all sub-regions,
        // so the element clears all content beneath every sub-region.
        for (var region : regions) {
            var regionXSs = xSs + region.xOffsetSs();
            var queryXSs = regionXSs - STRUCTURAL_HORIZONTAL_MARGIN_SS;
            var queryWidthSs = region.widthSs() + 2 * STRUCTURAL_HORIZONTAL_MARGIN_SS;

            var regionTopSs = extents.yGet(true, queryXSs, queryWidthSs);
            var regionCeilingSs = Math.min(regionTopSs, anchorSs);

            // Constraint: elementY + yOffset + height <= ceiling - margin
            var regionYSs = regionCeilingSs - marginSs
                - region.yOffsetSs() - region.heightSs();

            elementYSs = Math.min(elementYSs, regionYSs);
        }

        // Set phase: reserve each sub-region at its visual top.
        // Shorter sub-regions (e.g. text) have a higher yOffset → shallower reservation,
        // enabling later elements to nestle closer where only the short sub-region exists.
        for (var region : regions) {
            var regionXSs = xSs + region.xOffsetSs();
            var regionTopSs = elementYSs + region.yOffsetSs();
            extents.ySet(true, regionXSs, region.widthSs(), regionTopSs);
        }

        // Overall height is the max extent across all sub-regions
        double overallHeightSs = 0;

        for (var region : regions) {
            overallHeightSs = Math.max(
                overallHeightSs, region.yOffsetSs() + region.heightSs());
        }

        builder.putDecorationLayout(element,
            new LayoutResult.DecorationLayout(
                xSs, elementYSs, widthSs, overallHeightSs, marginSs, regions));

    }

    /**
     * Checks whether a range span is already covered by an existing range element.
     */
    public static boolean isRangeCovered(
        StaffElement startNote,
        StaffElement endNote,
        List<? extends RangeElement> existingElements) {

        for (var element : existingElements) {
            if (element.getAnchorElement() == startNote && element.getEndElement() == endNote) {
                return true;
            }
        }

        return false;
    }

}
