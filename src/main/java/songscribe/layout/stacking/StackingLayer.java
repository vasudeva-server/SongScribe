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
 * the collision-aware placement methods that read and reserve the one and write the other.
 * <p>
 * Every placement method that places an element reserves its footprint in {@link #extents()} and
 * records its {@link LayoutResult.DecorationLayout.Plain} in the decoration table, so the two never
 * disagree about where an element sits. The exception is
 * {@link #stackAboveWithRegions}, which reserves but returns its placement for the caller to wrap
 * and record.
 * <p>
 * Each stacker builds one per extents layer it owns. The layer holds no state of its own beyond
 * the two collaborators, so two layers over the same extents and table behave as one.
 * <p>
 * Reserve-edge / return convention, shared by every placing method: the reservation is written at
 * the element's outer edge (its top above the staff, its bottom below), so a neighboring tier that
 * later queries this step adds its own margin — each tier-to-tier gap equals the outer element's
 * margin. The value returned is that outer Y.
 */
final class StackingLayer {

    private final StaffExtents extents;
    private final DecorationLayouts.Builder decorationLayouts;

    /**
     * Makes the layer over {@code extents}, recording into {@code decorationLayouts}.
     *
     * @param extents           the extents layer placements read and reserve in
     * @param decorationLayouts the table placements record their layouts in
     */
    StackingLayer(StaffExtents extents, DecorationLayouts.Builder decorationLayouts) {
        this.extents = extents;
        this.decorationLayouts = decorationLayouts;
    }

    /**
     * Returns the extents layer this layer reserves in, for seeding and for queries no placement
     * method covers.
     *
     * @return the extents layer
     */
    StaffExtents extents() {
        return extents;
    }

    /**
     * Places an element at the anchored ceiling (above) or floor (below) for the anchor's staff
     * position, reserving a flat footprint and recording the element's layout.
     * <p>
     * The element's inner edge is {@link #anchoredInnerEdgeYSs}: the more-outward of the anchor and
     * whatever this layer already holds under the footprint, moved one margin further out.
     *
     * @param element   the element to place, which supplies its own height
     * @param footprint the element's horizontal footprint
     * @param anchor    the side, the staff position the anchor is taken from, and the margin
     * @return the element's outer Y in staff-space units (top above, bottom below)
     */
    <E extends LineElement & IntrinsicHeight> double stackAtAnchor(
        E element, Footprint footprint, StaffAnchor anchor) {

        return stackAtAnchor(element, element.intrinsicHeightSs(), footprint, anchor);
    }

    /**
     * Places an element whose height is not its own at the anchored ceiling (above) or floor
     * (below), exactly as {@link #stackAtAnchor(LineElement, Footprint, StaffAnchor)} does.
     * <p>
     * This is the door for an element that cannot own its height — a text annotation, whose height
     * comes from the annotation font.
     *
     * @param element   the element to place
     * @param heightSs  the height to reserve, in staff spaces; for an element implementing
     *                  {@link IntrinsicHeight} call the overload without it instead, so the element
     *                  supplies it
     * @param footprint the element's horizontal footprint
     * @param anchor    the side, the staff position the anchor is taken from, and the margin
     * @return the element's outer Y in staff-space units (top above, bottom below)
     */
    double stackAtAnchor(
        LineElement element, double heightSs, Footprint footprint, StaffAnchor anchor) {

        var edge = new InnerEdge(
            anchor.direction(), anchoredInnerEdgeYSs(footprint, anchor), anchor.marginSs());

        return placeAtInnerEdge(element, heightSs, Outline.flat(footprint), edge);
    }

    /**
     * The inner edge {@link #stackAtAnchor} would compute for this footprint, without placing or
     * reserving anything: the more-outward of the anchored ceiling/floor and whatever this layer
     * already holds under the footprint, moved one margin further out.
     * <p>
     * Split out so {@link DynamicGrouper}'s shared-baseline pass, which asks every member of a group
     * what it wants before placing any of them, reads each member's unaided demand from here.
     *
     * @param footprint the element's horizontal footprint
     * @param anchor    the side, the staff position the anchor is taken from, and the margin
     * @return the element's inner edge in staff-space units (bottom above, top below)
     */
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
     * Places the staccato dot on its side of the staff.
     * <p>
     * At or beyond the staff edge, this is {@link #placeAndReserveClamped}. Within the staff, the
     * dot's <em>center</em> — not its edge — sits at {@link StackingUtils#staccatoAnchorCeilingSs}
     * or {@link StackingUtils#staccatoAnchorFloorSs}, since that distance already fully specifies
     * the dot's position relative to the note; the padding only applies to avoid colliding with
     * already-reserved content (e.g. a stem tip), not to the ideal, uncollided position.
     * <p>
     * The dot reserves the outer edge of {@code outline} — its round outline — rather than the top
     * of its box, so an accent stacking outside it clears the circle it actually is. Both placement
     * branches reserve it, and must stay in step.
     *
     * @param element       the staccato dot
     * @param outline       the dot's footprint, a flat inner edge, and its round outer edge from
     *                      {@link songscribe.layout.ShapeProfile#outerEdge}
     * @param side          the side of the staff and the dot's paddings
     * @param staffPosition the staff position of the note the dot belongs to
     * @return the dot's top Y (above) or bottom Y (below) in staff-space units
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
     * Places an element at LilyPond's {@code aligned_side} position: the more-outward of its
     * real-reservation support edge plus its padding and the staff edge plus its staff-padding.
     * <p>
     * The inner edge is {@link #clampedInnerEdgeYSs}. The element reserves the outer edge of
     * {@code outline}; an element may have a sloped inner edge and still reserve a flat outer one —
     * the accent does — in which case nothing that later stacks outside it can nestle into its
     * slope.
     *
     * @param element the element to place, which supplies its own height
     * @param outline the element's footprint and its inner and outer edges;
     *                {@link Outline#flat} for anything a neighbor may treat as a rectangle
     * @param side    the side of the staff and the element's paddings
     * @return the element's outer Y in staff-space units (top above, bottom below)
     */
    <E extends LineElement & IntrinsicHeight> double placeAndReserveClamped(
        E element, Outline outline, AlignedSide side) {

        var edge = new InnerEdge(
            side.direction(), clampedInnerEdgeYSs(outline, side), side.paddingSs());

        return placeAtInnerEdge(element, outline, edge);
    }

    /**
     * The inner edge {@link #placeAndReserveClamped} would compute for this outline, without
     * placing or reserving anything.
     * <p>
     * The support edge comes from {@link StaffExtents#clearance} — the real reservations under the
     * element's inner-edge profile (notehead, tie, staccato, …), excluding the staff line. When
     * nothing lies under the profile there is no support constraint and only the staff clamp
     * applies. Then, exactly as {@code Side_position_interface::aligned_side} does, the element is
     * clamped outward against the staff edge moved out by the staff-padding (the top staff line
     * above, the bottom staff line below).
     * <p>
     * Split out so {@link DynamicGrouper}'s shared-baseline pass reads each member's unaided demand
     * from here rather than repeating the support/staff-clamp arithmetic.
     *
     * @param outline the element's footprint and edges; only the inner edge is read
     * @param side    the side of the staff and the element's paddings
     * @return the element's inner edge in staff-space units (bottom above, top below)
     */
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
     * Places an element outward from an inner edge its caller has already computed, reserves the
     * outer edge of {@code outline}, and records the element's layout.
     * <p>
     * The inner edge need not be the one this element would demand on its own: a caller placing
     * several elements on one shared baseline — {@link DynamicGrouper}'s hairpin/text dynamic
     * groups — computes one Y for the whole group and then derives each member's inner edge from
     * it, and the trill seats at the most-outward edge any note in its range demands.
     *
     * @param element the element to place, which supplies its own height
     * @param outline the element's footprint and edges; only the outer edge is reserved
     * @param edge    the side, the inner edge's Y, and the margin recorded with the layout
     * @return the element's outer Y in staff-space units (top above, bottom below)
     */
    <E extends LineElement & IntrinsicHeight> double placeAtInnerEdge(
        E element, Outline outline, InnerEdge edge) {

        return placeAtInnerEdge(element, element.intrinsicHeightSs(), outline, edge);
    }

    /**
     * Places a composite element above the staff using sub-region collision detection.
     * <p>
     * Each sub-region independently queries this layer at its horizontal range to find its own
     * ceiling. The element is placed at the highest (furthest from staff) position needed across
     * all sub-regions. Each sub-region is then reserved at its own visual bottom, allowing later
     * elements to nestle into the gaps between shorter and taller sub-regions.
     * <p>
     * The placement is returned, not recorded: the caller wraps it in the layout its element needs
     * and records that.
     *
     * @param regions       the element's sub-regions, each offset from the footprint's left edge
     * @param footprint     the element's horizontal footprint
     * @param marginSs      the gap kept beyond the anchor or the reservations under each sub-region
     * @param staffPosition the staff position the anchored ceiling is taken from
     * @return the element's placed bounds, whose height is the tallest extent across
     *     {@code regions}
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

    /**
     * Places an element of {@code heightSs} outward from {@code edge}: the shared tail of every
     * edge-anchored placement.
     *
     * @param element  the element to place
     * @param heightSs the height to reserve, in staff spaces, already resolved by the caller
     * @param outline  the element's footprint and edges; only the outer edge is reserved
     * @param edge     the side, the inner edge's Y, and the margin recorded with the layout
     * @return the element's outer Y in staff-space units (top above, bottom below)
     */
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
     * Places the staccato dot with its center at {@code centerSs}, pushed outward by whatever this
     * layer holds under its footprint and then quantized off the staff lines.
     *
     * @param element  the staccato dot
     * @param outline  the dot's footprint, flat inner edge and round outer edge
     * @param side     the side of the staff and the dot's paddings; its staff-padding is unused,
     *                 since within the staff the anchor rather than the staff clamp is the outward
     *                 constraint
     * @param centerSs the dot center's ideal, uncollided Y
     * @return the dot's top Y (above) or bottom Y (below) in staff-space units
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

    /**
     * Reserves {@code reserveProfile} along the outer edge of {@code placed} and records
     * {@code placed} as {@code element}'s layout.
     *
     * @param direction      the side of the staff the element sits on
     * @param element        the element the layout is recorded for
     * @param placed         the element's final bounds
     * @param reserveProfile the element's outer edge, measured from its left edge
     * @return the element's outer Y in staff-space units (top above, bottom below)
     * @effects reserves in {@link #extents()} and records into the decoration table
     */
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
     * Queries the untagged extent under a footprint expanded by
     * {@link StackingUtils#STRUCTURAL_HORIZONTAL_MARGIN_SS} on each side (the horizontal collision
     * margin that collapses between adjacent elements).
     * <p>
     * <strong>Structural and system elements only.</strong> Every note-attached element resolves its
     * supports through {@link StaffExtents#clearance} instead. Scripts do so because that is what
     * LilyPond does; these callers do not, because they are modelled on abc2svg, and
     * {@link StackingUtils#STRUCTURAL_HORIZONTAL_MARGIN_SS} has no LilyPond counterpart — LilyPond
     * gives {@code TextScript}, {@code VoltaBracket} and {@code ChordName} no
     * {@code horizon-padding} at all, and places them by
     * {@code Axis_group_interface::add_outside_staff_grobs} rather than by
     * {@code Side_position_interface::aligned_side}.
     * <p>
     * This widens the <em>query</em>. {@link StaffExtents#clearance} instead dilates each
     * <em>reservation</em>, which is what LilyPond's {@code Skyline::padded} does. The two agree
     * exactly wherever every reservation under the footprint is flat, and diverge only against a
     * sloped one — a tie arc's chords. Prefer {@code clearance} for anything new.
     *
     * @param direction the side of the staff to query
     * @param footprint the footprint to query under, before expansion
     * @return the outermost reserved Y under the expanded footprint, as {@link StaffExtents#yGet}
     *     reports it
     */
    private double expandedExtentYSs(Direction direction, Footprint footprint) {
        var expanded = new Footprint(footprint.xSs() - STRUCTURAL_HORIZONTAL_MARGIN_SS,
            footprint.widthSs() + 2 * STRUCTURAL_HORIZONTAL_MARGIN_SS);

        return extents.yGet(direction, expanded);
    }

    /**
     * LilyPond {@code quantize-position} for a staccato dot center (staff-space, Y-down): within the
     * staff plus one position ({@link StackingUtils#STACCATO_QUANTIZE_ZONE_SS}, LilyPond
     * {@code staff_span.widen(1)}), snaps the center <em>outward</em> to the nearest odd staff
     * position — a space — so a dot a tie has pushed off its ideal anchor never comes to rest on a
     * staff line. Beyond that zone the dot keeps its raw padded position, since LilyPond does not
     * quantize once the dot has cleared the staff.
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
