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

import songscribe.dom.StaffElement;
import songscribe.engraving.EngravingConstants;
import songscribe.engraving.Staff;
import songscribe.engraving.StaffPosition;
import songscribe.layout.LayoutResult;
import songscribe.layout.LayoutResultBuilder;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/**
 * The staff geometry and pure functions of a staff position shared by all stacking delegates:
 * staff-line positions and ink edges, collision margins, and the anchor ceiling/floor
 * calculations.
 * <p>
 * Collision-aware placement, which reads and writes an extents layer, is on
 * {@link StackingLayer}.
 */
public final class StackingUtils {

    private static final double NOTE_HEAD_HEIGHT_SS =
        SMuFLMetadata.bboxSs(SMuFLGlyph.NOTEHEAD_BLACK).heightSs();

    static final double NOTE_HEAD_RADIUS_SS = NOTE_HEAD_HEIGHT_SS / 2.0;

    // Staff position of the top staff line (F5); positions <= this are at or above the staff
    static final int TOP_STAFF_LINE_POSITION = -StaffPosition.OUTERMOST_STAFF_LINE_SP;

    // Y coordinate of the top staff line in the middleLineY=0 coordinate system
    static final double STAFF_TOP_Y_SS = TOP_STAFF_LINE_POSITION * Staff.HALF_SPACE_SS;

    // Staff position of the bottom staff line (E4); positions >= this are at or below the staff
    static final int BOTTOM_STAFF_LINE_POSITION = StaffPosition.OUTERMOST_STAFF_LINE_SP;

    // Y coordinate of the bottom staff line in the middleLineY=0 coordinate system
    static final double STAFF_BOT_Y_SS = BOTTOM_STAFF_LINE_POSITION * Staff.HALF_SPACE_SS;

    // The outer ink edges of the outer staff lines: the top edge of the top line (above the staff)
    // and the bottom edge of the bottom line (below it). These, not the centerlines, are what an
    // element pads against when the staff is its outward clamp.
    public static final double STAFF_TOP_INK_Y_SS =
        STAFF_TOP_Y_SS - EngravingConstants.STAFF_LINE_HALF_THICKNESS_SS;
    public static final double STAFF_BOT_INK_Y_SS =
        STAFF_BOT_Y_SS + EngravingConstants.STAFF_LINE_HALF_THICKNESS_SS;

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
        Staff.STAFF_HALF_SS + Staff.HALF_SPACE_SS;

    private StackingUtils() {
    }

    /**
     * Returns the anchor ceiling Y for the given staff position, without consulting extents.
     * <p>
     * A position within or below the staff anchors at the top staff line.
     * A position at or above the top staff line anchors above the notehead.
     */
    public static double anchorCeilingSs(int staffPosition) {
        if (staffPosition > TOP_STAFF_LINE_POSITION) {
            return STAFF_TOP_Y_SS;
        }

        var noteHeadYSs = staffPosition * Staff.HALF_SPACE_SS;
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

        var noteHeadYSs = staffPosition * Staff.HALF_SPACE_SS;
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

        return staffPosition * Staff.HALF_SPACE_SS - staccatoDistanceSs(staffPosition);
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

        return staffPosition * Staff.HALF_SPACE_SS + staccatoDistanceSs(staffPosition);
    }

    /**
     * The distance from a notehead's center to the center of its staccato dot inside the staff:
     * an even staff position sits on a line, an odd one between two lines.
     */
    private static double staccatoDistanceSs(int staffPosition) {
        return staffPosition % 2 == 0
            ? STACCATO_ON_LINE_DISTANCE_SS
            : STACCATO_BETWEEN_LINES_DISTANCE_SS;
    }
}
