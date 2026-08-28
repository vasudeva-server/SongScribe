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

import org.jspecify.annotations.Nullable;

import songscribe.dom.CollisionRegion;
import songscribe.dom.LineElement;
import songscribe.dom.Span;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElement.Direction;
import songscribe.engraving.LineThickness;
import songscribe.engraving.Staff;
import songscribe.layout.LayoutResult;
import songscribe.layout.LayoutResultBuilder;
import songscribe.layout.MetronomeContent;
import songscribe.layout.StaffExtents;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/**
 * Shared static helpers used by all stacking delegates.
 * <p>
 * Contains collision-aware placement methods ({@link #stackAbove}, {@link #stackBelow},
 * {@link #stackStaccato}, {@link #stackAboveWithRegions}) and anchor ceiling/floor calculations.
 * The above/below variants share their implementation, dispatched on {@link Direction}.
 */
public final class StackingUtils {

    private static final double NOTE_HEAD_HEIGHT_SS =
        SMuFLMetadata.bboxSs(SMuFLGlyph.NOTEHEAD_BLACK).heightSs();

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

    // Half the staff line's ink thickness. The staff clamp pads to the outer edge of the top/bottom
    // staff line's ink, not to its centerline (STAFF_TOP_Y_SS / STAFF_BOT_Y_SS), because every
    // script and decoration padding is ink-to-ink: a fermata, a tie arc, and a round notehead are
    // all bounded by ink, so the staff line — which also has thickness — must be measured to its ink.
    static final double STAFF_LINE_HALF_THICKNESS_SS = LineThickness.STAFF_LINE_SS / 2.0;

    // The outer ink edges of the outer staff lines: the top edge of the top line (above the staff)
    // and the bottom edge of the bottom line (below it). These, not the centerlines, are what an
    // element pads against when the staff is its outward clamp.
    public static final double STAFF_TOP_INK_Y_SS = STAFF_TOP_Y_SS - STAFF_LINE_HALF_THICKNESS_SS;
    public static final double STAFF_BOT_INK_Y_SS = STAFF_BOT_Y_SS + STAFF_LINE_HALF_THICKNESS_SS;

    // Horizontal collision margin for structural/system elements (collapses between adjacent elements)
    static final double STRUCTURAL_HORIZONTAL_MARGIN_SS = 0.75; // 6px

    // Horizontal collision margin for scripts (staccato, accent, fermata, trill). Scripts sit within
    // a note's own horizontal neighborhood, so they take a far tighter horizon than structural
    // elements: widening a script's footprint by the structural margin would make it collide with a
    // tie arc, an accidental, or a neighboring note that it does not physically overlap.
    public static final double SCRIPT_HORIZON_PADDING_SS = 0.1; // LilyPond define-grobs.scm Script horizon-padding

    // Distance from the note center to the staccato dot when the note sits on an interior
    // staff line (a line other than the top or bottom one) — clears the line itself.
    static final double STACCATO_ON_LINE_DISTANCE_SS = 1.5;

    // Distance from the note center to the staccato dot when the note sits in a space
    // between staff lines (including the top and bottom spaces).
    static final double STACCATO_BETWEEN_LINES_DISTANCE_SS = 1.0;

    // LilyPond quantize-position zone: the staff plus one staff-position beyond each outer line
    // (LilyPond side-position-interface.cc aligned_side: staff_span.widen(1)). A staccato whose
    // center falls within this band is snapped to a space; beyond it, the dot keeps its raw padded
    // position. In staff-space units, |center| <= this value.
    static final double STACCATO_QUANTIZE_ZONE_SS =
        Staff.STAFF_HALF_SS + Staff.STAFF_POSITION_OFFSET_SS;

    private StackingUtils() {
    }

    /**
     * Queries the untagged extent under a footprint expanded by
     * {@link #STRUCTURAL_HORIZONTAL_MARGIN_SS} on each side (the horizontal collision margin that
     * collapses between adjacent elements).
     * <p>
     * <strong>Structural and system elements only.</strong> Every note-attached element resolves its
     * supports through {@link StaffExtents#clearance} instead. Scripts do so because that is what
     * LilyPond does; these callers do not, because they are modelled on abc2svg, and
     * {@link #STRUCTURAL_HORIZONTAL_MARGIN_SS} has no LilyPond counterpart — LilyPond gives
     * {@code TextScript}, {@code VoltaBracket} and {@code ChordName} no {@code horizon-padding} at
     * all, and places them by {@code Axis_group_interface::add_outside_staff_grobs} rather than by
     * {@code Side_position_interface::aligned_side}.
     * <p>
     * This widens the <em>query</em>. {@link StaffExtents#clearance} instead dilates each
     * <em>reservation</em>, which is what LilyPond's {@code Skyline::padded} does. The two agree
     * exactly wherever every reservation under the footprint is flat, and diverge only against a
     * sloped one — a tie arc's chords. Prefer {@code clearance} for anything new.
     */
    static double yGetExpanded(StaffExtents extents, boolean above, double xSs, double widthSs) {
        return extents.yGet(above, xSs - STRUCTURAL_HORIZONTAL_MARGIN_SS,
            widthSs + 2 * STRUCTURAL_HORIZONTAL_MARGIN_SS);
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
     * Clamps an obstacle's top Y to never sit lower (larger Y) than the top staff line, mirroring
     * {@link #anchorCeilingSs}'s within-staff clamp for callers that bypass it (e.g. a sloped
     * ceiling that must keep the raw per-tip Y rather than {@link #anchorCeilingSs}'s flattening).
     */
    public static double staffTopClampSs(double obstacleTopYSs) {
        return Math.min(obstacleTopYSs, STAFF_TOP_Y_SS);
    }

    /**
     * The beam-resolved top Y of {@code element}'s stem, or {@code fallbackTopSs} when
     * {@code builder} has not yet resolved a {@link LayoutResult.StemLayout} for it (no stem, or
     * beam layout not yet computed). Shared by callers that need the post-beam-layout stem top,
     * distinct from a pre-beam estimate such as {@link songscribe.layout.ElementColumn#getAbsoluteTopYSs()}.
     */
    static double resolvedStemTopYSs(
        LayoutResultBuilder builder, StaffElement element, double fallbackTopSs) {

        var stemLayout = builder.getStemLayout(element);
        return stemLayout != null ? stemLayout.topYSs() : fallbackTopSs;
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
        LayoutResultBuilder builder) {

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
        LayoutResultBuilder builder) {

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
     * The dot reserves {@code reserveProfile} — its round outline — rather than the top of its box,
     * so an accent stacking outside it clears the circle it actually is. Both placement branches
     * reserve it, and must stay in step.
     *
     * @param reserveProfile the dot's outer edge, from {@link songscribe.layout.ShapeProfile#outerEdge}
     * @return the computed top Y (above) or bottom Y (below) in staff-space units
     */
    public static double stackStaccato(
        Direction direction,
        StaffExtents extents,
        LineElement element,
        double xSs, double widthSs, double heightSs,
        StaffExtents.Profile reserveProfile,
        double marginSs, double staffPaddingSs,
        double horizonPaddingSs,
        int staffPosition,
        LayoutResultBuilder builder) {

        var atOrBeyondStaffEdge = direction.isUp()
            ? staffPosition <= TOP_STAFF_LINE_POSITION
            : staffPosition >= BOTTOM_STAFF_LINE_POSITION;

        if (atOrBeyondStaffEdge) {
            var profiles = new StaffExtents.Profiles(
                StaffExtents.Profile.flat(widthSs), reserveProfile);

            return placeAndReserveClamped(direction, extents, element, xSs, widthSs, heightSs,
                profiles, marginSs, staffPaddingSs, horizonPaddingSs, builder);
        }

        var centerSs = direction.isUp()
            ? staccatoAnchorCeilingSs(staffPosition)
            : staccatoAnchorFloorSs(staffPosition);

        return stackAtCenter(direction, extents, element, xSs, widthSs, heightSs, reserveProfile,
            marginSs, horizonPaddingSs, centerSs, builder);
    }

    /**
     * Shared placement core: given a computed {@code boundSs} (the Y this element must clear on
     * the given side), positions {@code element} one {@code marginSs} inside it, reserves its
     * footprint tagged {@code tag}, writes the decoration layout, and returns the element's outer Y.
     * <p>
     * Reserve-edge / return convention: the reservation is written at the element's edge nearest
     * the staff (its top edge above, its bottom edge below), so a neighboring tier that later
     * queries this step adds its own margin — each tier-to-tier gap equals the outer element's
     * margin. The value returned is the element's outer Y: its top when above the staff, its
     * bottom when below.
     *
     * @return the element's outer Y in staff-space units (top above, bottom below)
     */
    static double placeAndReserve(
        Direction direction,
        StaffExtents extents,
        LineElement element,
        double xSs, double widthSs, double heightSs, double boundSs, double marginSs,
        LayoutResultBuilder builder) {

        var above = direction.isUp();
        var innerEdgeYSs = above ? boundSs - marginSs : boundSs + marginSs;

        return placeAtInnerEdge(direction, extents, element, xSs, widthSs, heightSs,
            innerEdgeYSs, StaffExtents.Profile.flat(widthSs), marginSs, builder);
    }

    /**
     * Shared tail for {@link #placeAndReserve} and {@link #placeAndReserveClamped}: given the
     * element's already-computed inner edge (nearest the staff), positions the element outward
     * from it by {@code heightSs}, reserves {@code reserveProfile} along its outer edge, and writes
     * the decoration layout.
     * <p>
     * {@code innerEdgeYSs} need not be the edge this element would demand on its own: a caller
     * placing several elements on one shared baseline — {@link DynamicGrouper}'s hairpin/text
     * dynamic groups — computes one Y for the whole group and then derives each member's inner
     * edge from it. That is precisely why the inner edge is a parameter here rather than being
     * recomputed inside.
     *
     * @param innerEdgeYSs        the element's inner edge in staff-space units (bottom above,
     *                            top below)
     * @param reserveProfile      the element's outer edge; {@link StaffExtents.Profile#flat} for
     *                            anything a neighbour may treat as a rectangle
     * @param decorationMarginSs  the margin recorded in the resulting {@link
     *                            LayoutResult.DecorationLayout}
     * @return the element's outer Y in staff-space units (top above, bottom below)
     */
    static double placeAtInnerEdge(
        Direction direction,
        StaffExtents extents,
        LineElement element,
        double xSs, double widthSs, double heightSs, double innerEdgeYSs,
        StaffExtents.Profile reserveProfile, double decorationMarginSs,
        LayoutResultBuilder builder) {

        var above = direction.isUp();

        double elementTopYSs;
        double reserveEdgeYSs;

        if (above) {
            elementTopYSs = innerEdgeYSs - heightSs;
            reserveEdgeYSs = elementTopYSs;
        } else {
            elementTopYSs = innerEdgeYSs;
            reserveEdgeYSs = elementTopYSs + heightSs;
        }

        extents.ySetProfile(above, xSs, reserveProfile, reserveEdgeYSs);

        builder.putDecorationLayout(element,
            new LayoutResult.DecorationLayout(xSs, elementTopYSs, widthSs, heightSs, decorationMarginSs));

        return above ? elementTopYSs : reserveEdgeYSs;
    }

    /**
     * Places an element at LilyPond's {@code aligned_side} position: the more-outward of its
     * real-reservation support edge plus {@code paddingSs} and the staff edge plus
     * {@code staffPaddingSs}.
     * <p>
     * The support edge comes from {@link StaffExtents#clearance} — the real reservations under the
     * element's inner-edge profile (notehead, tie, staccato, …), excluding the staff line. When
     * nothing lies under the profile there is no support constraint and only the staff clamp
     * applies. Then, exactly as {@code Side_position_interface::aligned_side} does, the element is
     * clamped outward against {@code staffEdge ± staffPaddingSs} (the top staff line above, the
     * bottom staff line below).
     * <p>
     * Reserve-edge / return convention matches {@link #placeAndReserve}: the reservation is written
     * at the element's outer edge (its top above, its bottom below) so a neighboring tier adds its
     * own padding when it queries this step, and the returned value is the element's outer Y.
     * <p>
     * An element may have a sloped inner edge and still reserve a flat outer one — the accent does —
     * in which case nothing that later stacks outside it can nestle into its slope.
     *
     * @param profiles         the element's inner and outer edges; {@link StaffExtents.Profiles#flat}
     *                         for anything a neighbour may treat as a rectangle
     * @param paddingSs        the element's own padding against whatever its footprint contacts
     * @param staffPaddingSs   the element's staff-padding — the minimum gap it keeps from the staff
     *                         line when the staff clamp is the outward constraint
     * @param horizonPaddingSs how far beyond its own footprint the element looks for a support
     * @return the element's outer Y in staff-space units (top above, bottom below)
     */
    static double placeAndReserveClamped(
        Direction direction,
        StaffExtents extents,
        LineElement element,
        double xSs, double widthSs, double heightSs,
        StaffExtents.Profiles profiles,
        double paddingSs, double staffPaddingSs, double horizonPaddingSs,
        LayoutResultBuilder builder) {

        var innerEdgeYSs = clampedInnerEdgeYSs(direction, extents, xSs, profiles.inner(),
            paddingSs, staffPaddingSs, horizonPaddingSs);

        return placeAtInnerEdge(direction, extents, element, xSs, widthSs, heightSs,
            innerEdgeYSs, profiles.outer(), paddingSs, builder);
    }

    /**
     * The inner edge {@link #placeAndReserveClamped} would compute for this footprint, without
     * placing or reserving anything.
     * <p>
     * Split out so a caller that must know an element's unaided demand before deciding where to
     * put it — {@link DynamicGrouper}'s shared-baseline pass, which asks every member of a group
     * what it wants before placing any of them — reads it from here rather than repeating the
     * support/staff-clamp arithmetic.
     *
     * @param innerProfile the element's inner edge, {@link StaffExtents.Profiles#inner()}
     * @return the element's inner edge in staff-space units (bottom above, top below)
     */
    static double clampedInnerEdgeYSs(
        Direction direction,
        StaffExtents extents,
        double xSs, StaffExtents.Profile innerProfile,
        double paddingSs, double staffPaddingSs, double horizonPaddingSs) {

        var above = direction.isUp();
        var support = extents.clearance(above, xSs, innerProfile, paddingSs, horizonPaddingSs);
        // The staff clamp pads to the staff line's ink edge, not its centerline — padding is
        // ink-to-ink (see STAFF_TOP_INK_Y_SS).
        var staffEdgeYSs = above ? STAFF_TOP_INK_Y_SS : STAFF_BOT_INK_Y_SS;

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
        LayoutResultBuilder builder) {

        var innerEdgeYSs =
            anchoredInnerEdgeYSs(direction, extents, xSs, widthSs, marginSs, staffPosition);

        return placeAtInnerEdge(direction, extents, element, xSs, widthSs, heightSs,
            innerEdgeYSs, StaffExtents.Profile.flat(widthSs), marginSs, builder);
    }

    /**
     * The inner edge {@link #stackAtAnchor} would compute for this footprint, without placing or
     * reserving anything: the more-outward of the anchored ceiling/floor and whatever the extents
     * already hold under the footprint, moved one {@code marginSs} further out.
     * <p>
     * Split out for the same reason as {@link #clampedInnerEdgeYSs} — {@link DynamicGrouper} needs
     * each group member's unaided demand before it places any of them.
     *
     * @return the element's inner edge in staff-space units (bottom above, top below)
     */
    static double anchoredInnerEdgeYSs(
        Direction direction,
        StaffExtents extents,
        double xSs, double widthSs, double marginSs,
        int staffPosition) {

        var above = direction.isUp();
        var anchorSs = above ? anchorCeilingSs(staffPosition) : anchorFloorSs(staffPosition);
        var currentSs = yGetExpanded(extents, above, xSs, widthSs);
        var boundSs = above ? Math.min(currentSs, anchorSs) : Math.max(currentSs, anchorSs);

        return above ? boundSs - marginSs : boundSs + marginSs;
    }

    private static double stackAtCenter(
        Direction direction,
        StaffExtents extents,
        LineElement element,
        double xSs, double widthSs, double heightSs,
        StaffExtents.Profile reserveProfile,
        double marginSs, double horizonPaddingSs,
        double centerSs,
        LayoutResultBuilder builder) {

        var above = direction.isUp();

        // The dot's supports, read the way LilyPond reads them: horizonPaddingSs dilates each
        // *reservation*, never the dot's own footprint (skyline.cc internal_distance pads `dim`).
        // Against a flat support the two are identical; against the tie arc's chords — the only
        // sloped thing that can lie beneath the innermost script — a widened query would instead read
        // the chord's interior up to horizonPaddingSs beyond the dot, where a dilated support holds
        // the chord's endpoint height flat.
        var support = extents.clearance(
            above, xSs, StaffExtents.Profile.flat(widthSs), marginSs, horizonPaddingSs);

        // The dot's edge nearest the staff: its bottom above the staff, its top below. It starts at
        // the ideal, uncollided note-relative position and is pushed outward by whatever lies under
        // it. Absence of support is a distinct case, not a reservation at the middle staff line —
        // which is what `yGet` returning 0.0 used to make it look like.
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

        var elementTopYSs = centerYSs - heightSs / 2.0;
        var reserveEdgeYSs = above ? elementTopYSs : elementTopYSs + heightSs;

        // Reserve along the element's outer edge. The neighboring tier applies its own margin when
        // it queries, so each tier-to-tier gap = the neighboring element's margin.
        extents.ySetProfile(above, xSs, reserveProfile, reserveEdgeYSs);

        builder.putDecorationLayout(element,
            new LayoutResult.DecorationLayout(xSs, elementTopYSs, widthSs, heightSs, marginSs));

        return above ? elementTopYSs : reserveEdgeYSs;
    }

    /**
     * LilyPond {@code quantize-position} for a staccato dot center (staff-space, Y-down): within the
     * staff plus one position ({@link #STACCATO_QUANTIZE_ZONE_SS}, LilyPond
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
        var position = centerYSs / Staff.STAFF_POSITION_OFFSET_SS;
        // Round outward to the nearest integer staff position (above = toward smaller Y).
        var rounded = above ? Math.floor(position) : Math.ceil(position);

        // An even staff position is a staff line; push one more half-space outward to the space.
        if (((int) rounded) % 2 == 0) {
            rounded += above ? -1 : 1;
        }

        return rounded * Staff.STAFF_POSITION_OFFSET_SS;
    }

    /**
     * Places a composite element above the staff using sub-region collision detection.
     * <p>
     * Each sub-region independently queries the existing extents at its horizontal
     * range to find its own ceiling. The element is placed at the highest (furthest
     * from staff) position needed across all sub-regions. Each sub-region is then
     * reserved at its own visual bottom, allowing later elements to nestle into
     * the gaps between shorter and taller sub-regions.
     *
     * @param content the positioned typeset content for a metronome marking, or null for
     *                every other decoration type
     */
    public static void stackAboveWithRegions(
        StaffExtents extents,
        LineElement element,
        List<CollisionRegion> regions,
        double xSs, double widthSs, double marginSs,
        int staffPosition,
        LayoutResultBuilder builder,
        @Nullable MetronomeContent content
    ) {
        var anchorSs = anchorCeilingSs(staffPosition);
        var elementYSs = Double.MAX_VALUE;

        // Query phase: each sub-region finds its own ceiling independently.
        // The element Y is the min (highest on page) across all sub-regions,
        // so the element clears all content beneath every sub-region.
        for (var region : regions) {
            var regionXSs = xSs + region.xOffsetSs();
            var regionTopSs = yGetExpanded(extents, true, regionXSs, region.widthSs());
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
                xSs, elementYSs, 0.0, widthSs, overallHeightSs, marginSs, content));

    }

    /**
     * Checks whether a range span is already covered by an existing span.
     */
    public static boolean isRangeCovered(
        StaffElement startNote,
        StaffElement endNote,
        List<? extends Span> existingElements) {

        for (var element : existingElements) {
            if (element.getAnchorElement() == startNote && element.getEndElement() == endNote) {
                return true;
            }
        }

        return false;
    }

}
