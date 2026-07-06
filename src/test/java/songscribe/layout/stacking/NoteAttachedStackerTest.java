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
import songscribe.layout.LayoutEngine;
import songscribe.layout.LayoutResult;
import songscribe.layout.StaffExtents;
import songscribe.engraving.Staff;
import songscribe.engraving.SMuFLConstants;

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

    // Phase 4 (#503) — staccato/accent/tie ordering constants.
    // 2 positions above the middle line: far enough that accent's natural gap-relative position
    // (ACCENT_STACCATO_GAP_SS beyond staccato) is the binding constraint rather than the
    // staff-edge clamp — same value as GAP_BINDS_ABOVE_STAFF_POSITION in ArticulationStackingTest.
    private static final int GAP_BINDS_ABOVE_SP = -2;

    // Mirror of PROTRUDING_ARC_Y_SS for the below-staff (up-stem) direction: for sp=0,
    // anchorFloorSs = STAFF_BOT_Y_SS = 2.0, so a flat arc at 3.0 protrudes past it.
    private static final double PROTRUDING_ARC_Y_BELOW_SS = 3.0;

    // Phase 3 (#503) — clearStaccatoUnderTies: a tie set well inside the note, so its magnitude
    // is smaller than any staccato-clearance target, forcing the outward-shift branch.
    private static final double NEAR_NOTE_TIE_Y_SS = -1.0;

    // Mirror of NEAR_NOTE_TIE_Y_SS for a downward-arcing (arcSign=+1) tie.
    private static final double NEAR_NOTE_TIE_Y_BELOW_SS = 1.0;

    // Phase 4 (#503) — clearStaccatoUnderTies max-rule branch selection.
    // Above the staff (arcSign=-1): dot-center magnitude is 1.5 at both the line position
    // adjacent to the staff centre (sp=0, reuses STAFF_CENTER_SP) and its neighboring space
    // (sp=-1); dot + STACCATO_TIE_GAP_SS (2.05) is smaller than the staff-line term (2.19), so
    // the staff-line clearance binds. Two staff lines further out (sp=-2, reuses
    // GAP_BINDS_ABOVE_SP, and its neighboring space sp=-3) the dot-center magnitude is 2.5, so
    // dot + gap (3.05) exceeds the staff-line term and the dot clearance binds instead.
    private static final int SP_SPACE_ADJACENT_TO_CENTRE_ABOVE = -1;
    private static final int SP_SPACE_TWO_OUT_ABOVE = -3;

    // Below-the-staff mirror (arcSign=+1) of the constants above, for up-stem symmetry.
    private static final int SP_SPACE_ADJACENT_TO_CENTRE_BELOW = 1;
    private static final int SP_LINE_TWO_OUT_BELOW = 2;
    private static final int SP_SPACE_TWO_OUT_BELOW = 3;

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
            var centerYSs = Staff.spToSs(STAFF_CENTER_SP);
            var noteheadTopSs = centerYSs + type.getNoteheadTopOffsetSs();
            var noteheadBotSs = noteheadTopSs + type.getFullElementHeightSs();
            var expectedTopSs = Math.min(centerYSs + type.getTopYOffsetSs(StaffElement.Direction.DOWN), noteheadTopSs);
            var expectedBotSs =
                Math.max(expectedTopSs + type.getElementHeightSs(StaffElement.Direction.DOWN), noteheadBotSs);

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

            assertThat(extents.yGet(true, START_NOTE_X_SS, SMuFLConstants.NOTE_HEAD_WIDTH_SS))
                .isCloseTo(EXTREME_STEM_TOP_SS, within(TOLERANCE));
            assertThat(extents.yGet(false, START_NOTE_X_SS, SMuFLConstants.NOTE_HEAD_WIDTH_SS))
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
            assertThat(extents.yGet(true, START_NOTE_X_SS, SMuFLConstants.NOTE_HEAD_WIDTH_SS))
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
                Staff.spToSs(LEDGER_LINE_BELOW_SP) + StackingUtils.NOTE_HEAD_RADIUS_SS;
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
            assertThat(extents.yGet(true, midTieXSs, SMuFLConstants.NOTE_HEAD_WIDTH_SS))
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
        // Staccato is placed before the tie is seeded (LilyPond `avoid-slur inside`), so it never
        // sees the reduced tie margin — only the accent pass, which runs after the tie is seeded,
        // reads TIE_DECORATION_MARGIN_SS. Use an accent (alone, no staccato) to exercise that path.
        var note = stemDownNote(STAFF_CENTER_SP);
        note.addArticulation(new Articulation(note, ArticulationType.ACCENT));
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
    // Phase 4 (#503) — staccato inside the tie, accent outside (LilyPond model)
    // -------------------------------------------------------------------------

    @Nested
    class AboveStaffStaccatoTieAccentOrdering {

        @Test
        void testStaccatoInsideTieAccentOutsideWhenTiePresent() {
            var startNote = stemDownNote(STAFF_CENTER_SP);
            startNote.addArticulation(new Articulation(startNote, ArticulationType.STACCATO));
            startNote.addArticulation(new Articulation(startNote, ArticulationType.ACCENT));
            var staccato = startNote.getArticulations().get(0);
            var accent = startNote.getArticulations().get(1);

            var endNote = stemDownNote(STAFF_CENTER_SP);
            var line = detachedLine();
            var tie = new Tie(startNote, endNote);
            line.addRangeElement(tie);

            var builder = new LayoutResult.Builder();
            // Protruding flat arc: arcY = -3.0 is farther from the note than staccato's natural
            // note-relative position, so the tie — not staccato — becomes the binding constraint
            // for the accent pass, which runs after the tie is seeded.
            builder.putTieLayout(tie, flatTieLayout(START_NOTE_X_SS, END_NOTE_X_SS, PROTRUDING_ARC_Y_SS));

            var context = new StackingContext(
                List.of(mockColumnAt(startNote, START_NOTE_X_SS),
                    mockColumnAt(endNote, END_NOTE_X_SS)),
                line, builder);
            new NoteAttachedStacker(context, new StaffExtents(LINE_WIDTH_SS)).stack();

            var result = builder.build();
            var staccatoLayout = require(result.getDecorationLayout(staccato));
            var accentLayout = require(result.getDecorationLayout(accent));

            // Above the staff, smaller (more negative) Y is farther from the note. Staccato tucks
            // inside the tie (its whole box stays closer to the note than the arc); accent stacks
            // outside the tie (farther from the note than the arc) — the tie sits between them.
            assertThat(staccatoLayout.ySs())
                .describedAs("staccato must tuck inside (closer to the note than) the tie arc")
                .isGreaterThan(PROTRUDING_ARC_Y_SS);
            assertThat(accentLayout.ySs())
                .describedAs("accent must stack outside (farther from the note than) the tie arc")
                .isLessThan(PROTRUDING_ARC_Y_SS);
            assertThat(accentLayout.ySs())
                .describedAs("accent must be farther from the note than staccato")
                .isLessThan(staccatoLayout.ySs());
        }

        @Test
        void testStaccatoPositionUnchangedByTieAccentDirectlyAboveStaccatoWithoutTie() {
            // No-tie note: staccato + accent, nothing else.
            var noTieNote = stemDownNote(GAP_BINDS_ABOVE_SP);
            noTieNote.addArticulation(new Articulation(noTieNote, ArticulationType.STACCATO));
            noTieNote.addArticulation(new Articulation(noTieNote, ArticulationType.ACCENT));
            var noTieStaccato = noTieNote.getArticulations().get(0);
            var noTieAccent = noTieNote.getArticulations().get(1);

            var noTieBuilder = new LayoutResult.Builder();
            var noTieContext = new StackingContext(
                List.of(mockColumnAt(noTieNote, START_NOTE_X_SS)), detachedLine(), noTieBuilder);
            new NoteAttachedStacker(noTieContext, new StaffExtents(LINE_WIDTH_SS)).stack();
            var noTieResult = noTieBuilder.build();
            var noTieStaccatoLayout = require(noTieResult.getDecorationLayout(noTieStaccato));
            var noTieAccentLayout = require(noTieResult.getDecorationLayout(noTieAccent));

            // With-tie note: identical staff position and articulations, plus a tie whose arc is
            // farther from the note than staccato's natural position.
            var withTieStartNote = stemDownNote(GAP_BINDS_ABOVE_SP);
            withTieStartNote.addArticulation(new Articulation(withTieStartNote, ArticulationType.STACCATO));
            withTieStartNote.addArticulation(new Articulation(withTieStartNote, ArticulationType.ACCENT));
            var withTieStaccato = withTieStartNote.getArticulations().get(0);

            var withTieEndNote = stemDownNote(GAP_BINDS_ABOVE_SP);
            var line = detachedLine();
            var tie = new Tie(withTieStartNote, withTieEndNote);
            line.addRangeElement(tie);

            var withTieBuilder = new LayoutResult.Builder();
            withTieBuilder.putTieLayout(tie,
                flatTieLayout(START_NOTE_X_SS, END_NOTE_X_SS, PROTRUDING_ARC_Y_SS));
            var withTieContext = new StackingContext(
                List.of(mockColumnAt(withTieStartNote, START_NOTE_X_SS),
                    mockColumnAt(withTieEndNote, END_NOTE_X_SS)),
                line, withTieBuilder);
            new NoteAttachedStacker(withTieContext, new StaffExtents(LINE_WIDTH_SS)).stack();
            var withTieResult = withTieBuilder.build();
            var withTieStaccatoLayout = require(withTieResult.getDecorationLayout(withTieStaccato));

            // Staccato is placed before the tie is seeded, so its position never depends on
            // whether a tie is present.
            assertThat(withTieStaccatoLayout.ySs())
                .describedAs("staccato position must be unchanged by tie presence")
                .isCloseTo(noTieStaccatoLayout.ySs(), within(TOLERANCE));

            // Without a tie, accent stacks directly above staccato using the gap constant.
            var expectedNoTieAccentYSs = noTieStaccatoLayout.ySs()
                - NoteAttachedStacker.ACCENT_STACCATO_GAP_SS
                - noTieAccentLayout.heightSs();
            assertThat(noTieAccentLayout.ySs())
                .describedAs("without a tie, accent must stack directly above staccato")
                .isCloseTo(expectedNoTieAccentYSs, within(TOLERANCE));
        }
    }

    // -------------------------------------------------------------------------
    // Phase 3 (#503) — clearStaccatoUnderTies: shifts the placed tie outward to clear a
    // staccato dot tucked under its arc, reading the dot's actual placed layout rather than
    // predicting its center.
    // -------------------------------------------------------------------------

    @Nested
    class ClearStaccatoUnderTies {

        @Test
        void testShiftsTieOutwardToClearPlacedStaccatoDot() {
            var startNote = stemDownNote(GAP_BINDS_ABOVE_SP);
            startNote.addArticulation(new Articulation(startNote, ArticulationType.STACCATO));

            var endNote = stemDownNote(GAP_BINDS_ABOVE_SP);
            var line = detachedLine();
            var tie = new Tie(startNote, endNote);
            line.addRangeElement(tie);

            var builder = new LayoutResult.Builder();
            // Set well inside the note — closer than any staccato-clearance target — so the
            // shift branch (not the already-clears guard) is what's under test.
            builder.putTieLayout(tie, flatTieLayout(START_NOTE_X_SS, END_NOTE_X_SS, NEAR_NOTE_TIE_Y_SS));

            var context = new StackingContext(
                List.of(mockColumnAt(startNote, START_NOTE_X_SS),
                    mockColumnAt(endNote, END_NOTE_X_SS)),
                line, builder);
            new NoteAttachedStacker(context, new StaffExtents(LINE_WIDTH_SS)).stack();

            // Derived from the Phase 1/2 max-rule: outermost of (placed dot center + gap) and
            // (staff edge + line clearance).
            var dotCenterMagSs = -StackingUtils.staccatoAnchorCeilingSs(GAP_BINDS_ABOVE_SP);
            var targetMagSs = Math.max(
                dotCenterMagSs + NoteAttachedStacker.STACCATO_TIE_GAP_SS,
                Staff.STAFF_HALF_SS + LayoutEngine.STAFF_LINE_TIE_CLEARANCE_GAP_SS);
            var expectedYSs = -targetMagSs;

            var shiftedTie = require(builder.getTieLayout(tie));
            assertThat(shiftedTie.startYSs())
                .describedAs("tie endpoint must shift outward to clear the placed staccato dot")
                .isCloseTo(expectedYSs, within(TOLERANCE));
            assertThat(shiftedTie.cp1YSs())
                .describedAs("the whole tie, not just its endpoints, must translate")
                .isCloseTo(expectedYSs, within(TOLERANCE));
        }

        @Test
        void testLeavesTieUntouchedWhenAlreadyClearOfStaccatoDot() {
            var startNote = stemDownNote(STAFF_CENTER_SP);
            startNote.addArticulation(new Articulation(startNote, ArticulationType.STACCATO));

            var endNote = stemDownNote(STAFF_CENTER_SP);
            var line = detachedLine();
            var tie = new Tie(startNote, endNote);
            line.addRangeElement(tie);

            var builder = new LayoutResult.Builder();
            // PROTRUDING_ARC_Y_SS already sits farther from the note than the staccato-clearance
            // target for a staff-center note, so the shift must be a no-op.
            builder.putTieLayout(tie, flatTieLayout(START_NOTE_X_SS, END_NOTE_X_SS, PROTRUDING_ARC_Y_SS));

            var context = new StackingContext(
                List.of(mockColumnAt(startNote, START_NOTE_X_SS),
                    mockColumnAt(endNote, END_NOTE_X_SS)),
                line, builder);
            new NoteAttachedStacker(context, new StaffExtents(LINE_WIDTH_SS)).stack();

            var shiftedTie = require(builder.getTieLayout(tie));
            assertThat(shiftedTie.startYSs())
                .describedAs("a tie already clearing the placed dot must not be shifted")
                .isCloseTo(PROTRUDING_ARC_Y_SS, within(TOLERANCE));
        }

        @Test
        void testClearsOutermostDotWhenEndNoteCarriesTheFartherStaccato() {
            // Both endpoint notes carry a staccato, but the end note sits higher (GAP_BINDS_ABOVE_SP
            // vs. the start note at staff center), so its dot is the farther-out of the two. The tie
            // must clear that outermost dot — exercising outermostStaccatoCenterMag's comparison
            // across both notes, not just the start note.
            var startNote = stemDownNote(STAFF_CENTER_SP);
            startNote.addArticulation(new Articulation(startNote, ArticulationType.STACCATO));

            var endNote = stemDownNote(GAP_BINDS_ABOVE_SP);
            endNote.addArticulation(new Articulation(endNote, ArticulationType.STACCATO));

            var line = detachedLine();
            var tie = new Tie(startNote, endNote);
            line.addRangeElement(tie);

            var builder = new LayoutResult.Builder();
            builder.putTieLayout(tie, flatTieLayout(START_NOTE_X_SS, END_NOTE_X_SS, NEAR_NOTE_TIE_Y_SS));

            var context = new StackingContext(
                List.of(mockColumnAt(startNote, START_NOTE_X_SS),
                    mockColumnAt(endNote, END_NOTE_X_SS)),
                line, builder);
            new NoteAttachedStacker(context, new StaffExtents(LINE_WIDTH_SS)).stack();

            var startDotCenterMagSs = -StackingUtils.staccatoAnchorCeilingSs(STAFF_CENTER_SP);
            var endDotCenterMagSs = -StackingUtils.staccatoAnchorCeilingSs(GAP_BINDS_ABOVE_SP);

            // Sanity: the end note's dot really is the outer one, so picking it (not the start dot)
            // is what the assertion below verifies.
            assertThat(endDotCenterMagSs)
                .describedAs("end note's dot must sit farther from the staff than the start note's")
                .isGreaterThan(startDotCenterMagSs);

            var targetMagSs = Math.max(
                endDotCenterMagSs + NoteAttachedStacker.STACCATO_TIE_GAP_SS,
                Staff.STAFF_HALF_SS + LayoutEngine.STAFF_LINE_TIE_CLEARANCE_GAP_SS);

            var shiftedTie = require(builder.getTieLayout(tie));
            assertThat(shiftedTie.startYSs())
                .describedAs("tie must clear the outermost of the two placed dots (the end note's)")
                .isCloseTo(-targetMagSs, within(TOLERANCE));
        }
    }

    // -------------------------------------------------------------------------
    // Phase 4 (#503) — clearStaccatoUnderTies max-rule: which of the two clearances (staff-line
    // vs. dot) binds, and that a collision-pushed dot is tracked by its actually placed position
    // rather than a predicted anchor.
    // -------------------------------------------------------------------------

    @Nested
    class ClearStaccatoUnderTiesMaxRule {

        @Test
        void testStaffLineTermWinsWhenPlacedDotSitsCloseToTheStaff() {
            for (var sp : new int[] {STAFF_CENTER_SP, SP_SPACE_ADJACENT_TO_CENTRE_ABOVE}) {
                var shiftedTie = shiftedTieForStaccatoNoteAbove(sp);
                var expectedYSs =
                    -(Staff.STAFF_HALF_SS + LayoutEngine.STAFF_LINE_TIE_CLEARANCE_GAP_SS);

                assertThat(shiftedTie.startYSs())
                    .describedAs("sp=%d: staff-line clearance must win over the dot clearance".formatted(sp))
                    .isCloseTo(expectedYSs, within(TOLERANCE));
            }
        }

        @Test
        void testDotTermWinsWhenPlacedDotSitsFartherFromTheStaff() {
            for (var sp : new int[] {GAP_BINDS_ABOVE_SP, SP_SPACE_TWO_OUT_ABOVE}) {
                var shiftedTie = shiftedTieForStaccatoNoteAbove(sp);
                var dotCenterMagSs = -StackingUtils.staccatoAnchorCeilingSs(sp);
                var staffLineTermSs = Staff.STAFF_HALF_SS + LayoutEngine.STAFF_LINE_TIE_CLEARANCE_GAP_SS;
                var dotTermSs = dotCenterMagSs + NoteAttachedStacker.STACCATO_TIE_GAP_SS;

                // Sanity: this sp must actually exercise the dot-bound branch, not the staff-line one.
                assertThat(dotTermSs)
                    .describedAs("sp=%d must make the dot clearance the larger of the two terms".formatted(sp))
                    .isGreaterThan(staffLineTermSs);

                assertThat(shiftedTie.startYSs())
                    .describedAs("sp=%d: dot clearance must win over the staff-line clearance".formatted(sp))
                    .isCloseTo(-dotTermSs, within(TOLERANCE));
            }
        }

        @Test
        void testTieTracksCollisionPushedDotRatherThanItsPredictedAnchor() {
            var startNote = stemDownNote(STAFF_CENTER_SP);
            startNote.addArticulation(new Articulation(startNote, ArticulationType.STACCATO));
            var staccato = startNote.getArticulations().getFirst();

            var endNote = stemDownNote(STAFF_CENTER_SP);
            var line = detachedLine();
            var tie = new Tie(startNote, endNote);
            line.addRangeElement(tie);

            var builder = new LayoutResult.Builder();
            // Extreme stem forces the staccato dot far past its natural (uncollided) anchor.
            builder.putStemLayout(startNote,
                new LayoutResult.StemLayout(EXTREME_STEM_TOP_SS, EXTREME_STEM_BOT_SS, 0, true));
            builder.putTieLayout(tie, flatTieLayout(START_NOTE_X_SS, END_NOTE_X_SS, NEAR_NOTE_TIE_Y_SS));

            var context = new StackingContext(
                List.of(mockColumnAt(startNote, START_NOTE_X_SS),
                    mockColumnAt(endNote, END_NOTE_X_SS)),
                line, builder);
            new NoteAttachedStacker(context, new StaffExtents(LINE_WIDTH_SS)).stack();

            var staccatoLayout = require(builder.build().getDecorationLayout(staccato));
            var actualDotCenterMagSs = -(staccatoLayout.ySs() + staccatoLayout.heightSs() / 2);
            var naturalDotCenterMagSs = -StackingUtils.staccatoAnchorCeilingSs(STAFF_CENTER_SP);

            // Sanity: the collision actually pushed the dot past its natural anchor.
            assertThat(actualDotCenterMagSs)
                .describedAs("stem collision must push the dot outward past its natural anchor")
                .isGreaterThan(naturalDotCenterMagSs);

            var expectedYSs = -(actualDotCenterMagSs + NoteAttachedStacker.STACCATO_TIE_GAP_SS);
            var shiftedTie = require(builder.getTieLayout(tie));

            assertThat(shiftedTie.startYSs())
                .describedAs("tie endpoint must clear the actually placed (pushed) dot, not its predicted anchor")
                .isCloseTo(expectedYSs, within(TOLERANCE));
        }

        /**
         * Builds a stem-down note (arc above, arcSign=-1) carrying a staccato, ties it to a plain
         * end note starting well inside the note ({@link #NEAR_NOTE_TIE_Y_SS}), runs the full
         * stack pass, and returns the resulting (shifted) tie layout.
         */
        private static LayoutResult.TieLayout shiftedTieForStaccatoNoteAbove(int sp) {
            var startNote = stemDownNote(sp);
            startNote.addArticulation(new Articulation(startNote, ArticulationType.STACCATO));

            var endNote = stemDownNote(sp);
            var line = detachedLine();
            var tie = new Tie(startNote, endNote);
            line.addRangeElement(tie);

            var builder = new LayoutResult.Builder();
            builder.putTieLayout(tie, flatTieLayout(START_NOTE_X_SS, END_NOTE_X_SS, NEAR_NOTE_TIE_Y_SS));

            var context = new StackingContext(
                List.of(mockColumnAt(startNote, START_NOTE_X_SS),
                    mockColumnAt(endNote, END_NOTE_X_SS)),
                line, builder);
            new NoteAttachedStacker(context, new StaffExtents(LINE_WIDTH_SS)).stack();

            return require(builder.getTieLayout(tie));
        }
    }

    // -------------------------------------------------------------------------
    // Phase 4 (#503) — up-stem symmetry: the with-staccato max rule holds for a downward-arcing
    // tie (stem-up notes, arcSign=+1) exactly as it does for the upward-arcing case above.
    // -------------------------------------------------------------------------

    @Nested
    class ClearStaccatoUnderTiesDownwardArcSymmetry {

        @Test
        void testStaffLineTermWinsForDownwardArcingTie() {
            for (var sp : new int[] {STAFF_CENTER_SP, SP_SPACE_ADJACENT_TO_CENTRE_BELOW}) {
                var shiftedTie = shiftedTieForStaccatoNoteBelow(sp);
                var expectedYSs = Staff.STAFF_HALF_SS + LayoutEngine.STAFF_LINE_TIE_CLEARANCE_GAP_SS;

                assertThat(shiftedTie.startYSs())
                    .describedAs("sp=%d: staff-line clearance must win for a downward-arcing tie too".formatted(sp))
                    .isCloseTo(expectedYSs, within(TOLERANCE));
            }
        }

        @Test
        void testDotTermWinsForDownwardArcingTie() {
            for (var sp : new int[] {SP_LINE_TWO_OUT_BELOW, SP_SPACE_TWO_OUT_BELOW}) {
                var shiftedTie = shiftedTieForStaccatoNoteBelow(sp);
                var dotCenterMagSs = StackingUtils.staccatoAnchorFloorSs(sp);
                var staffLineTermSs = Staff.STAFF_HALF_SS + LayoutEngine.STAFF_LINE_TIE_CLEARANCE_GAP_SS;
                var dotTermSs = dotCenterMagSs + NoteAttachedStacker.STACCATO_TIE_GAP_SS;

                // Sanity: this sp must actually exercise the dot-bound branch, not the staff-line one.
                assertThat(dotTermSs)
                    .describedAs("sp=%d must make the dot clearance the larger of the two terms".formatted(sp))
                    .isGreaterThan(staffLineTermSs);

                assertThat(shiftedTie.startYSs())
                    .describedAs("sp=%d: dot clearance must win for a downward-arcing tie too".formatted(sp))
                    .isCloseTo(dotTermSs, within(TOLERANCE));
            }
        }

        /**
         * Mirrors {@link ClearStaccatoUnderTiesMaxRule#shiftedTieForStaccatoNoteAbove} for a
         * stem-up note (arc below, arcSign=+1).
         */
        private static LayoutResult.TieLayout shiftedTieForStaccatoNoteBelow(int sp) {
            var startNote = stemUpNote(sp);
            startNote.addArticulation(new Articulation(startNote, ArticulationType.STACCATO));

            var endNote = stemUpNote(sp);
            var line = detachedLine();
            var tie = new Tie(startNote, endNote);
            line.addRangeElement(tie);

            var builder = new LayoutResult.Builder();
            builder.putTieLayout(tie, flatTieLayout(START_NOTE_X_SS, END_NOTE_X_SS, NEAR_NOTE_TIE_Y_BELOW_SS));

            var context = new StackingContext(
                List.of(mockColumnAt(startNote, START_NOTE_X_SS),
                    mockColumnAt(endNote, END_NOTE_X_SS)),
                line, builder);
            new NoteAttachedStacker(context, new StaffExtents(LINE_WIDTH_SS)).stack();

            return require(builder.getTieLayout(tie));
        }
    }

    // -------------------------------------------------------------------------
    // Phase 4 (#503) — below-staff mirror: staccato inside the tie, accent outside,
    // bottom content extent reflects the accent (Issue 6)
    // -------------------------------------------------------------------------

    @Test
    void testBelowStaffStaccatoInsideTieAccentOutsideAndBotContentExtentReflectsAccent() {
        var startNote = stemUpNote(STAFF_CENTER_SP);
        startNote.addArticulation(new Articulation(startNote, ArticulationType.STACCATO));
        startNote.addArticulation(new Articulation(startNote, ArticulationType.ACCENT));
        var staccato = startNote.getArticulations().get(0);
        var accent = startNote.getArticulations().get(1);

        var endNote = stemUpNote(STAFF_CENTER_SP);
        var line = detachedLine();
        var tie = new Tie(startNote, endNote);
        line.addRangeElement(tie);

        var builder = new LayoutResult.Builder();
        // Downward-arcing tie (stem-up notes) protruding past staccato's natural floor position.
        builder.putTieLayout(tie, flatTieLayout(START_NOTE_X_SS, END_NOTE_X_SS, PROTRUDING_ARC_Y_BELOW_SS));

        var context = new StackingContext(
            List.of(mockColumnAt(startNote, START_NOTE_X_SS),
                mockColumnAt(endNote, END_NOTE_X_SS)),
            line, builder);
        new NoteAttachedStacker(context, new StaffExtents(LINE_WIDTH_SS)).stack();

        var result = builder.build();
        var staccatoLayout = require(result.getDecorationLayout(staccato));
        var accentLayout = require(result.getDecorationLayout(accent));

        // Below the staff, larger Y is farther from the note. Staccato tucks inside the tie;
        // accent stacks outside it — the ordering mirrors the above-staff case.
        assertThat(staccatoLayout.ySs())
            .describedAs("staccato must tuck inside (closer to the note than) the tie arc")
            .isLessThan(PROTRUDING_ARC_Y_BELOW_SS);
        assertThat(accentLayout.ySs())
            .describedAs("accent must stack outside (farther from the note than) the tie arc")
            .isGreaterThan(PROTRUDING_ARC_Y_BELOW_SS);
        assertThat(accentLayout.ySs())
            .describedAs("accent must be farther from the note than staccato")
            .isGreaterThan(staccatoLayout.ySs());

        // The bottom content extent (lyric clearance) must reflect the outermost articulation
        // (accent's far edge), not the inner staccato (Issue 6).
        var accentFarEdgeSs = accentLayout.ySs() + accentLayout.heightSs();
        assertThat(context.getBotContentExtentSs())
            .describedAs("bottom content extent must reflect the accent's far edge, not staccato's")
            .isCloseTo(accentFarEdgeSs, within(TOLERANCE));
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
