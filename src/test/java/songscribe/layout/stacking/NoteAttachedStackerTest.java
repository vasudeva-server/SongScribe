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
import songscribe.dom.LineElement;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElement.Direction;
import songscribe.dom.Tie;
import songscribe.dom.Trill;
import songscribe.layout.ElementColumn;
import songscribe.layout.LayoutResult;
import songscribe.layout.ShapeProfile;
import songscribe.layout.StaffExtents;
import songscribe.shape.AccentShape;
import songscribe.engraving.Staff;
import songscribe.engraving.SMuFLConstants;

class NoteAttachedStackerTest extends UnitTest {

    private static final double LINE_WIDTH_SS = 64.0;
    private static final double TOLERANCE = 1e-9;

    // What yGet reports for a footprint with nothing reserved under it: the middle staff line.
    private static final double UNRESERVED_EXTENT_SS = 0.0;

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
    // A flat tie arc at -3.0 is above the ceiling (-3.0 < -2.0) → protrudes, i.e. is more
    // outward than the STAFF_LINE floor, so it wins contact()'s argmax as the TIE neighbor.
    private static final double PROTRUDING_ARC_Y_SS = -3.0;

    // Downward arc Y far below the extreme stem bot so the tie becomes the
    // bottoming constraint for botContentExtentSs.
    private static final double DOWNWARD_ARC_Y_SS = 20.0;

    // Control-point Y for the Bezier arc test (row 19).
    // B(0) = 0, B(1) = 0, B(0.5) = 0.75 * BEZIER_CP_Y_SS = -3.0.
    private static final double BEZIER_CP_Y_SS = -4.0;
    private static final double BEZIER_MID_Y_SS = -3.0;

    // Probes strung across the arc. The count is deliberately not a multiple of the 16 chords the
    // seeding lays down, so most probes land between chord endpoints, where a chord and the curve
    // it spans differ the most.
    private static final int ARC_PROBE_COUNT = 37;

    // Both noteheads reserve their own extents at the span's ends, and near the ends the notehead is
    // the more outward of the two; probe strictly inside them.
    private static final double ARC_PROBE_MIN_T = 0.1;
    private static final double ARC_PROBE_MAX_T = 0.9;

    // With the control points at the span's thirds, B(t) = 3 * cp * t * (1 - t) — a parabola with
    // |B''| = 6 * |cp| = 24 in t. A chord spanning dt = 1/16 of it departs by at most
    // |B''| * dt^2 / 8 = 0.0117 ss. Halving the chord count would quadruple that and be caught.
    private static final double MAX_CHORD_SAGITTA_SS = 0.015;

    // Phase 4 (#503) — staccato/accent/tie ordering constants.
    // 2 positions above the middle line: far enough that the staccato's own natural
    // (note-relative) position is more outward than the staff-padding clamp, so the real
    // reservation — not the clamp — is the binding constraint. Same value as
    // GAP_BINDS_ABOVE_STAFF_POSITION in ArticulationStackingTest.
    private static final int GAP_BINDS_ABOVE_SP = -2;

    // Mirror of PROTRUDING_ARC_Y_SS for the below-staff (up-stem) direction: for sp=0,
    // anchorFloorSs = STAFF_BOT_Y_SS = 2.0, so a flat arc at 3.0 protrudes past it.
    private static final double PROTRUDING_ARC_Y_BELOW_SS = 3.0;

    // A flat arc tucked against the note, inside the notehead extent (|Y| < NOTE_HEAD_RADIUS_SS ~
    // 0.5), so it never wins over the notehead's own real reservation and leaves the
    // articulations exactly where they'd sit untied. Used to pin that the tie pushes a script only
    // where the arc has curved past the note (LilyPond's horizontal-overlap skyline), not merely
    // by existing.
    private static final double TUCKED_ARC_Y_SS = -0.1;
    private static final double TUCKED_ARC_Y_BELOW_SS = 0.1;

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
    // Row 17 — seedTieBounds: upward-arcing tie reserves the arc Y in the above extents
    // -------------------------------------------------------------------------

    @Nested
    class SeedTieBoundsUpwardArc {

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

        @Test
        void testArcIsReservedAsChordsThatLieInsideTheCurveNotStepsThatOvershootIt() {
            var startNote = stemDownNote(STAFF_CENTER_SP);
            var endNote = stemDownNote(STAFF_CENTER_SP);

            var line = detachedLine();
            var tie = new Tie(startNote, endNote);
            line.addRangeElement(tie);

            var tieLayout = arcTieLayout(BEZIER_CP_Y_SS);
            var builder = new LayoutResult.Builder();
            builder.putTieLayout(tie, tieLayout);

            var context = new StackingContext(
                List.of(mockColumnAt(startNote, START_NOTE_X_SS),
                    mockColumnAt(endNote, END_NOTE_X_SS)),
                line, builder);
            var extents = new StaffExtents(LINE_WIDTH_SS);
            new NoteAttachedStacker(context, extents).stack();

            // The control points sit at the span's thirds, so the Bezier's x is exactly linear in t
            // and the curve's Y at a given x is directly comparable to what was reserved there.
            var spanWidthSs = END_NOTE_X_SS - START_NOTE_X_SS;

            for (var i = 0; i <= ARC_PROBE_COUNT; i++) {
                var t = ARC_PROBE_MIN_T
                    + (ARC_PROBE_MAX_T - ARC_PROBE_MIN_T) * i / ARC_PROBE_COUNT;
                var probeXSs = START_NOTE_X_SS + t * spanWidthSs;
                var arcYSs = NoteAttachedStacker.evaluateBezierYSs(t, tieLayout);
                var reservedYSs = extents.yGet(true, probeXSs, 0.0);

                // A chord joins two points on a convex arc, so it lies inside it — never beyond.
                // A flat step stamped at its segment's midpoint Y instead bulges past the arc on the
                // apex side of the midpoint and falls short of it on the other, by up to half the
                // arc's rise across the segment — here fully 0.3 ss, twenty times the chord's worst
                // case. Either bound catches it.
                assertThat(reservedYSs)
                    .describedAs("reservation at x = %.3f must not bulge past the arc".formatted(probeXSs))
                    .isGreaterThanOrEqualTo(arcYSs - TOLERANCE);

                assertThat(reservedYSs)
                    .describedAs("reservation at x = %.3f must track the arc".formatted(probeXSs))
                    .isLessThanOrEqualTo(arcYSs + MAX_CHORD_SAGITTA_SS);
            }
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
        void testDownwardArcReservesOnlyBelowExtentsNotAbove() {
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
            var extents = new StaffExtents(LINE_WIDTH_SS);
            new NoteAttachedStacker(context, extents).stack();

            var midTieXSs = (START_NOTE_X_SS + END_NOTE_X_SS) / 2.0;
            assertThat(extents.yGet(false, midTieXSs, SMuFLConstants.NOTE_HEAD_WIDTH_SS))
                .describedAs("downward arc must be reserved in the below extents")
                .isCloseTo(DOWNWARD_ARC_Y_SS, within(TOLERANCE));
            assertThat(extents.yGet(true, midTieXSs, SMuFLConstants.NOTE_HEAD_WIDTH_SS))
                .describedAs("a downward arc must never be reserved in the above extents")
                .isEqualTo(UNRESERVED_EXTENT_SS);
        }
    }

    // -------------------------------------------------------------------------
    // Phase 5, Task 3 — seedTieBounds: conflicting-stem tie reserves on the SIDE the
    // renderer actually draws. Left stem up / right stem down: Tie.arcSign()'s both-stem
    // fallthrough resolves any conflicting pairing to the neutral UP branch (tie above,
    // arcSign=-1) regardless of which side is up. A pre-fix single-note routing that read
    // only the left note's direction (up → reserve BELOW) would pick the opposite side —
    // this test fails without seedTieBounds routing through span.arcSign().
    // -------------------------------------------------------------------------

    @Nested
    class SeedTieBoundsConflictingStems {

        @Test
        void testConflictingStemTieReservesOnRenderedSideNotOppositeSide() {
            var startNote = stemUpNote(STAFF_CENTER_SP);
            var endNote = stemDownNote(STAFF_CENTER_SP);

            var line = detachedLine();
            var tie = new Tie(startNote, endNote);
            line.addRangeElement(tie);

            assertThat(tie.arcSign())
                .describedAs("sanity: conflicting stems resolve to the neutral branch — arc bulges up (above)")
                .isLessThan(0);

            var builder = new LayoutResult.Builder();
            builder.putTieLayout(tie,
                flatTieLayout(START_NOTE_X_SS, END_NOTE_X_SS, PROTRUDING_ARC_Y_SS));

            var context = new StackingContext(
                List.of(mockColumnAt(startNote, START_NOTE_X_SS),
                    mockColumnAt(endNote, END_NOTE_X_SS)),
                line, builder);
            var extents = new StaffExtents(LINE_WIDTH_SS);
            new NoteAttachedStacker(context, extents).stack();

            var midTieXSs = (START_NOTE_X_SS + END_NOTE_X_SS) / 2.0;
            assertThat(extents.yGet(true, midTieXSs, SMuFLConstants.NOTE_HEAD_WIDTH_SS))
                .describedAs("conflicting-stem tie must reserve on the side the renderer actually draws (above)")
                .isCloseTo(PROTRUDING_ARC_Y_SS, within(TOLERANCE));
            assertThat(extents.yGet(false, midTieXSs, SMuFLConstants.NOTE_HEAD_WIDTH_SS))
                .describedAs("must not reserve on the opposite (below) side")
                .isEqualTo(UNRESERVED_EXTENT_SS);
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
    // Articulations avoid the tie (LilyPond model). Every script takes the tie as a side-support
    // element (Script_engraver::acknowledge_tie -> add_support), so the tie is innermost and each
    // script is pushed *outside* it — but only where the arc actually overlaps the script's
    // footprint (LilyPond merges its supports into one skyline and uses Skyline::distance). A
    // protruding arc therefore pushes the staccato and accent farther from the note; an arc tucked
    // against the note leaves them where they'd sit untied. This is the opposite of a slur, which
    // is stacked outside the scripts via `avoid-slur`.
    // -------------------------------------------------------------------------

    @Nested
    class ArticulationsAvoidTie {

        @Test
        void testAboveStaffProtrudingTiePushesStaccatoAndAccentOutward() {
            var noTie = staccatoAccentLayouts(false, false, PROTRUDING_ARC_Y_SS);
            var withTie = staccatoAccentLayouts(false, true, PROTRUDING_ARC_Y_SS);

            // The arc protrudes farther from the note than either articulation's note-relative
            // position, so both scripts must clear it. Above the staff, more outward = smaller
            // (more negative) Y.
            assertThat(withTie.staccatoYSs())
                .describedAs("staccato must be pushed outward to clear a protruding tie")
                .isLessThan(noTie.staccatoYSs());
            assertThat(withTie.accentYSs())
                .describedAs("accent must be pushed outward to clear a protruding tie")
                .isLessThan(noTie.accentYSs());
        }

        @Test
        void testBelowStaffProtrudingTiePushesStaccatoAndAccentOutward() {
            var noTie = staccatoAccentLayouts(true, false, PROTRUDING_ARC_Y_BELOW_SS);
            var withTie = staccatoAccentLayouts(true, true, PROTRUDING_ARC_Y_BELOW_SS);

            // Below the staff (up-stem note), more outward = larger Y.
            assertThat(withTie.staccatoYSs())
                .describedAs("staccato must be pushed outward to clear a protruding tie (up-stem)")
                .isGreaterThan(noTie.staccatoYSs());
            assertThat(withTie.accentYSs())
                .describedAs("accent must be pushed outward to clear a protruding tie (up-stem)")
                .isGreaterThan(noTie.accentYSs());
        }

        @Test
        void testTuckedTieLeavesStaccatoAndAccentWhereTheydSitUntied() {
            // The arc stays inside the notehead extent, so it never protrudes into either script's
            // footprint: the tie pushes a script only where the arc has curved past the note, not
            // merely by existing.
            var noTieAbove = staccatoAccentLayouts(false, false, TUCKED_ARC_Y_SS);
            var withTieAbove = staccatoAccentLayouts(false, true, TUCKED_ARC_Y_SS);
            assertThat(withTieAbove.staccatoYSs()).isCloseTo(noTieAbove.staccatoYSs(), within(TOLERANCE));
            assertThat(withTieAbove.accentYSs()).isCloseTo(noTieAbove.accentYSs(), within(TOLERANCE));

            var noTieBelow = staccatoAccentLayouts(true, false, TUCKED_ARC_Y_BELOW_SS);
            var withTieBelow = staccatoAccentLayouts(true, true, TUCKED_ARC_Y_BELOW_SS);
            assertThat(withTieBelow.staccatoYSs()).isCloseTo(noTieBelow.staccatoYSs(), within(TOLERANCE));
            assertThat(withTieBelow.accentYSs()).isCloseTo(noTieBelow.accentYSs(), within(TOLERANCE));
        }

        @Test
        void testStaccatoInnermostAccentStacksOutsideIt() {
            // A staccato and accent on a tied note stack as a script column: staccato innermost,
            // accent just outside it — even when the tie has pushed the whole column outward.
            // Above the staff, smaller (more negative) Y is farther from the note.
            var layouts = staccatoAccentLayouts(false, true, PROTRUDING_ARC_Y_SS);

            assertThat(layouts.accentYSs())
                .describedAs("accent must stack outside the staccato (farther from the note)")
                .isLessThan(layouts.staccatoYSs());
        }

        /** Staccato and accent outer Y for one note, optionally up-stem and optionally tied. */
        private record ArticulationLayouts(double staccatoYSs, double accentYSs) {
        }

        private static ArticulationLayouts staccatoAccentLayouts(
                boolean upStem, boolean withTie, double arcYSs) {
            var startNote = upStem ? stemUpNote(STAFF_CENTER_SP) : stemDownNote(STAFF_CENTER_SP);
            startNote.addArticulation(new Articulation(startNote, ArticulationType.STACCATO));
            startNote.addArticulation(new Articulation(startNote, ArticulationType.ACCENT));
            var staccato = startNote.getArticulations().get(0);
            var accent = startNote.getArticulations().get(1);

            var line = detachedLine();
            var builder = new LayoutResult.Builder();
            List<ElementColumn> columns;

            if (withTie) {
                var endNote = upStem ? stemUpNote(STAFF_CENTER_SP) : stemDownNote(STAFF_CENTER_SP);
                var tie = new Tie(startNote, endNote);
                line.addRangeElement(tie);
                builder.putTieLayout(tie, flatTieLayout(START_NOTE_X_SS, END_NOTE_X_SS, arcYSs));
                columns = List.of(mockColumnAt(startNote, START_NOTE_X_SS),
                    mockColumnAt(endNote, END_NOTE_X_SS));
            } else {
                columns = List.of(mockColumnAt(startNote, START_NOTE_X_SS));
            }

            var context = new StackingContext(columns, line, builder);
            new NoteAttachedStacker(context, new StaffExtents(LINE_WIDTH_SS)).stack();

            var result = builder.build();
            return new ArticulationLayouts(
                require(result.getDecorationLayout(staccato)).ySs(),
                require(result.getDecorationLayout(accent)).ySs());
        }
    }

    // -------------------------------------------------------------------------
    // Row 23 — stackFermata: LilyPond's aligned_side, max(realSupport + FERMATA_PADDING_SS,
    // staffEdge + SCRIPT_STAFF_PADDING_SS) outward
    // -------------------------------------------------------------------------

    @Test
    void testFermataClearsStaffPaddingClampWhenNoteWithinStaff() {
        var note = stemDownNote(STAFF_CENTER_SP);
        var fermata = new FermataAttachment(note);
        note.addAttachment(fermata);

        var builder = new LayoutResult.Builder();
        var context = new StackingContext(
            List.of(mockColumnAt(note, START_NOTE_X_SS)), detachedLine(), builder);
        new NoteAttachedStacker(context, new StaffExtents(LINE_WIDTH_SS)).stack();

        var layout = require(builder.build().findAttachmentDecorationLayout(note, FermataAttachment.class));

        // The note's own real reservation (computeNoteBounds) is well within the staff, so the
        // staff-padding clamp — not the note's edge — is the binding constraint.
        var supportBoundSs = NoteAttachedStacker.computeNoteBounds(note).topSs();
        var staffInnerYSs = StackingUtils.STAFF_TOP_Y_SS - NoteAttachedStacker.SCRIPT_STAFF_PADDING_SS;
        var innerEdgeYSs =
            Math.min(supportBoundSs - NoteAttachedStacker.FERMATA_PADDING_SS, staffInnerYSs);
        var expectedYSs = innerEdgeYSs - fermata.getContentHeightSs();
        assertThat(layout.ySs()).isCloseTo(expectedYSs, within(TOLERANCE));
    }

    @Test
    void testPreviewPathStacksFermataAboveTheStaff() {
        // computePreviewDecorationLayouts (the no-tie preview path) must place a fermata the same
        // way the full pipeline's stackFermata does: both delegate to stackFermataAt.
        var note = stemDownNote(STAFF_CENTER_SP);
        var fermata = new FermataAttachment(note);
        note.addAttachment(fermata);

        var result = NoteAttachedStacker.computePreviewDecorationLayouts(note, START_NOTE_X_SS);
        var layout = require(result.findAttachmentDecorationLayout(note, FermataAttachment.class));

        var supportBoundSs = NoteAttachedStacker.computeNoteBounds(note).topSs();
        var staffInnerYSs = StackingUtils.STAFF_TOP_Y_SS - NoteAttachedStacker.SCRIPT_STAFF_PADDING_SS;
        var innerEdgeYSs =
            Math.min(supportBoundSs - NoteAttachedStacker.FERMATA_PADDING_SS, staffInnerYSs);
        var expectedYSs = innerEdgeYSs - fermata.getContentHeightSs();
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

    @Test
    void testDegenerateTrillWithNoEndElementSeatsAtAnchorOnly() {
        // Unlike Trill(anchor) (endElement == anchor, so endIndex == anchorIndex naturally), an
        // explicit null end element makes getEndElementIndex() return -1, exercising the
        // endIndex < anchorIndex degenerate-span clamp in stackSingleTrill directly.
        var note = stemDownNote(STAFF_CENTER_SP);
        var line = detachedLine();
        line.addElement(note);
        var trill = new Trill(note);
        trill.setEndElement(null);
        line.addRangeElement(trill);

        var builder = new LayoutResult.Builder();
        var context = new StackingContext(
            List.of(mockColumnAt(note, START_NOTE_X_SS)), line, builder);
        new NoteAttachedStacker(context, new StaffExtents(LINE_WIDTH_SS)).stack();

        var layout = require(builder.build().getDecorationLayout(trill));

        assertThat(layout.widthSs()).isCloseTo(trill.getContentWidthSs(), within(TOLERANCE));
    }

    @Test
    void testTrillSkipsIntermediateLineElementWithNoColumn() {
        // A trill spanning anchor to end whose line has an intermediate element (e.g. a rest) not
        // included among the stacking columns exercises the null-column skip: the loop must pass
        // over that index without failing, and seat identically to the same trill with no
        // intermediate element at all, since a columnless rest contributes no constraint.
        var anchorNote = stemDownNote(STAFF_CENTER_SP);
        var endNote = stemDownNote(STAFF_CENTER_SP);

        var lineWithRest = detachedLine();
        lineWithRest.addElement(anchorNote);
        lineWithRest.addElement(ElementType.CROTCHET_REST.newInstance());
        lineWithRest.addElement(endNote);
        var trillWithRest = new Trill(anchorNote, endNote);
        lineWithRest.addRangeElement(trillWithRest);

        var builderWithRest = new LayoutResult.Builder();
        var contextWithRest = new StackingContext(
            List.of(mockColumnAt(anchorNote, START_NOTE_X_SS), mockColumnAt(endNote, END_NOTE_X_SS)),
            lineWithRest, builderWithRest);
        new NoteAttachedStacker(contextWithRest, new StaffExtents(LINE_WIDTH_SS)).stack();
        var layoutWithRest = require(builderWithRest.build().getDecorationLayout(trillWithRest));

        var anchorNoteNoRest = stemDownNote(STAFF_CENTER_SP);
        var endNoteNoRest = stemDownNote(STAFF_CENTER_SP);

        var lineNoRest = detachedLine();
        lineNoRest.addElement(anchorNoteNoRest);
        lineNoRest.addElement(endNoteNoRest);
        var trillNoRest = new Trill(anchorNoteNoRest, endNoteNoRest);
        lineNoRest.addRangeElement(trillNoRest);

        var builderNoRest = new LayoutResult.Builder();
        var contextNoRest = new StackingContext(
            List.of(mockColumnAt(anchorNoteNoRest, START_NOTE_X_SS),
                mockColumnAt(endNoteNoRest, END_NOTE_X_SS)),
            lineNoRest, builderNoRest);
        new NoteAttachedStacker(contextNoRest, new StaffExtents(LINE_WIDTH_SS)).stack();
        var layoutNoRest = require(builderNoRest.build().getDecorationLayout(trillNoRest));

        assertThat(layoutWithRest.ySs())
            .describedAs("the columnless rest between anchor and end contributes no constraint")
            .isCloseTo(layoutNoRest.ySs(), within(TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // Multi-note trill: the anchor note seats via TRILL_SCRIPT_PADDING_SS, subsequent notes via
    // TRILL_SPANNER_PADDING_SS; the whole span seats at whichever note demands the max Δy.
    // -------------------------------------------------------------------------

    // An explicit stem-top bound for both trill notes, chosen so neither note's real support edge
    // beats the staff-padding clamp under TRILL_SCRIPT_PADDING_SS alone (support - script padding
    // is less outward than the clamp), but DOES beat it under the larger TRILL_SPANNER_PADDING_SS
    // (support - spanner padding is more outward than the clamp) — the only way a multi-note
    // trill's max-Δy computation can be told apart from a single-padding one.
    private static final double MULTI_NOTE_TRILL_STEM_TOP_SS = -1.9;

    @Test
    void testMultiNoteTrillSeatsAtMaxDeltaYAcrossMixedScriptAndSpannerPadding() {
        var anchorNote = stemDownNote(STAFF_CENTER_SP);
        var endNote = stemDownNote(STAFF_CENTER_SP);

        var line = detachedLine();
        line.addElement(anchorNote);
        line.addElement(endNote);
        var trill = new Trill(anchorNote, endNote);
        line.addRangeElement(trill);

        var builder = new LayoutResult.Builder();
        // Both notes share the same real support edge, via an explicit stem top rather than
        // relying on notehead geometry to land in the narrow window described above.
        builder.putStemLayout(anchorNote,
            new LayoutResult.StemLayout(MULTI_NOTE_TRILL_STEM_TOP_SS, EXTREME_STEM_BOT_SS, 0, true));
        builder.putStemLayout(endNote,
            new LayoutResult.StemLayout(MULTI_NOTE_TRILL_STEM_TOP_SS, EXTREME_STEM_BOT_SS, 0, true));

        var context = new StackingContext(
            List.of(mockColumnAt(anchorNote, START_NOTE_X_SS), mockColumnAt(endNote, END_NOTE_X_SS)),
            line, builder);
        new NoteAttachedStacker(context, new StaffExtents(LINE_WIDTH_SS)).stack();

        var layout = require(builder.build().getDecorationLayout(trill));

        // The anchor's own constraint (script padding) does not beat the staff clamp, so if the
        // trill used script padding for every note it would seat at the clamp. It must instead
        // seat at the end note's larger spanner-padding requirement.
        var expectedInnerEdgeYSs =
            MULTI_NOTE_TRILL_STEM_TOP_SS - NoteAttachedStacker.TRILL_SPANNER_PADDING_SS;
        var expectedYSs = expectedInnerEdgeYSs - trill.getContentHeightSs();

        assertThat(layout.ySs()).isCloseTo(expectedYSs, within(TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // Accent direction: end-to-end placement side.
    //
    // The aligned_side placement values themselves (isolated-clamp vs. real-support-override,
    // above and below) are covered directly against StackingUtils.placeAndReserveClamped in
    // StackingUtilsTest; NoteAttachedStacker no longer has its own wrapper around that method.
    // -------------------------------------------------------------------------

    @Nested
    class StackAgainstNeighborPlacementValues {

        @Test
        void testDownStemNotePlacesAccentAboveTheStaff() {
            var note = stemDownNote(STAFF_CENTER_SP);
            note.addArticulation(new Articulation(note, ArticulationType.ACCENT));
            var accent = note.getArticulations().getFirst();

            var builder = new LayoutResult.Builder();
            var context = contextWith(List.of(mockColumnAt(note, START_NOTE_X_SS)), builder);
            new NoteAttachedStacker(context, new StaffExtents(LINE_WIDTH_SS)).stack();

            var layout = require(builder.build().getDecorationLayout(accent));
            assertThat(layout.ySs())
                .describedAs("accent must place opposite a down stem: above the staff")
                .isLessThan(0.0);
        }

        @Test
        void testUpStemNotePlacesAccentBelowTheStaff() {
            var note = stemUpNote(STAFF_CENTER_SP);
            note.addArticulation(new Articulation(note, ArticulationType.ACCENT));
            var accent = note.getArticulations().getFirst();

            var builder = new LayoutResult.Builder();
            var context = contextWith(List.of(mockColumnAt(note, START_NOTE_X_SS)), builder);
            new NoteAttachedStacker(context, new StaffExtents(LINE_WIDTH_SS)).stack();

            var layout = require(builder.build().getDecorationLayout(accent));
            assertThat(layout.ySs())
                .describedAs("accent must place opposite an up stem: below the staff")
                .isGreaterThan(0.0);
        }
    }

    // -------------------------------------------------------------------------
    // Gap B — decoration-over-decoration placement, each landing its own padding beyond the inner
    // decoration. stack() places Tier 2 decorations fermata-then-trill (the fermata loop over
    // columns runs before stackTrills), so with all three present the reachable pairs are
    // FERMATA x ACCENT (FERMATA_PADDING_SS) and TRILL x FERMATA (TRILL_SCRIPT_PADDING_SS).
    // -------------------------------------------------------------------------

    @Test
    void testFermataClearsAccentAndTrillClearsFermataByNoteDecorationMargin() {
        var note = stemDownNote(STAFF_CENTER_SP);
        note.addArticulation(new Articulation(note, ArticulationType.ACCENT));
        var accent = note.getArticulations().getFirst();

        var fermata = new FermataAttachment(note);
        note.addAttachment(fermata);

        var line = detachedLine();
        line.addElement(note);
        var trill = new Trill(note);
        line.addRangeElement(trill);

        var builder = new LayoutResult.Builder();
        var context = new StackingContext(
            List.of(mockColumnAt(note, START_NOTE_X_SS)), line, builder);
        new NoteAttachedStacker(context, new StaffExtents(LINE_WIDTH_SS)).stack();

        var result = builder.build();
        var accentLayout = require(result.getDecorationLayout(accent));
        var trillLayout = require(result.getDecorationLayout(trill));
        var fermataLayout = require(result.findAttachmentDecorationLayout(note, FermataAttachment.class));

        // FERMATA x ACCENT: fermata's near edge clears accent's outer edge (its real support) by
        // FERMATA_PADDING_SS.
        assertThat(accentLayout.ySs() - (fermataLayout.ySs() + fermataLayout.heightSs()))
            .describedAs("fermata must clear the accent by FERMATA_PADDING_SS")
            .isCloseTo(NoteAttachedStacker.FERMATA_PADDING_SS, within(TOLERANCE));

        // TRILL x FERMATA: a single-note trill seats at the anchor note's script padding,
        // TRILL_SCRIPT_PADDING_SS, since its range has only the one (anchor) note.
        assertThat(fermataLayout.ySs() - (trillLayout.ySs() + trillLayout.heightSs()))
            .describedAs("trill must clear the fermata by TRILL_SCRIPT_PADDING_SS")
            .isCloseTo(NoteAttachedStacker.TRILL_SCRIPT_PADDING_SS, within(TOLERANCE));
    }

    @Test
    void testFermataClearsAccentDirectlyWhenNoTrillPresent() {
        var note = stemDownNote(STAFF_CENTER_SP);
        note.addArticulation(new Articulation(note, ArticulationType.ACCENT));
        var accent = note.getArticulations().getFirst();

        var fermata = new FermataAttachment(note);
        note.addAttachment(fermata);

        var builder = new LayoutResult.Builder();
        var context = contextWith(List.of(mockColumnAt(note, START_NOTE_X_SS)), builder);
        new NoteAttachedStacker(context, new StaffExtents(LINE_WIDTH_SS)).stack();

        var result = builder.build();
        var accentLayout = require(result.getDecorationLayout(accent));
        var fermataLayout = require(result.findAttachmentDecorationLayout(note, FermataAttachment.class));

        // FERMATA x ACCENT (no trill present): fermata clears the accent directly.
        assertThat(accentLayout.ySs() - (fermataLayout.ySs() + fermataLayout.heightSs()))
            .describedAs("fermata must clear the accent by FERMATA_PADDING_SS directly "
                + "when no trill is present")
            .isCloseTo(NoteAttachedStacker.FERMATA_PADDING_SS, within(TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // Gap C (critical) — decoration over staccato must still clear the outer staff line by at
    // least SCRIPT_STAFF_PADDING_SS (the staff clamp is a floor no real reservation can violate):
    // pins the quantization assumption that no staccato lands in the thin under-clear band, and
    // catches any regression to center-seeding the staccato.
    // -------------------------------------------------------------------------

    @Test
    void testAccentOverStaccatoClearsOuterStaffLineByAtLeastStaffPadding() {
        var note = stemDownNote(GAP_BINDS_ABOVE_SP);
        note.addArticulation(new Articulation(note, ArticulationType.STACCATO));
        note.addArticulation(new Articulation(note, ArticulationType.ACCENT));
        var accent = note.getArticulations().get(1);

        var builder = new LayoutResult.Builder();
        var context = contextWith(List.of(mockColumnAt(note, START_NOTE_X_SS)), builder);
        new NoteAttachedStacker(context, new StaffExtents(LINE_WIDTH_SS)).stack();

        var accentLayout = require(builder.build().getDecorationLayout(accent));
        var clearanceFromStaffLineSs = -Staff.STAFF_HALF_SS
            - (accentLayout.ySs() + accentLayout.heightSs());

        assertThat(clearanceFromStaffLineSs)
            .describedAs("accent over a staccato must still clear the outer staff line by at "
                + "least SCRIPT_STAFF_PADDING_SS, not just the staccato's own edge-to-edge margin")
            .isGreaterThanOrEqualTo(NoteAttachedStacker.SCRIPT_STAFF_PADDING_SS - TOLERANCE);
    }

    @Test
    void testFermataOverStaccatoWithNoAccentClearsOuterStaffLineByAtLeastStaffPadding() {
        var note = stemDownNote(GAP_BINDS_ABOVE_SP);
        note.addArticulation(new Articulation(note, ArticulationType.STACCATO));
        var staccato = note.getArticulations().getFirst();

        var fermata = new FermataAttachment(note);
        note.addAttachment(fermata);

        var builder = new LayoutResult.Builder();
        var context = contextWith(List.of(mockColumnAt(note, START_NOTE_X_SS)), builder);
        new NoteAttachedStacker(context, new StaffExtents(LINE_WIDTH_SS)).stack();

        var result = builder.build();
        var fermataLayout =
            require(result.findAttachmentDecorationLayout(note, FermataAttachment.class));
        var clearanceFromStaffLineSs = -Staff.STAFF_HALF_SS
            - (fermataLayout.ySs() + fermataLayout.heightSs());

        assertThat(clearanceFromStaffLineSs)
            .describedAs("a fermata placed over a staccato (no accent) must still clear the "
                + "outer staff line by at least SCRIPT_STAFF_PADDING_SS")
            .isGreaterThanOrEqualTo(NoteAttachedStacker.SCRIPT_STAFF_PADDING_SS - TOLERANCE);

        // A fermata is a note decoration, not an articulation: it must clear the staccato's outer
        // edge by the full FERMATA_PADDING_SS. Above the staff the fermata sits farther out (more
        // negative Y), so the gap is the staccato's top edge minus the fermata's bottom edge.
        var staccatoLayout = require(result.getDecorationLayout(staccato));
        var gapAboveStaccatoSs =
            staccatoLayout.ySs() - (fermataLayout.ySs() + fermataLayout.heightSs());

        assertThat(gapAboveStaccatoSs)
            .describedAs("fermata over a staccato (no accent) clears it by FERMATA_PADDING_SS")
            .isCloseTo(NoteAttachedStacker.FERMATA_PADDING_SS, within(TOLERANCE));
    }

    @Test
    void testNoteWithStaccatoButNoAccentPlacesNoAccentDecoration() {
        // stackAccentAboveExtents returns null on its @Nullable no-accent contract and must place
        // nothing: a note carrying only a staccato yields exactly one decoration — the staccato.
        var note = stemDownNote(STAFF_CENTER_SP);
        note.addArticulation(new Articulation(note, ArticulationType.STACCATO));
        var staccato = note.getArticulations().getFirst();

        var builder = new LayoutResult.Builder();
        var context = contextWith(List.of(mockColumnAt(note, START_NOTE_X_SS)), builder);
        new NoteAttachedStacker(context, new StaffExtents(LINE_WIDTH_SS)).stack();

        assertThat(builder.build().getDecorationLayouts())
            .describedAs("only the staccato is placed; no accent decoration when the note has none")
            .containsOnlyKeys(staccato);
    }

    // -------------------------------------------------------------------------
    // Flipped/mixed stems — a down-stem note (accent above) and an up-stem note (accent below)
    // share one upward arc. The arc is seeded on the above side only, so it pushes the above accent
    // outward (that accent takes the tie as a side-support element) while the below accent, on the
    // opposite side, never sees it and stays note-relative.
    // -------------------------------------------------------------------------

    @Test
    void testMixedStemSharedArcPushesTheAboveAccentButNotTheBelowAccent() {
        // Down-stem note: accent placed above. Up-stem note: accent placed below.
        var startNote = stemDownNote(STAFF_CENTER_SP);
        startNote.addArticulation(new Articulation(startNote, ArticulationType.ACCENT));
        var aboveAccent = startNote.getArticulations().getFirst();

        var endNote = stemUpNote(STAFF_CENTER_SP);
        endNote.addArticulation(new Articulation(endNote, ArticulationType.ACCENT));
        var belowAccent = endNote.getArticulations().getFirst();

        var line = detachedLine();
        var tie = new Tie(startNote, endNote);
        line.addRangeElement(tie);

        var builder = new LayoutResult.Builder();
        // Upward arc protruding past the above accent's note-relative position, so that accent must
        // clear it.
        builder.putTieLayout(tie, flatTieLayout(START_NOTE_X_SS, END_NOTE_X_SS, PROTRUDING_ARC_Y_SS));

        var context = new StackingContext(
            List.of(mockColumnAt(startNote, START_NOTE_X_SS), mockColumnAt(endNote, END_NOTE_X_SS)),
            line, builder);
        new NoteAttachedStacker(context, new StaffExtents(LINE_WIDTH_SS)).stack();

        var result = builder.build();
        var aboveAccentLayout = require(result.getDecorationLayout(aboveAccent));
        var belowAccentLayout = require(result.getDecorationLayout(belowAccent));

        // Above accent clears the protruding arc by one articulation margin — measured against its
        // wedge, not its box. The arc begins at the note's column X, which is right of the accent's
        // left edge (the accent is wider than the notehead it is centred on), so even widened by
        // SCRIPT_HORIZON_PADDING_SS the arc never reaches the wedge's zero-offset cap. Over the arc,
        // the accent's edge has already climbed away from its bounding box by this much.
        var arcNearEdgeSs = START_NOTE_X_SS - StackingUtils.SCRIPT_HORIZON_PADDING_SS;
        var wedgeOffsetSs = ShapeProfile.innerEdge(AccentShape.accent(), true)
            .offsetSs(arcNearEdgeSs - aboveAccentLayout.xSs());
        var expectedAboveYSs = PROTRUDING_ARC_Y_SS + wedgeOffsetSs
            - NoteAttachedStacker.ACCENT_PADDING_SS - aboveAccentLayout.heightSs();
        assertThat(aboveAccentLayout.ySs())
            .describedAs("above accent must be pushed outward to clear the protruding arc")
            .isCloseTo(expectedAboveYSs, within(TOLERANCE));

        // Below accent anchors to the staff-padding clamp; the above-only arc never affects it.
        var expectedBelowYSs = StackingUtils.anchorFloorSs(STAFF_CENTER_SP)
            + NoteAttachedStacker.SCRIPT_STAFF_PADDING_SS;
        assertThat(belowAccentLayout.ySs())
            .describedAs("below accent must anchor to the notehead/staff floor, never seeing the arc")
            .isCloseTo(expectedBelowYSs, within(TOLERANCE));
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
