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

import java.util.List;

import songscribe.dom.StaffElement;
import songscribe.dom.StaffElement.Direction;
import songscribe.smufl.SMuFLMetadata;
import songscribe.dom.CollisionRegion;
import songscribe.layout.LayoutResult;
import songscribe.dom.LineElement;
import songscribe.dom.RangeElement;
import songscribe.layout.StaffExtents;
import songscribe.engraving.Staff;

/**
 * Shared static helpers used by all stacking delegates.
 * <p>
 * Contains collision-aware placement methods ({@link #stackAbove}, {@link #stackBelow},
 * {@link #stackStaccato}, {@link #stackBeyond}, {@link #stackAboveWithRegions}) and anchor
 * ceiling/floor calculations. The above/below variants share their implementation, dispatched
 * on {@link Direction}.
 */
public final class StackingUtils {

    // Note head height from SMuFL noteheadBlack bounding box (staff-space units)
    private static final double NOTE_HEAD_HEIGHT_SS = SMuFLMetadata.noteHeadHeightSs();

    static final double NOTE_HEAD_RADIUS_SS = NOTE_HEAD_HEIGHT_SS / 2.0;

    // Staff position of the top staff line (F5); positions <= this are at or above the staff
    static final int TOP_STAFF_LINE_POSITION = -4;

    // Y coordinate of the top staff line in the middleLineY=0 coordinate system
    static final double STAFF_TOP_Y_SS =
        TOP_STAFF_LINE_POSITION * Staff.STAFF_POSITION_OFFSET_SS;

    // Staff position of the bottom staff line (E4); positions >= this are at or below the staff
    static final int BOTTOM_STAFF_LINE_POSITION = 4;

    // Y coordinate of the bottom staff line in the middleLineY=0 coordinate system
    static final double STAFF_BOT_Y_SS =
        BOTTOM_STAFF_LINE_POSITION * Staff.STAFF_POSITION_OFFSET_SS;

    // Horizontal collision margin for structural/system elements (collapses between adjacent elements)
    static final double STRUCTURAL_HORIZONTAL_MARGIN_SS = 0.75; // 6px

    // Distance from the note center to the staccato dot when the note sits on an interior
    // staff line (a line other than the top or bottom one) — clears the line itself.
    static final double STACCATO_ON_LINE_DISTANCE_SS = 1.5;

    // Distance from the note center to the staccato dot when the note sits in a space
    // between staff lines (including the top and bottom spaces).
    static final double STACCATO_BETWEEN_LINES_DISTANCE_SS = 1.0;

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

        var noteHeadYSs = staffPosition * Staff.STAFF_POSITION_OFFSET_SS;
        return noteHeadYSs - NOTE_HEAD_RADIUS_SS;
    }

    /**
     * Returns the anchor floor Y for the given staff position.
     */
    public static double anchorFloorSs(int staffPosition) {
        if (staffPosition < BOTTOM_STAFF_LINE_POSITION) {
            return STAFF_BOT_Y_SS;
        }

        var noteHeadYSs = staffPosition * Staff.STAFF_POSITION_OFFSET_SS;
        return noteHeadYSs + NOTE_HEAD_RADIUS_SS;
    }

    /**
     * Returns the staccato anchor ceiling Y for the given staff position.
     * <p>
     * Unlike {@link #anchorCeilingSs}, notes within the staff do not anchor at a fixed
     * staff line: a note on an interior staff line anchors {@link #STACCATO_ON_LINE_DISTANCE_SS}
     * from the note center (clearing the line), while a note in a space (including the top
     * space) anchors {@link #STACCATO_BETWEEN_LINES_DISTANCE_SS} from the note center. Notes
     * on or above the top staff line anchor the same as {@link #anchorCeilingSs}.
     */
    public static double staccatoAnchorCeilingSs(int staffPosition) {
        if (staffPosition <= TOP_STAFF_LINE_POSITION) {
            return anchorCeilingSs(staffPosition);
        }

        var noteHeadYSs = staffPosition * Staff.STAFF_POSITION_OFFSET_SS;
        var distanceSs = StaffElement.isLinePosition(staffPosition)
            ? STACCATO_ON_LINE_DISTANCE_SS
            : STACCATO_BETWEEN_LINES_DISTANCE_SS;

        return noteHeadYSs - distanceSs;
    }

    /**
     * Returns the staccato anchor floor Y for the given staff position.
     * <p>
     * Mirrors {@link #staccatoAnchorCeilingSs} for below-staff placement.
     */
    public static double staccatoAnchorFloorSs(int staffPosition) {
        if (staffPosition >= BOTTOM_STAFF_LINE_POSITION) {
            return anchorFloorSs(staffPosition);
        }

        var noteHeadYSs = staffPosition * Staff.STAFF_POSITION_OFFSET_SS;
        var distanceSs = StaffElement.isLinePosition(staffPosition)
            ? STACCATO_ON_LINE_DISTANCE_SS
            : STACCATO_BETWEEN_LINES_DISTANCE_SS;

        return noteHeadYSs + distanceSs;
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

        return stackAtAnchor(Direction.UP, extents, element, xSs, widthSs, heightSs, marginSs,
            staffPosition, builder);
    }

    /**
     * Places an element below the staff using anchored floor collision detection.
     * <p>
     * Uses the anchored floor (bottom staff line or notehead) as the reference point,
     * combined with existing extents reservations, to determine the lowest clear Y.
     * Updates the extents and writes a {@link LayoutResult.DecorationLayout}.
     *
     * @return the computed bottom Y in staff-space units
     */
    public static double stackBelow(
        StaffExtents extents,
        LineElement element,
        double xSs, double widthSs, double heightSs, double marginSs,
        int staffPosition,
        LayoutResult.Builder builder) {

        return stackAtAnchor(Direction.DOWN, extents, element, xSs, widthSs, heightSs, marginSs,
            staffPosition, builder);
    }

    /**
     * Places the staccato dot on the given side of the staff ({@link Direction#UP} = above,
     * {@link Direction#DOWN} = below).
     * <p>
     * At or beyond the staff edge, this is edge-anchored with margin, identical to
     * {@link #stackAbove}/{@link #stackBelow}. Within the staff, the dot's <em>center</em> —
     * not its edge — sits at {@link #staccatoAnchorCeilingSs}/{@link #staccatoAnchorFloorSs},
     * since that distance already fully specifies the dot's position relative to the note;
     * margin only applies to avoid colliding with already-reserved content (e.g. a stem tip),
     * not to the ideal, uncollided position.
     *
     * @return the computed top Y (above) or bottom Y (below) in staff-space units
     */
    public static double stackStaccato(
        Direction direction,
        StaffExtents extents,
        LineElement element,
        double xSs, double widthSs, double heightSs, double marginSs,
        int staffPosition,
        LayoutResult.Builder builder) {

        var atOrBeyondStaffEdge = direction.isUp()
            ? staffPosition <= TOP_STAFF_LINE_POSITION
            : staffPosition >= BOTTOM_STAFF_LINE_POSITION;

        if (atOrBeyondStaffEdge) {
            return stackAtAnchor(direction, extents, element, xSs, widthSs, heightSs, marginSs,
                staffPosition, builder);
        }

        var centerSs = direction.isUp()
            ? staccatoAnchorCeilingSs(staffPosition)
            : staccatoAnchorFloorSs(staffPosition);

        return stackAtCenter(direction, extents, element, xSs, widthSs, heightSs, marginSs,
            centerSs, builder);
    }

    /**
     * Places an element on the given side of the staff, beyond whatever is already reserved
     * (e.g. staccato), but never closer to the staff than {@code staffGapSs} from the staff
     * edge. Used to stack accent beyond staccato: staccato's own note-relative position can sit
     * closer to the staff than accent's minimum clearance requires, so accent must satisfy
     * both — whichever constraint is further from the staff wins.
     *
     * @return the computed top Y (above) or bottom Y (below) in staff-space units
     */
    public static double stackBeyond(
        Direction direction,
        StaffExtents extents,
        LineElement element,
        double xSs, double widthSs, double heightSs, double marginSs, double staffGapSs,
        LayoutResult.Builder builder) {

        var above = direction.isUp();
        var queryXSs = xSs - STRUCTURAL_HORIZONTAL_MARGIN_SS;
        var queryWidthSs = widthSs + 2 * STRUCTURAL_HORIZONTAL_MARGIN_SS;
        var currentSs = extents.yGet(above, queryXSs, queryWidthSs);

        double elementTopYSs;
        double reserveEdgeYSs;

        if (above) {
            var naturalYSs = currentSs - marginSs - heightSs;
            var staffMinimumYSs = STAFF_TOP_Y_SS - staffGapSs - heightSs;
            elementTopYSs = Math.min(naturalYSs, staffMinimumYSs);
            reserveEdgeYSs = elementTopYSs;
        } else {
            var naturalTopYSs = currentSs + marginSs;
            var staffMinimumTopYSs = STAFF_BOT_Y_SS + staffGapSs;
            elementTopYSs = Math.max(naturalTopYSs, staffMinimumTopYSs);
            reserveEdgeYSs = elementTopYSs + heightSs;
        }

        extents.ySet(above, xSs, widthSs, reserveEdgeYSs);

        builder.putDecorationLayout(element,
            new LayoutResult.DecorationLayout(xSs, elementTopYSs, widthSs, heightSs, marginSs));

        return above ? elementTopYSs : reserveEdgeYSs;
    }

    /**
     * Places an element on the given side of the staff at the anchored ceiling/floor for
     * {@code staffPosition}. Shared core for {@link #stackAbove}, {@link #stackBelow}, and
     * the edge-anchored branch of {@link #stackStaccato}.
     */
    static double stackAtAnchor(
        Direction direction,
        StaffExtents extents,
        LineElement element,
        double xSs, double widthSs, double heightSs, double marginSs,
        int staffPosition,
        LayoutResult.Builder builder) {

        var anchorSs = direction.isUp() ? anchorCeilingSs(staffPosition) : anchorFloorSs(staffPosition);
        return stackAtAnchor(direction, extents, element, xSs, widthSs, heightSs, marginSs,
            anchorSs, builder);
    }

    private static double stackAtAnchor(
        Direction direction,
        StaffExtents extents,
        LineElement element,
        double xSs, double widthSs, double heightSs, double marginSs,
        double anchorSs,
        LayoutResult.Builder builder) {

        var above = direction.isUp();
        var queryXSs = xSs - STRUCTURAL_HORIZONTAL_MARGIN_SS;
        var queryWidthSs = widthSs + 2 * STRUCTURAL_HORIZONTAL_MARGIN_SS;
        var currentSs = extents.yGet(above, queryXSs, queryWidthSs);
        var boundSs = above ? Math.min(currentSs, anchorSs) : Math.max(currentSs, anchorSs);

        double elementTopYSs;
        double reserveEdgeYSs;

        if (above) {
            // Position: bottom margin between this element's bottom and the ceiling
            elementTopYSs = boundSs - marginSs - heightSs;
            reserveEdgeYSs = elementTopYSs;
        } else {
            // Position: top margin between the floor and this element's top
            elementTopYSs = boundSs + marginSs;
            reserveEdgeYSs = elementTopYSs + heightSs;
        }

        // Reserve at element edge (top above, bottom below). The neighboring tier applies its
        // own margin when it queries, so each tier-to-tier gap = the neighboring element's margin.
        extents.ySet(above, xSs, widthSs, reserveEdgeYSs);

        builder.putDecorationLayout(element,
            new LayoutResult.DecorationLayout(xSs, elementTopYSs, widthSs, heightSs, marginSs));

        return above ? elementTopYSs : reserveEdgeYSs;
    }

    private static double stackAtCenter(
        Direction direction,
        StaffExtents extents,
        LineElement element,
        double xSs, double widthSs, double heightSs, double marginSs,
        double centerSs,
        LayoutResult.Builder builder) {

        var above = direction.isUp();
        // Query: expand by horizontal margin (collapses between adjacent elements)
        var queryXSs = xSs - STRUCTURAL_HORIZONTAL_MARGIN_SS;
        var queryWidthSs = widthSs + 2 * STRUCTURAL_HORIZONTAL_MARGIN_SS;
        var currentSs = extents.yGet(above, queryXSs, queryWidthSs);

        double elementTopYSs;
        double reserveEdgeYSs;

        if (above) {
            // Ideal (uncollided) position: centered exactly at the note-relative distance.
            var idealBottomYSs = centerSs + heightSs / 2.0;
            // Collision constraint: don't intrude into what's already reserved, with margin.
            var collisionBottomYSs = currentSs - marginSs;
            var elementBottomYSs = Math.min(idealBottomYSs, collisionBottomYSs);
            elementTopYSs = elementBottomYSs - heightSs;
            reserveEdgeYSs = elementTopYSs;
        } else {
            var idealTopYSs = centerSs - heightSs / 2.0;
            var collisionTopYSs = currentSs + marginSs;
            elementTopYSs = Math.max(idealTopYSs, collisionTopYSs);
            reserveEdgeYSs = elementTopYSs + heightSs;
        }

        // Reserve at element edge. The neighboring tier applies its own margin when it
        // queries, so each tier-to-tier gap = the neighboring element's margin.
        extents.ySet(above, xSs, widthSs, reserveEdgeYSs);

        builder.putDecorationLayout(element,
            new LayoutResult.DecorationLayout(xSs, elementTopYSs, widthSs, heightSs, marginSs));

        return above ? elementTopYSs : reserveEdgeYSs;
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
