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

import module java.desktop;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.font.DocumentFonts;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/**
 * Pins the glissando trim/reject arithmetic and the fall bounding box, both of which are pure
 * functions of a pair of {@link SlideGeometry.NoteContext}s and need no layout pipeline.
 */
class SlideGeometryTest extends UnitTest {

    /** Floating-point tolerance for staff-space comparisons. */
    private static final double TOLERANCE_SS = 1e-9;

    /**
     * Distance either side of a threshold used to straddle it. Large enough to survive
     * double-rounding, small enough that the two cases together pin the threshold tightly.
     */
    private static final double BOUNDARY_MARGIN_SS = 1e-6;

    /** The gap trimmed off each end of the attach ray, along the line direction. */
    private static final double GAP_SS = NoteGeometry.GLISSANDO_DRAWN_GAP_SS;

    /** Notehead-center Y of a note sitting on the staff midline. */
    private static final double MIDLINE_YSS = 0.0;

    // ---- horizontal case ----

    /** Source note's stem-free right attach edge for the horizontal case. */
    private static final double HORIZONTAL_START_XSS = 1.0;

    /** Target note's stem-free left attach edge for the horizontal case. */
    private static final double HORIZONTAL_END_XSS = 5.0;

    // ---- diagonal case: a 3-4-5 triangle, so the attach length is exactly 5 ----

    /** Horizontal run of the diagonal attach ray. */
    private static final double DIAGONAL_RUN_SS = 4.0;

    /** Vertical rise of the diagonal attach ray. */
    private static final double DIAGONAL_RISE_SS = 3.0;

    /** Length of the diagonal attach ray, {@code hypot(4, 3)}. */
    private static final double DIAGONAL_ATTACH_LENGTH_SS = 5.0;

    // ---- fall case ----

    /** Host note's stem-full column right edge for the fall case. */
    private static final double FALL_COLUMN_RIGHT_XSS = 2.0;

    /** Host note's notehead-center Y for the fall case (one space below the midline). */
    private static final double FALL_NOTE_CENTER_YSS = 1.0;

    // ---- layout pipeline cases ----

    /** Staff width available to the pipeline tests, in staff spaces. */
    private static final double STAFF_RIGHT_MARGIN_SS = 60.0;

    /** Point size of the throwaway lyric font the layout engine needs but these tests never use. */
    private static final int LYRIC_FONT_SIZE_PX = 12;

    /** Staff position of a note on the middle line. */
    private static final int MIDLINE_SP = 0;

    /** Staff position of the glissando target, below the midline so the line slopes downward. */
    private static final int TARGET_SP = 4;

    /**
     * Builds a note context whose stem-free attach extents both sit at {@code attachXSs}, at
     * notehead-center Y {@code cySs}. Only the source's right extent and the target's left extent
     * take part in the endpoint math, so collapsing both onto one X keeps each case readable.
     */
    private static SlideGeometry.NoteContext contextAt(double attachXSs, double cySs) {
        return new SlideGeometry.NoteContext(
            note(), cySs, attachXSs, attachXSs, attachXSs);
    }

    private static StaffElement note() {
        return ElementType.CROTCHET.newInstance();
    }

    // ======================================================================
    // Trimming
    // ======================================================================

    @Test
    void testHorizontalGlissandoIsTrimmedByTheGapAtEachEnd() {
        var endpoints = SlideGeometry.computeEndpoints(
            contextAt(HORIZONTAL_START_XSS, MIDLINE_YSS),
            contextAt(HORIZONTAL_END_XSS, MIDLINE_YSS));

        assertThat(endpoints).isNotNull();
        assertThat(endpoints.startXSs()).isEqualTo(HORIZONTAL_START_XSS + GAP_SS, within(TOLERANCE_SS));
        assertThat(endpoints.endXSs()).isEqualTo(HORIZONTAL_END_XSS - GAP_SS, within(TOLERANCE_SS));
        assertThat(endpoints.startYSs()).isEqualTo(MIDLINE_YSS, within(TOLERANCE_SS));
        assertThat(endpoints.endYSs()).isEqualTo(MIDLINE_YSS, within(TOLERANCE_SS));
        assertThat(endpoints.angle()).isEqualTo(0.0, within(TOLERANCE_SS));
        assertThat(endpoints.length())
            .isEqualTo(HORIZONTAL_END_XSS - HORIZONTAL_START_XSS - 2 * GAP_SS, within(TOLERANCE_SS));
    }

    @Test
    void testDiagonalGlissandoAngleAndLengthMatchTheTrimmedSegment() {
        // The gap is measured along the line, so each end moves by gap·(unitX, unitY).
        var unitX = DIAGONAL_RUN_SS / DIAGONAL_ATTACH_LENGTH_SS;
        var unitY = DIAGONAL_RISE_SS / DIAGONAL_ATTACH_LENGTH_SS;

        var endpoints = SlideGeometry.computeEndpoints(
            contextAt(0.0, MIDLINE_YSS),
            contextAt(DIAGONAL_RUN_SS, DIAGONAL_RISE_SS));

        assertThat(endpoints).isNotNull();
        assertThat(endpoints.startXSs()).isEqualTo(unitX * GAP_SS, within(TOLERANCE_SS));
        assertThat(endpoints.startYSs()).isEqualTo(unitY * GAP_SS, within(TOLERANCE_SS));
        assertThat(endpoints.endXSs())
            .isEqualTo(DIAGONAL_RUN_SS - unitX * GAP_SS, within(TOLERANCE_SS));
        assertThat(endpoints.endYSs())
            .isEqualTo(DIAGONAL_RISE_SS - unitY * GAP_SS, within(TOLERANCE_SS));

        assertThat(endpoints.angle())
            .isEqualTo(Math.atan2(DIAGONAL_RISE_SS, DIAGONAL_RUN_SS), within(TOLERANCE_SS));
        assertThat(endpoints.length())
            .isEqualTo(DIAGONAL_ATTACH_LENGTH_SS - 2 * GAP_SS, within(TOLERANCE_SS));

        // The reported length must be the distance between the reported endpoints, not just the
        // arithmetic that produced them.
        assertThat(Math.hypot(
            endpoints.endXSs() - endpoints.startXSs(),
            endpoints.endYSs() - endpoints.startYSs()))
            .isEqualTo(endpoints.length(), within(TOLERANCE_SS));
    }

    // ======================================================================
    // Rejection
    // ======================================================================

    @Test
    void testGlissandoIsRejectedWhenTheAttachLengthLeavesNoRoomForBothGaps() {
        // Exactly 2·gap is rejected — the guard is `attachLength <= 2 * gap`.
        var endpoints = SlideGeometry.computeEndpoints(
            contextAt(0.0, MIDLINE_YSS),
            contextAt(2 * GAP_SS, MIDLINE_YSS));

        assertThat(endpoints).isNull();
    }

    @Test
    void testGlissandoIsRejectedWhenTheDrawnLengthFallsBelowTheMinimum() {
        // Room for both gaps, but the remainder is a hair under the minimum drawn length.
        var attachLengthSs =
            2 * GAP_SS + SlideGeometry.MIN_RECT_LENGTH_SS - BOUNDARY_MARGIN_SS;

        var endpoints = SlideGeometry.computeEndpoints(
            contextAt(0.0, MIDLINE_YSS),
            contextAt(attachLengthSs, MIDLINE_YSS));

        assertThat(endpoints).isNull();
    }

    @Test
    void testGlissandoJustAboveTheMinimumDrawnLengthIsAccepted() {
        // Paired with the rejection case above, this pins the threshold to MIN_RECT_LENGTH_SS
        // rather than merely showing that some short glissandos are dropped.
        var attachLengthSs =
            2 * GAP_SS + SlideGeometry.MIN_RECT_LENGTH_SS + BOUNDARY_MARGIN_SS;

        var endpoints = SlideGeometry.computeEndpoints(
            contextAt(0.0, MIDLINE_YSS),
            contextAt(attachLengthSs, MIDLINE_YSS));

        assertThat(endpoints).isNotNull();
        assertThat(endpoints.length())
            .isEqualTo(SlideGeometry.MIN_RECT_LENGTH_SS + BOUNDARY_MARGIN_SS, within(TOLERANCE_SS));
    }

    @Test
    void testGlissandoWithNoFollowingElementIsRejected() {
        var endpoints = SlideGeometry.computeEndpoints(
            contextAt(HORIZONTAL_START_XSS, MIDLINE_YSS), null);

        assertThat(endpoints).isNull();
    }

    // ======================================================================
    // Fall bounds
    // ======================================================================

    @Test
    void testFallBoundsAreTheGlyphBBoxTranslatedByTheDrawPoint() {
        var src = new SlideGeometry.NoteContext(
            note(), FALL_NOTE_CENTER_YSS, FALL_COLUMN_RIGHT_XSS, 0.0, 0.0);

        var bounds = SlideGeometry.computeFallBoundsSs(src);

        var glyphXSs = FALL_COLUMN_RIGHT_XSS + NoteGeometry.FALL_GAP_SS;
        var bbox = SMuFLMetadata.requireBBox(SMuFLGlyph.BRASS_FALL_LIP_SHORT);

        assertThat(bounds).isNotNull();
        assertThat(bounds.getX()).isEqualTo(glyphXSs + bbox.left(), within(TOLERANCE_SS));
        assertThat(bounds.getY()).isEqualTo(FALL_NOTE_CENTER_YSS + bbox.top(), within(TOLERANCE_SS));
        assertThat(bounds.getWidth()).isEqualTo(bbox.width(), within(TOLERANCE_SS));
        assertThat(bounds.getHeight()).isEqualTo(bbox.height(), within(TOLERANCE_SS));

        // A fall is a single glyph with no target note, so there is no reject case to trip over.
        assertThat(bounds.getWidth()).isPositive();
        assertThat(bounds.getHeight()).isPositive();
    }

    // ======================================================================
    // The layout pipeline stores the geometry
    // ======================================================================

    private static LayoutEngine engine() {
        return new LayoutEngine(
            LyricRenderMetrics.forFont(new Font(Font.DIALOG, Font.PLAIN, LYRIC_FONT_SIZE_PX)),
            STAFF_RIGHT_MARGIN_SS,
            DocumentFonts.defaultFonts());
    }

    /**
     * Builds a line of crotchets at the given staff positions, all stem-up so the column extents
     * are deterministic.
     */
    private static Line lineOfCrotchetsAt(int... staffPositions) {
        var line = detachedLine();

        for (var staffPosition : staffPositions) {
            var element = ElementType.CROTCHET.newInstance();
            element.setUpper(true);
            element.setStaffPosition(staffPosition);
            line.addElement(element);
        }

        return line;
    }

    @Test
    void testLayoutStoresGlissandoEndpointsForTheOwningNote() {
        var line = lineOfCrotchetsAt(MIDLINE_SP, TARGET_SP);
        var source = line.getElement(0);
        source.setGlissando();

        var slideLayout = engine().layout(line).getSlideLayout(source);

        assertThat(slideLayout).isNotNull();
        assertThat(slideLayout.fallBounds()).isNull();

        var glissando = slideLayout.glissando();
        assertThat(glissando).isNotNull();

        // Y is midline-relative, so the source note on the midline starts at Y 0 and the line
        // descends toward the lower target.
        assertThat(glissando.startYSs()).isGreaterThan(MIDLINE_YSS);
        assertThat(glissando.endYSs()).isGreaterThan(glissando.startYSs());
        assertThat(glissando.endXSs()).isGreaterThan(glissando.startXSs());
        assertThat(glissando.length()).isGreaterThanOrEqualTo(SlideGeometry.MIN_RECT_LENGTH_SS);
    }

    @Test
    void testLayoutStoresFallBoundsForTheOwningNote() {
        var line = lineOfCrotchetsAt(MIDLINE_SP);
        var source = line.getElement(0);
        source.setFall();

        var slideLayout = engine().layout(line).getSlideLayout(source);

        assertThat(slideLayout).isNotNull();
        assertThat(slideLayout.glissando()).isNull();

        var fallBounds = slideLayout.fallBounds();
        assertThat(fallBounds).isNotNull();
        assertThat(fallBounds.getWidth()).isPositive();
    }

    @Test
    void testLayoutStoresNothingForAGlissandoWithNoFollowingNote() {
        var line = lineOfCrotchetsAt(MIDLINE_SP);
        var source = line.getElement(0);
        source.setGlissando();

        assertThat(engine().layout(line).getSlideLayout(source)).isNull();
    }
}
