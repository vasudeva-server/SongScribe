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
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Articulation;
import songscribe.dom.ArticulationType;
import songscribe.dom.ElementType;
import songscribe.dom.FermataAttachment;
import songscribe.dom.StaffElement;
import songscribe.dom.Tie;
import songscribe.dom.Trill;
import songscribe.layout.ElementColumn;
import songscribe.layout.LayoutResult;
import songscribe.layout.StaffExtents;
import songscribe.smufl.Engraving;

class NoteAttachedStackerTest extends UnitTest {

    private static final double LINE_WIDTH_SS = 64.0;
    private static final double TOLERANCE = 1e-9;

    // Column X positions
    private static final double START_NOTE_X_SS = 10.0;
    private static final double END_NOTE_X_SS = 25.0;

    // Staff position at center of staff (sp=0)
    private static final int STAFF_CENTER_SP = 0;

    // Staff position one ledger space below the bottom staff line.
    // spToSs(8) = 4.0 = STAFF_HEIGHT_SS (the initial lowestNoteBotSs),
    // so the update (4.0 + NOTE_HEAD_RADIUS_SS) exceeds the initial value.
    private static final int LEDGER_LINE_BELOW_SP = 8;

    // Stem bounds well outside any notehead extent so the min/max clamps in
    // seedNoteBounds always pick these values over notehead geometry.
    private static final double EXTREME_STEM_TOP_SS = -10.0;
    private static final double EXTREME_STEM_BOT_SS = 10.0;

    // For sp=0: anchorCeilingSs = STAFF_TOP_Y_SS = -2.0 (since 0 > TOP_STAFF_LINE_POSITION=-4).
    // A flat tie arc at -3.0 is above the ceiling (-3.0 < -2.0) → protrudes.
    // A flat tie arc at -1.0 is below the ceiling (-1.0 ≥ -2.0) → does not protrude.
    private static final double PROTRUDING_ARC_Y_SS = -3.0;
    private static final double NON_PROTRUDING_ARC_Y_SS = -1.0;

    // Downward arc Y far below the extreme stem bot so the tie becomes the
    // bottoming constraint for botContentExtentSs.
    private static final double DOWNWARD_ARC_Y_SS = 20.0;

    // Control-point Y for the Bezier arc test (row 19).
    // B(0) = 0, B(1) = 0, B(0.5) = 0.75 * BEZIER_CP_Y_SS = -3.0.
    private static final double BEZIER_CP_Y_SS = -4.0;
    private static final double BEZIER_MID_Y_SS = -3.0;

    // -------------------------------------------------------------------------
    // Row 15 — computeNoteBounds: both code paths
    // -------------------------------------------------------------------------

    @Nested
    class ComputeNoteBounds {

        @Test
        void testTypeGeometryPathReturnsNoteheadDerivedBounds() {
            // Stem-down CROTCHET at staff center — no stem layout, so type-geometry path.
            var note = stemDownNote(STAFF_CENTER_SP);
            var type = note.getType();
            var centerYSs = StaffExtents.spToSs(STAFF_CENTER_SP);
            var noteheadTopSs = centerYSs + type.getNoteheadTopOffsetSs();
            var noteheadBotSs = noteheadTopSs + type.getFullElementHeightSs();
            var expectedTopSs = Math.min(centerYSs + type.getTopYOffsetSs(false), noteheadTopSs);
            var expectedBotSs = Math.max(expectedTopSs + type.getElementHeightSs(false), noteheadBotSs);

            var bounds = NoteAttachedStacker.computeNoteBounds(note);

            assertThat(bounds.topSs()).isCloseTo(expectedTopSs, within(TOLERANCE));
            assertThat(bounds.botSs()).isCloseTo(expectedBotSs, within(TOLERANCE));
        }

        @Test
        void testStemPathSeedsExactStemExtentsIntoNoteAttachedLayer() {
            var note = stemUpNote(STAFF_CENTER_SP);
            var builder = new LayoutResult.Builder();
            // Extreme stem values ensure the min/max clamps always select the stem.
            builder.putStemLayout(note,
                new LayoutResult.StemLayout(EXTREME_STEM_TOP_SS, EXTREME_STEM_BOT_SS, 0, true));

            var context = contextWith(List.of(mockColumnAt(note, START_NOTE_X_SS)), builder);
            var extents = new StaffExtents(LINE_WIDTH_SS);
            new NoteAttachedStacker(context, extents).stack();

            assertThat(extents.yGet(true, START_NOTE_X_SS, Engraving.NOTE_HEAD_WIDTH_SS))
                .isCloseTo(EXTREME_STEM_TOP_SS, within(TOLERANCE));
            assertThat(extents.yGet(false, START_NOTE_X_SS, Engraving.NOTE_HEAD_WIDTH_SS))
                .isCloseTo(EXTREME_STEM_BOT_SS, within(TOLERANCE));
        }

        @Test
        void testTypeGeometryPathSeedsNoteTopIntoAboveExtent() {
            // No stem layout → falls back to computeNoteBounds.
            var note = stemDownNote(STAFF_CENTER_SP);

            var context = contextWith(List.of(mockColumnAt(note, START_NOTE_X_SS)));
            var extents = new StaffExtents(LINE_WIDTH_SS);
            new NoteAttachedStacker(context, extents).stack();

            // topSs for a stem-down note at staff center is negative (notehead top is
            // above center), so it beats the initial 0.0 and is captured by yGet.
            var expectedTopSs = NoteAttachedStacker.computeNoteBounds(note).topSs();
            assertThat(extents.yGet(true, START_NOTE_X_SS, Engraving.NOTE_HEAD_WIDTH_SS))
                .isCloseTo(expectedTopSs, within(TOLERANCE));
        }
    }

    // -------------------------------------------------------------------------
    // Row 16 — seedNoteBounds: context accumulator updates
    // -------------------------------------------------------------------------

    @Nested
    class SeedNoteBoundsContextUpdates {

        @Test
        void testLowestNoteBotSsIsSetToNoteheadCenterPlusRadius() {
            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(LEDGER_LINE_BELOW_SP);

            var context = contextWith(List.of(mockColumnAt(note, START_NOTE_X_SS)));
            new NoteAttachedStacker(context, new StaffExtents(LINE_WIDTH_SS)).stack();

            var expected =
                StaffExtents.spToSs(LEDGER_LINE_BELOW_SP) + StackingUtils.NOTE_HEAD_RADIUS_SS;
            assertThat(context.getLowestNoteBotSs()).isCloseTo(expected, within(TOLERANCE));
        }

        @Test
        void testBotContentExtentSsIsSetToFullElementBottom() {
            var note = stemUpNote(STAFF_CENTER_SP);
            var builder = new LayoutResult.Builder();
            // Extreme stem makes botSs = EXTREME_STEM_BOT_SS, which exceeds the initial
            // STAFF_HALF_SS (2.0) so the context field is updated.
            builder.putStemLayout(note,
                new LayoutResult.StemLayout(EXTREME_STEM_TOP_SS, EXTREME_STEM_BOT_SS, 0, true));

            var context = contextWith(List.of(mockColumnAt(note, START_NOTE_X_SS)), builder);
            new NoteAttachedStacker(context, new StaffExtents(LINE_WIDTH_SS)).stack();

            assertThat(context.getBotContentExtentSs())
                .isCloseTo(EXTREME_STEM_BOT_SS, within(TOLERANCE));
        }
    }

    // -------------------------------------------------------------------------
    // Row 17 — seedTieBounds: upward-arcing tie, notesWithUpwardTie membership
    // -------------------------------------------------------------------------

    @Nested
    class SeedTieBoundsUpwardArc {

        @Test
        void testAddsBothEndpointsToUpwardTieSetWhenArcProtrudesAboveCeiling() {
            var startNote = stemDownNote(STAFF_CENTER_SP);
            var endNote = stemDownNote(STAFF_CENTER_SP);

            var line = detachedLine();
            var tie = new Tie(startNote, endNote);
            line.addRangeElement(tie);

            var builder = new LayoutResult.Builder();
            // Flat arc at -3.0 < anchor ceiling (-2.0) → both endpoints protrude.
            builder.putTieLayout(tie,
                flatTieLayout(START_NOTE_X_SS, END_NOTE_X_SS, PROTRUDING_ARC_Y_SS));

            var context = new StackingContext(
                List.of(mockColumnAt(startNote, START_NOTE_X_SS),
                    mockColumnAt(endNote, END_NOTE_X_SS)),
                line, builder);
            new NoteAttachedStacker(context, new StaffExtents(LINE_WIDTH_SS)).stack();

            assertThat(context.getNotesWithUpwardTie())
                .containsExactlyInAnyOrder(startNote, endNote);
        }

        @Test
        void testExcludesBothEndpointsFromSetWhenArcStaysBelowCeiling() {
            var startNote = stemDownNote(STAFF_CENTER_SP);
            var endNote = stemDownNote(STAFF_CENTER_SP);

            var line = detachedLine();
            var tie = new Tie(startNote, endNote);
            line.addRangeElement(tie);

            var builder = new LayoutResult.Builder();
            // Flat arc at -1.0 ≥ anchor ceiling (-2.0) → neither endpoint protrudes.
            builder.putTieLayout(tie,
                flatTieLayout(START_NOTE_X_SS, END_NOTE_X_SS, NON_PROTRUDING_ARC_Y_SS));

            var context = new StackingContext(
                List.of(mockColumnAt(startNote, START_NOTE_X_SS),
                    mockColumnAt(endNote, END_NOTE_X_SS)),
                line, builder);
            new NoteAttachedStacker(context, new StaffExtents(LINE_WIDTH_SS)).stack();

            assertThat(context.getNotesWithUpwardTie()).isEmpty();
        }

        @Test
        void testReservesArcYInAboveExtentAcrossTieSpan() {
            var startNote = stemDownNote(STAFF_CENTER_SP);
            var endNote = stemDownNote(STAFF_CENTER_SP);

            var line = detachedLine();
            var tie = new Tie(startNote, endNote);
            line.addRangeElement(tie);

            var builder = new LayoutResult.Builder();
            builder.putTieLayout(tie,
                flatTieLayout(START_NOTE_X_SS, END_NOTE_X_SS, PROTRUDING_ARC_Y_SS));

            var context = new StackingContext(
                List.of(mockColumnAt(startNote, START_NOTE_X_SS),
                    mockColumnAt(endNote, END_NOTE_X_SS)),
                line, builder);
            var extents = new StaffExtents(LINE_WIDTH_SS);
            new NoteAttachedStacker(context, extents).stack();

            // Query at mid-span — the entire tie arc is sampled above, so the min equals arcY.
            var midTieXSs = (START_NOTE_X_SS + END_NOTE_X_SS) / 2.0;
            assertThat(extents.yGet(true, midTieXSs, Engraving.NOTE_HEAD_WIDTH_SS))
                .isCloseTo(PROTRUDING_ARC_Y_SS, within(TOLERANCE));
        }
    }

    // -------------------------------------------------------------------------
    // Row 18 — seedTieBounds: downward-arcing tie (stem-up notes)
    // -------------------------------------------------------------------------

    @Nested
    class SeedTieBoundsDownwardArc {

        @Test
        void testUpdatesBotContentExtentSsFromDownwardArc() {
            var startNote = stemUpNote(STAFF_CENTER_SP);
            var endNote = stemUpNote(STAFF_CENTER_SP);

            var line = detachedLine();
            var tie = new Tie(startNote, endNote);
            line.addRangeElement(tie);

            var builder = new LayoutResult.Builder();
            // Stem sets botContentExtentSs to EXTREME_STEM_BOT_SS (10.0); arc at 20.0
            // is further down, so the tie becomes the bottoming constraint.
            builder.putStemLayout(startNote,
                new LayoutResult.StemLayout(EXTREME_STEM_TOP_SS, EXTREME_STEM_BOT_SS, 0, true));
            builder.putStemLayout(endNote,
                new LayoutResult.StemLayout(EXTREME_STEM_TOP_SS, EXTREME_STEM_BOT_SS, 0, true));
            builder.putTieLayout(tie,
                flatTieLayout(START_NOTE_X_SS, END_NOTE_X_SS, DOWNWARD_ARC_Y_SS));

            var context = new StackingContext(
                List.of(mockColumnAt(startNote, START_NOTE_X_SS),
                    mockColumnAt(endNote, END_NOTE_X_SS)),
                line, builder);
            new NoteAttachedStacker(context, new StaffExtents(LINE_WIDTH_SS)).stack();

            assertThat(context.getBotContentExtentSs())
                .isCloseTo(DOWNWARD_ARC_Y_SS, within(TOLERANCE));
        }

        @Test
        void testDownwardArcIsNotAddedToUpwardTieSet() {
            var startNote = stemUpNote(STAFF_CENTER_SP);
            var endNote = stemUpNote(STAFF_CENTER_SP);

            var line = detachedLine();
            var tie = new Tie(startNote, endNote);
            line.addRangeElement(tie);

            var builder = new LayoutResult.Builder();
            builder.putTieLayout(tie,
                flatTieLayout(START_NOTE_X_SS, END_NOTE_X_SS, DOWNWARD_ARC_Y_SS));

            var context = new StackingContext(
                List.of(mockColumnAt(startNote, START_NOTE_X_SS),
                    mockColumnAt(endNote, END_NOTE_X_SS)),
                line, builder);
            new NoteAttachedStacker(context, new StaffExtents(LINE_WIDTH_SS)).stack();

            assertThat(context.getNotesWithUpwardTie()).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // Row 19 — evaluateBezierYSs: cubic Bezier at t=0, t=0.5, t=1
    // -------------------------------------------------------------------------

    @Nested
    class EvaluateBezierYSs {

        @Test
        void testAtTZeroReturnsStartY() {
            // B(0) = startYSs regardless of control points
            var layout = arcTieLayout(BEZIER_CP_Y_SS);
            assertThat(NoteAttachedStacker.evaluateBezierYSs(0.0, layout))
                .isCloseTo(0.0, within(TOLERANCE));
        }

        @Test
        void testAtTOneReturnsEndY() {
            // B(1) = endYSs regardless of control points
            var layout = arcTieLayout(BEZIER_CP_Y_SS);
            assertThat(NoteAttachedStacker.evaluateBezierYSs(1.0, layout))
                .isCloseTo(0.0, within(TOLERANCE));
        }

        @Test
        void testAtTHalfReturnsHandComputedMidpoint() {
            // B(0.5) = 0.125·0 + 0.375·(-4) + 0.375·(-4) + 0.125·0 = -3.0 = BEZIER_MID_Y_SS
            var layout = arcTieLayout(BEZIER_CP_Y_SS);
            assertThat(NoteAttachedStacker.evaluateBezierYSs(0.5, layout))
                .isCloseTo(BEZIER_MID_Y_SS, within(TOLERANCE));
        }
    }

    // -------------------------------------------------------------------------
    // Row 20 — TIE_DECORATION_MARGIN_SS for notes with upward ties
    // -------------------------------------------------------------------------

    @Test
    void testUpwardTieNoteUsesReducedMarginForArticulation() {
        var note = stemDownNote(STAFF_CENTER_SP);
        note.addArticulation(new Articulation(note, ArticulationType.STACCATO));
        var articulation = note.getArticulations().getFirst();

        var endNote = stemDownNote(STAFF_CENTER_SP);
        var line = detachedLine();
        var tie = new Tie(note, endNote);
        line.addRangeElement(tie);

        var builder = new LayoutResult.Builder();
        // Protruding flat arc: arcY = -3.0 < anchorCeiling(-2.0) → note added to
        // notesWithUpwardTie; extents at START_NOTE_X_SS are set to PROTRUDING_ARC_Y_SS.
        builder.putTieLayout(tie, flatTieLayout(START_NOTE_X_SS, END_NOTE_X_SS, PROTRUDING_ARC_Y_SS));

        var context = new StackingContext(
            List.of(mockColumnAt(note, START_NOTE_X_SS),
                mockColumnAt(endNote, END_NOTE_X_SS)),
            line, builder);
        new NoteAttachedStacker(context, new StaffExtents(LINE_WIDTH_SS)).stack();

        // Tie arc sets ceiling to PROTRUDING_ARC_Y_SS; reduced margin = TIE_DECORATION_MARGIN_SS.
        // If the code mistakenly used NOTE_DECORATION_MARGIN_SS the Y would differ by 0.25 ss.
        var expectedYSs = PROTRUDING_ARC_Y_SS
            - NoteAttachedStacker.TIE_DECORATION_MARGIN_SS
            - articulation.getContentHeightSs();
        var layout = require(builder.build().getDecorationLayout(articulation));
        assertThat(layout.ySs()).isCloseTo(expectedYSs, within(TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // Row 23 — stackFermata exact Y = anchor_ceiling − margin − height
    // -------------------------------------------------------------------------

    @Test
    void testFermataYEqualsAnchorCeilingMinusMarginMinusHeight() {
        var note = stemDownNote(STAFF_CENTER_SP);
        var fermata = new FermataAttachment(note);
        note.addAttachment(fermata);

        var builder = new LayoutResult.Builder();
        var context = new StackingContext(
            List.of(mockColumnAt(note, START_NOTE_X_SS)), detachedLine(), builder);
        new NoteAttachedStacker(context, new StaffExtents(LINE_WIDTH_SS)).stack();

        var layout = require(builder.build().findAttachmentDecorationLayout(note, FermataAttachment.class));

        // Fresh extents: top[] = 0.0; sp=0 (within staff) → anchorCeiling = STAFF_TOP_Y_SS.
        // ceilingSs = min(0.0, STAFF_TOP_Y_SS) = STAFF_TOP_Y_SS.
        var ceilingSs = StackingUtils.anchorCeilingSs(STAFF_CENTER_SP);
        var expectedYSs = ceilingSs
            - NoteAttachedStacker.NOTE_DECORATION_MARGIN_SS
            - fermata.getContentHeightSs();
        assertThat(layout.ySs()).isCloseTo(expectedYSs, within(TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // Row 24 — stackSingleTrill single-note: endXSs == anchorXSs → width = glyph width
    // -------------------------------------------------------------------------

    @Test
    void testSingleNoteTrillWidthEqualsGlyphWidth() {
        var note = stemDownNote(STAFF_CENTER_SP);
        var line = detachedLine();
        line.addElement(note);
        var trill = new Trill(note);
        line.addRangeElement(trill);

        var builder = new LayoutResult.Builder();
        var context = new StackingContext(
            List.of(mockColumnAt(note, START_NOTE_X_SS)), line, builder);
        new NoteAttachedStacker(context, new StaffExtents(LINE_WIDTH_SS)).stack();

        var layout = require(builder.build().getDecorationLayout(trill));

        // Single-note trill: endXSs defaults to anchorXSs → span = 0, so
        // widthSs = max(glyphWidth, 0 + glyphWidth) = glyphWidth = getContentWidthSs().
        assertThat(layout.widthSs()).isCloseTo(trill.getContentWidthSs(), within(TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static ElementColumn mockColumnAt(StaffElement element, double xSs) {
        var column = mock(ElementColumn.class);
        when(column.getElement()).thenReturn(element);
        when(column.getXSs()).thenReturn(xSs);
        return column;
    }

    private static StackingContext contextWith(List<ElementColumn> columns) {
        return contextWith(columns, new LayoutResult.Builder());
    }

    private static StackingContext contextWith(
            List<ElementColumn> columns, LayoutResult.Builder builder) {
        return new StackingContext(columns, detachedLine(), builder);
    }

    private static StaffElement stemUpNote(int staffPosSp) {
        var note = ElementType.CROTCHET.newInstance();
        note.setStaffPosition(staffPosSp);
        note.setUpper(true);
        return note;
    }

    private static StaffElement stemDownNote(int staffPosSp) {
        var note = ElementType.CROTCHET.newInstance();
        note.setStaffPosition(staffPosSp);
        note.setUpper(false);
        return note;
    }

    @SuppressWarnings("NullAway")
    private static <T> T require(@Nullable T value) {
        assertThat(value).isNotNull();
        return value;
    }

    /**
     * Returns a cubic Bezier tie layout with all Y coordinates equal to {@code arcY}.
     * For a flat Bezier: (1-t)³+3(1-t)²t+3(1-t)t²+t³ = 1, so evaluateBezierYSs(t)=arcY for any t.
     */
    private static LayoutResult.TieLayout flatTieLayout(
            double startX, double endX, double arcY) {
        var cp1X = startX + (endX - startX) / 3.0;
        var cp2X = startX + (endX - startX) * 2.0 / 3.0;
        return new LayoutResult.TieLayout(
            startX, arcY,
            endX, arcY,
            cp1X, arcY,
            cp2X, arcY,
            cp1X, arcY,
            cp2X, arcY
        );
    }

    /**
     * Returns a cubic Bezier tie layout with startYSs=endYSs=0 and both control-point Y
     * values equal to {@code cpY}. Useful for Bezier math tests where endpoint and mid-arc
     * Y values must be independently verifiable.
     * <p>
     * B(0) = 0, B(1) = 0, B(0.5) = 0.75 * cpY.
     */
    private static LayoutResult.TieLayout arcTieLayout(double cpY) {
        var cp1X = START_NOTE_X_SS + (END_NOTE_X_SS - START_NOTE_X_SS) / 3.0;
        var cp2X = START_NOTE_X_SS + (END_NOTE_X_SS - START_NOTE_X_SS) * 2.0 / 3.0;
        return new LayoutResult.TieLayout(
            START_NOTE_X_SS, 0.0,
            END_NOTE_X_SS, 0.0,
            cp1X, cpY,
            cp2X, cpY,
            cp1X, cpY,
            cp2X, cpY
        );
    }
}
