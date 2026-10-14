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

import songscribe.dom.CollisionRegion;
import songscribe.dom.Direction;
import songscribe.dom.IntrinsicHeight;
import songscribe.dom.LineElement;
import songscribe.engraving.Staff;
import songscribe.layout.DecorationLayouts;
import songscribe.layout.Footprint;
import songscribe.layout.LayoutResult;
import songscribe.layout.StaffExtents;

import static songscribe.layout.stacking.StackingUtils.BOTTOM_STAFF_LINE_POSITION;
import static songscribe.layout.stacking.StackingUtils.STACCATO_QUANTIZE_ZONE_SS;
import static songscribe.layout.stacking.StackingUtils.STAFF_BOT_INK_Y_SS;
import static songscribe.layout.stacking.StackingUtils.STAFF_TOP_INK_Y_SS;
import static songscribe.layout.stacking.StackingUtils.STRUCTURAL_HORIZONTAL_MARGIN_SS;
import static songscribe.layout.stacking.StackingUtils.TOP_STAFF_LINE_POSITION;
import static songscribe.layout.stacking.StackingUtils.anchorCeilingSs;
import static songscribe.layout.stacking.StackingUtils.anchorFloorSs;
import static songscribe.layout.stacking.StackingUtils.staccatoAnchorCeilingSs;
import static songscribe.layout.stacking.StackingUtils.staccatoAnchorFloorSs;

/**
 * One {@link StaffExtents} layer and the decoration table its placements are recorded into, with
 * the collision-aware placement methods. Every placement method reserves its footprint in
 * {@link #extents()} and records its {@link LayoutResult.DecorationLayout.Plain}, except
 * {@link #stackAboveWithRegions}, which reserves but returns its placement for the caller to wrap
 * and record.
 * <p>
 * Reserve-edge / return convention, shared by every placing method: the reservation is written at
 * the element's outer edge (its top above the staff, its bottom below), so a neighboring tier adds
 * its own margin. The value returned is that outer Y.
 */
final class StackingLayer {

    private final StaffExtents extents;
    private final DecorationLayouts.Builder decorationLayouts;

    StackingLayer(StaffExtents extents, DecorationLayouts.Builder decorationLayouts) {
        this.extents = extents;
        this.decorationLayouts = decorationLayouts;
    }

    StaffExtents extents() {
        return extents;
    }

    <E extends LineElement & IntrinsicHeight> double stackAtAnchor(
        E element, Footprint footprint, StaffAnchor anchor) {

        return stackAtAnchor(element, element.intrinsicHeightSs(), footprint, anchor);
    }

    /**
     * @param heightSs for an element implementing {@link IntrinsicHeight} call the overload
     *                 without it instead, so the element supplies it
     */
    double stackAtAnchor(
        LineElement element, double heightSs, Footprint footprint, StaffAnchor anchor) {

        var edge = new InnerEdge(
            anchor.direction(), anchoredInnerEdgeYSs(footprint, anchor), anchor.marginSs());

        return placeAtInnerEdge(element, heightSs, Outline.flat(footprint), edge);
    }

    double anchoredInnerEdgeYSs(Footprint footprint, StaffAnchor anchor) {
        var direction = anchor.direction();
        var above = direction.isUp();
        var staffPosition = anchor.staffPosition();
        var anchorSs = above ? anchorCeilingSs(staffPosition) : anchorFloorSs(staffPosition);
        var currentSs = expandedExtentYSs(direction, footprint);
        var boundSs = above ? Math.min(currentSs, anchorSs) : Math.max(currentSs, anchorSs);
        var marginSs = anchor.marginSs();

        return above ? boundSs - marginSs : boundSs + marginSs;
    }

    /**
     * Within the staff, the dot's <em>center</em>, not its edge, sits at
     * {@link StackingUtils#staccatoAnchorCeilingSs} or {@link StackingUtils#staccatoAnchorFloorSs}.
     * <p>
     * The dot reserves the outer edge of {@code outline}, its round outline, rather than the top
     * of its box. Both placement branches reserve it, and must stay in step.
     */
    <E extends LineElement & IntrinsicHeight> double stackStaccato(
        E element, Outline outline, AlignedSide side, int staffPosition) {

        var direction = side.direction();
        var atOrBeyondStaffEdge = direction.isUp()
            ? staffPosition <= TOP_STAFF_LINE_POSITION
            : staffPosition >= BOTTOM_STAFF_LINE_POSITION;

        if (atOrBeyondStaffEdge) {
            return placeAndReserveClamped(element, outline, side);
        }

        var centerSs = direction.isUp()
            ? staccatoAnchorCeilingSs(staffPosition)
            : staccatoAnchorFloorSs(staffPosition);

        return stackAtCenter(element, outline, side, centerSs);
    }

    /**
     * An element may have a sloped inner edge and still reserve a flat outer one — the accent
     * does — in which case nothing that later stacks outside it can nestle into its slope.
     */
    <E extends LineElement & IntrinsicHeight> double placeAndReserveClamped(
        E element, Outline outline, AlignedSide side) {

        var edge = new InnerEdge(
            side.direction(), clampedInnerEdgeYSs(outline, side), side.paddingSs());

        return placeAtInnerEdge(element, outline, edge);
    }

    double clampedInnerEdgeYSs(Outline outline, AlignedSide side) {
        var direction = side.direction();
        var above = direction.isUp();
        var support = extents.clearance(direction, outline.footprint().xSs(),
            outline.profiles().inner(), side.supportPadding());
        // The staff clamp pads to the staff line's ink edge, not its centerline — padding is
        // ink-to-ink (see STAFF_TOP_INK_Y_SS).
        var staffEdgeYSs = above ? STAFF_TOP_INK_Y_SS : STAFF_BOT_INK_Y_SS;
        var staffPaddingSs = side.staffPaddingSs();

        // The element's inner edge (nearest the staff): the more-outward of the support's demand and
        // staffEdge ∓ staffPadding. Above, "more outward" is the smaller Y; below, the larger.
        if (above) {
            var staffInnerYSs = staffEdgeYSs - staffPaddingSs;
            return support.present() ? Math.min(support.ySs(), staffInnerYSs) : staffInnerYSs;
        }

        var staffInnerYSs = staffEdgeYSs + staffPaddingSs;
        return support.present() ? Math.max(support.ySs(), staffInnerYSs) : staffInnerYSs;
    }

    /**
     * The inner edge need not be the one this element would demand on its own: a caller placing
     * several elements on one shared baseline computes one Y for the group.
     */
    <E extends LineElement & IntrinsicHeight> double placeAtInnerEdge(
        E element, Outline outline, InnerEdge edge) {

        return placeAtInnerEdge(element, element.intrinsicHeightSs(), outline, edge);
    }

    /**
     * The placement is returned, not recorded.
     *
     * @param regions the element's sub-regions, each offset from the footprint's left edge
     */
    LayoutResult.DecorationLayout.Plain stackAboveWithRegions(
        List<CollisionRegion> regions, Footprint footprint, double marginSs, int staffPosition) {

        var xSs = footprint.xSs();
        var anchorSs = anchorCeilingSs(staffPosition);
        var elementYSs = Double.MAX_VALUE;

        // Query phase: each sub-region finds its own ceiling independently.
        // The element Y is the min (highest on page) across all sub-regions,
        // so the element clears all content beneath every sub-region.
        for (var region : regions) {
            var regionFootprint = new Footprint(xSs + region.xOffsetSs(), region.widthSs());
            var regionTopSs = expandedExtentYSs(Direction.UP, regionFootprint);
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
            extents.ySet(Direction.UP, new Footprint(regionXSs, region.widthSs()), regionTopSs);
        }

        // Overall height is the max extent across all sub-regions
        double overallHeightSs = 0;

        for (var region : regions) {
            overallHeightSs = Math.max(
                overallHeightSs, region.yOffsetSs() + region.heightSs());
        }

        return new LayoutResult.DecorationLayout.Plain(
            xSs, elementYSs, footprint.widthSs(), overallHeightSs, marginSs);
    }

    private double placeAtInnerEdge(
        LineElement element, double heightSs, Outline outline, InnerEdge edge) {

        var direction = edge.direction();
        var elementTopYSs = direction.isUp() ? edge.ySs() - heightSs : edge.ySs();
        var footprint = outline.footprint();
        var placed = new LayoutResult.DecorationLayout.Plain(
            footprint.xSs(), elementTopYSs, footprint.widthSs(), heightSs, edge.marginSs());

        return reserveAndRecord(direction, element, placed, outline.profiles().outer());
    }

    /**
     * @param side its staff-padding is unused
     */
    private <E extends LineElement & IntrinsicHeight> double stackAtCenter(
        E element, Outline outline, AlignedSide side, double centerSs) {

        var direction = side.direction();
        var above = direction.isUp();
        var heightSs = element.intrinsicHeightSs();
        var footprint = outline.footprint();
        var marginSs = side.paddingSs();

        // The dot's supports, read the way LilyPond reads them: the horizon padding dilates each
        // *reservation*, never the dot's own footprint (skyline.cc internal_distance pads `dim`).
        // Against a flat support the two are identical; against the tie arc's chords — the only
        // sloped thing that can lie beneath the innermost script — a widened query would instead read
        // the chord's interior up to the horizon padding beyond the dot, where a dilated support
        // holds the chord's endpoint height flat.
        var support = extents.clearance(
            direction, footprint.xSs(), outline.profiles().inner(), side.supportPadding());

        // The dot's edge nearest the staff: its bottom above the staff, its top below. It starts at
        // the ideal, uncollided note-relative position and is pushed outward by whatever lies under
        // it. Absence of support is a distinct case, not a reservation at the middle staff line.
        double innerEdgeYSs;
        double centerYSs;

        if (above) {
            innerEdgeYSs = centerSs + heightSs / 2.0;

            if (support.present()) {
                innerEdgeYSs = Math.min(innerEdgeYSs, support.ySs());
            }

            centerYSs = innerEdgeYSs - heightSs / 2.0;
        } else {
            innerEdgeYSs = centerSs - heightSs / 2.0;

            if (support.present()) {
                innerEdgeYSs = Math.max(innerEdgeYSs, support.ySs());
            }

            centerYSs = innerEdgeYSs + heightSs / 2.0;
        }

        // LilyPond quantize-position: snap the dot center outward to the next off-line space. The
        // uncollided anchor already sits in a space, so this only moves a dot that a tie (or other
        // support) has pushed off its anchor, keeping it off the staff line it would otherwise
        // land on.
        centerYSs = quantizeStaccatoCenterSs(centerYSs, direction);

        var placed = new LayoutResult.DecorationLayout.Plain(
            footprint.xSs(), centerYSs - heightSs / 2.0, footprint.widthSs(), heightSs, marginSs);

        return reserveAndRecord(direction, element, placed, outline.profiles().outer());
    }

    private double reserveAndRecord(
        Direction direction,
        LineElement element,
        LayoutResult.DecorationLayout.Plain placed,
        StaffExtents.Profile reserveProfile) {

        var above = direction.isUp();
        var reserveEdgeYSs = above ? placed.ySs() : placed.ySs() + placed.heightSs();

        extents.ySetProfile(direction, placed.xSs(), reserveProfile, reserveEdgeYSs);
        decorationLayouts.putPlain(element, placed);

        return reserveEdgeYSs;
    }

    /**
     * <strong>Structural and system elements only.</strong> Every note-attached element resolves its
     * supports through {@link StaffExtents#clearance} instead. This widens the <em>query</em>;
     * {@code clearance} dilates each <em>reservation</em>, as LilyPond does. The two diverge only
     * against a sloped reservation. Prefer {@code clearance} for anything new.
     */
    private double expandedExtentYSs(Direction direction, Footprint footprint) {
        var expanded = new Footprint(footprint.xSs() - STRUCTURAL_HORIZONTAL_MARGIN_SS,
            footprint.widthSs() + 2 * STRUCTURAL_HORIZONTAL_MARGIN_SS);

        return extents.yGet(direction, expanded);
    }

    /**
     * LilyPond {@code quantize-position} for a staccato dot center: within
     * {@link StackingUtils#STACCATO_QUANTIZE_ZONE_SS} it snaps outward to a space, so a dot a tie
     * has pushed off its anchor never rests on a staff line. Beyond the zone the dot keeps its raw
     * padded position.
     */
    private static double quantizeStaccatoCenterSs(double centerYSs, Direction direction) {
        if (Math.abs(centerYSs) > STACCATO_QUANTIZE_ZONE_SS) {
            return centerYSs;
        }

        var above = direction.isUp();
        var position = centerYSs / Staff.HALF_SPACE_SS;
        // Round outward to the nearest integer staff position (above = toward smaller Y).
        var rounded = above ? Math.floor(position) : Math.ceil(position);

        // An even staff position is a staff line; push one more half-space outward to the space.
        if (((int) rounded) % 2 == 0) {
            rounded += above ? -1 : 1;
        }

        return rounded * Staff.HALF_SPACE_SS;
    }
}
