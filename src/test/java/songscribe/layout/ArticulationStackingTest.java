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

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.shape.AccentShape;
import songscribe.dom.Articulation;
import songscribe.font.DocumentFonts;
import songscribe.dom.ArticulationType;
import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.layout.stacking.NoteAttachedStacker;
import songscribe.layout.stacking.StackingUtils;
import songscribe.layout.stacking.VerticalStackingCalculator;

@SuppressWarnings("DataFlowIssue")
class ArticulationStackingTest extends UnitTest {

    private static final double LINE_WIDTH_SS = 64.0;
    private static final double TOLERANCE = 0.001;
    private static final double NOTE_X_SS = 10.0;

    // A within-staff position whose note stems up, so its articulations stack below the staff.
    private static final int UP_STEM_STAFF_POSITION = 2;

    // D5/G4: far enough from the middle line that accent's natural gap-relative position (see
    // ACCENT_STACCATO_GAP_SS) is more extreme than the staff-edge clamp, so the gap constant is
    // the binding constraint on where accent lands beyond staccato.
    private static final int GAP_BINDS_ABOVE_STAFF_POSITION = -2;
    private static final int GAP_BINDS_BELOW_STAFF_POSITION = 2;

    // C5/A4: one position closer to the middle line than GAP_BINDS_*, where staccato itself
    // still sits within the staff, so accent's natural gap-relative position falls short of the
    // staff-edge clamp — the clamp (not the gap constant) is the binding constraint.
    private static final int CLAMP_BINDS_ABOVE_STAFF_POSITION = GAP_BINDS_ABOVE_STAFF_POSITION + 1;
    private static final int CLAMP_BINDS_BELOW_STAFF_POSITION = GAP_BINDS_BELOW_STAFF_POSITION - 1;

    // A position well below the staff whose note stems up. Here the anchored floor (the notehead
    // bottom) sits below StaffExtents' default below-content reservation, so the anchored floor is
    // the binding constraint and the placed top-Y is exactly anchorFloorSs + ARTICULATION_MARGIN_SS.
    private static final int BELOW_STAFF_STAFF_POSITION = 8;

    // Expected top-Y of a single below-staff articulation for an up-stem note placed below the
    // staff: the anchored floor plus the articulation margin.
    private static final double EXPECTED_BELOW_TOP_Y_SS =
        StackingUtils.anchorFloorSs(BELOW_STAFF_STAFF_POSITION) + NoteAttachedStacker.ARTICULATION_MARGIN_SS;

    // An explicit stem bottom for the preview/full-layout parity test: distinct from what the
    // non-beamed computeNoteBounds fallback would produce for a bare notehead at
    // UP_STEM_STAFF_POSITION (so the full-layout path's seeding genuinely diverges from the
    // preview path's), but still short of the staccato's ideal center position minus margin, so
    // it does not flip which placement branch wins in either path.
    private static final double EXPLICIT_STEM_BOTTOM_SS = 1.8;
    private static final double EXPLICIT_STEM_TOP_SS = -1.0;

    /** Asserts value is not null and returns it non-null for NullAway. */
    @SuppressWarnings("NullAway")
    private static <T> T require(@Nullable T value, String description) {
        assertThat(value).describedAs(description).isNotNull();
        return value;
    }

    /**
     * Creates a note with the given staff position, stem direction, and articulations.
     * Upper=true means stem up (note below middle), upper=false means stem down (note above middle).
     */
    private static StaffElement createNote(int staffPosition, boolean upper, ArticulationType... types) {
        var note = ElementType.CROTCHET.newInstance();
        note.setStaffPosition(staffPosition);
        note.setUpper(upper);

        for (var type : types) {
            note.addArticulation(new Articulation(note, type));
        }

        return note;
    }

    /**
     * Creates an ElementColumn for a note at the standard test X position.
     */
    private static ElementColumn columnFor(StaffElement note) {
        var column = new ElementColumn(
            note, List.of(), 0.0, 1.0, -1.5, 2.0, null, 0.0, false
        );
        column.setXSs(NOTE_X_SS);
        return column;
    }

    /**
     * Runs vertical stacking on the given columns and returns the built LayoutResult.
     */
    private static LayoutResult stackColumns(List<ElementColumn> columns) {
        var builder = new LayoutResult.Builder();
        var calculator = new VerticalStackingCalculator();
        calculator.calculate(columns, detachedLine(), builder, LINE_WIDTH_SS, DocumentFonts.defaultFonts());
        return builder.build();
    }

    /**
     * Runs vertical stacking on a single column and returns the built LayoutResult.
     */
    private static LayoutResult stackSingleColumn(StaffElement note) {
        return stackColumns(List.of(columnFor(note)));
    }

    /**
     * Asserts that staccato and accent are stacked as two separate glyphs above the staff
     * (staccato closest to the note, accent beyond it), with accent positioned at whichever is
     * further from the staff: {@link NoteAttachedStacker#ACCENT_STACCATO_GAP_SS} beyond
     * staccato, or {@link NoteAttachedStacker#ARTICULATION_MARGIN_SS} above the top staff line —
     * mirroring {@link StackingUtils#stackBeyond}.
     */
    private static void assertSeparateGlyphsAboveStaff(StaffElement note, LayoutResult result) {
        var staccatoArticulation = note.getArticulations().get(0);
        var accentArticulation = note.getArticulations().get(1);

        var staccatoLayout = require(
            result.getDecorationLayout(staccatoArticulation), "staccato DecorationLayout");
        var accentLayout = require(
            result.getDecorationLayout(accentArticulation), "accent DecorationLayout");

        var naturalTopYSs = staccatoLayout.ySs()
            - NoteAttachedStacker.ACCENT_STACCATO_GAP_SS - accentLayout.heightSs();
        var staffMinimumTopYSs = StackingUtils.anchorCeilingSs(0)
            - NoteAttachedStacker.ARTICULATION_MARGIN_SS - accentLayout.heightSs();
        var expectedAccentYSs = Math.min(naturalTopYSs, staffMinimumTopYSs);

        assertThat(staccatoLayout.ySs()).isLessThan(0.0);
        assertThat(accentLayout.ySs()).isLessThan(staccatoLayout.ySs());
        assertThat(accentLayout.ySs()).isCloseTo(expectedAccentYSs, within(TOLERANCE));
    }

    /**
     * Below-staff mirror of {@link #assertSeparateGlyphsAboveStaff}.
     */
    private static void assertSeparateGlyphsBelowStaff(StaffElement note, LayoutResult result) {
        var staccatoArticulation = note.getArticulations().get(0);
        var accentArticulation = note.getArticulations().get(1);

        var staccatoLayout = require(
            result.getDecorationLayout(staccatoArticulation), "staccato DecorationLayout");
        var accentLayout = require(
            result.getDecorationLayout(accentArticulation), "accent DecorationLayout");

        var naturalTopYSs = staccatoLayout.ySs() + staccatoLayout.heightSs()
            + NoteAttachedStacker.ACCENT_STACCATO_GAP_SS;
        var staffMinimumTopYSs = StackingUtils.anchorFloorSs(0)
            + NoteAttachedStacker.ARTICULATION_MARGIN_SS;
        var expectedAccentYSs = Math.max(naturalTopYSs, staffMinimumTopYSs);

        assertThat(staccatoLayout.ySs()).isGreaterThan(0.0);
        assertThat(accentLayout.ySs()).isGreaterThan(staccatoLayout.ySs());
        assertThat(accentLayout.ySs()).isCloseTo(expectedAccentYSs, within(TOLERANCE));
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class StemDownArticulationsAbove {

        @Test
        void testStaccatoPositionedAboveNoteExtents() {
            var note = createNote(-2, false, ArticulationType.STACCATO);
            var result = stackSingleColumn(note);
            var layout = require(
                result.getDecorationLayout(note.getArticulations().getFirst()),
                "staccato DecorationLayout");

            // Within-staff center placement: the dot's center sits staccatoAnchorCeilingSs(sp)
            // from the note (clearing the staff line), not edge-anchored to a fixed staff line.
            // The note's own seeded extent (~-1.5 ss) is well short of this ideal position
            // (~-2.5 ss center), so collision never binds here — verified analytically via
            // notehead geometry (radius 0.5 ss) and confirmed by this test passing.
            var expectedYSs = StackingUtils.staccatoAnchorCeilingSs(-2) - layout.heightSs() / 2.0;
            assertThat(layout.ySs()).isCloseTo(expectedYSs, within(TOLERANCE));
            assertThat(layout.xSs()).isCloseTo(NOTE_X_SS, within(TOLERANCE));
            assertThat(layout.heightSs()).isGreaterThan(0.0);
            assertThat(layout.widthSs()).isGreaterThan(0.0);
        }

        @Test
        void testAccentPositionedAboveNoteExtents() {
            var note = createNote(-2, false, ArticulationType.ACCENT);
            var result = stackSingleColumn(note);
            var layout = require(
                result.getDecorationLayout(note.getArticulations().getFirst()),
                "accent DecorationLayout");

            // Within the staff, accent uses the fixed staff-line anchor (not a note-relative
            // center), and the note's own seeded extent (~-1.5 ss) is well short of the anchor
            // (-2.0 ss), so the anchor — not the note's own reservation — is the binding
            // constraint here.
            var expectedYSs = StackingUtils.anchorCeilingSs(-2)
                - NoteAttachedStacker.ARTICULATION_MARGIN_SS - layout.heightSs();
            assertThat(layout.ySs()).isCloseTo(expectedYSs, within(TOLERANCE));
        }

        @Test
        void testAccentStacksBeyondStaccatoAtStaffEdgeClampNearMiddleLine() {
            // C5 (1 position from the middle line): staccato itself stays within the staff, so
            // accent's natural gap-relative position falls short of the staff-edge clamp — the
            // clamp determines accent's position here, not the gap constant.
            var note = createNote(
                CLAMP_BINDS_ABOVE_STAFF_POSITION, false, ArticulationType.STACCATO, ArticulationType.ACCENT);
            var result = stackSingleColumn(note);
            assertSeparateGlyphsAboveStaff(note, result);
        }

        @Test
        void testAccentStacksBeyondStaccatoWithGapFarFromMiddleLine() {
            // D5: far enough from the middle line that accent's natural gap-relative position
            // (ACCENT_STACCATO_GAP_SS beyond staccato) determines its placement, not the clamp.
            var note = createNote(
                GAP_BINDS_ABOVE_STAFF_POSITION, false, ArticulationType.STACCATO, ArticulationType.ACCENT);
            var result = stackSingleColumn(note);
            assertSeparateGlyphsAboveStaff(note, result);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class StemUpArticulationsBelow {

        @Test
        void testStaccatoPositionedBelowNoteExtents() {
            // Staff position 2 = within staff, stems up
            var note = createNote(UP_STEM_STAFF_POSITION, true, ArticulationType.STACCATO);
            var result = stackSingleColumn(note);
            var layout = require(
                result.getDecorationLayout(note.getArticulations().getFirst()),
                "staccato DecorationLayout");

            // Within-staff center placement: the dot's center sits staccatoAnchorFloorSs(sp)
            // from the note. The note's own seeded extent (~1.5 ss) is well short of this ideal
            // position (~2.5 ss center), so collision never binds here — verified analytically
            // via notehead geometry (radius 0.5 ss) and confirmed by this test passing.
            var expectedYSs = StackingUtils.staccatoAnchorFloorSs(UP_STEM_STAFF_POSITION)
                - layout.heightSs() / 2.0;
            assertThat(layout.ySs()).isCloseTo(expectedYSs, within(TOLERANCE));
            assertThat(layout.xSs()).isCloseTo(NOTE_X_SS, within(TOLERANCE));
            assertThat(layout.heightSs()).isGreaterThan(0.0);
            assertThat(layout.widthSs()).isGreaterThan(0.0);
        }

        @Test
        void testAccentPositionedBelowNoteExtents() {
            var note = createNote(UP_STEM_STAFF_POSITION, true, ArticulationType.ACCENT);
            var result = stackSingleColumn(note);
            var layout = require(
                result.getDecorationLayout(note.getArticulations().getFirst()),
                "accent DecorationLayout");

            // Within the staff, accent uses the fixed staff-line anchor (not a note-relative
            // center), and the note's own seeded extent (~1.5 ss) is well short of the anchor
            // (2.0 ss), so the anchor — not the note's own reservation — is the binding
            // constraint here.
            var expectedYSs = StackingUtils.anchorFloorSs(UP_STEM_STAFF_POSITION)
                + NoteAttachedStacker.ARTICULATION_MARGIN_SS;
            assertThat(layout.ySs()).isCloseTo(expectedYSs, within(TOLERANCE));
        }

        @Test
        void testAccentStacksBeyondStaccatoAtStaffEdgeClampNearMiddleLine() {
            // A4 (1 position from the middle line): staccato itself stays within the staff, so
            // accent's natural gap-relative position falls short of the staff-edge clamp — the
            // clamp determines accent's position here, not the gap constant.
            var note = createNote(
                CLAMP_BINDS_BELOW_STAFF_POSITION, true, ArticulationType.STACCATO, ArticulationType.ACCENT);
            var result = stackSingleColumn(note);
            assertSeparateGlyphsBelowStaff(note, result);
        }

        @Test
        void testAccentStacksBeyondStaccatoWithGapFarFromMiddleLine() {
            // G4: far enough from the middle line that accent's natural gap-relative position
            // (ACCENT_STACCATO_GAP_SS beyond staccato) determines its placement, not the clamp.
            var note = createNote(
                GAP_BINDS_BELOW_STAFF_POSITION, true, ArticulationType.STACCATO, ArticulationType.ACCENT);
            var result = stackSingleColumn(note);
            assertSeparateGlyphsBelowStaff(note, result);
        }

        @Test
        void testBelowStaffArticulationSitsAtAnchoredFloorPlusMargin() {
            // A note well below the staff: the anchored floor (its notehead bottom) is the binding
            // constraint, so the placed top-Y is exactly anchorFloorSs + ARTICULATION_MARGIN_SS.
            var note = createNote(BELOW_STAFF_STAFF_POSITION, true, ArticulationType.STACCATO);
            var result = stackSingleColumn(note);
            var layout = require(
                result.getDecorationLayout(note.getArticulations().getFirst()),
                "staccato DecorationLayout");

            assertThat(layout.ySs()).isGreaterThan(0.0);
            assertThat(layout.ySs()).isCloseTo(EXPECTED_BELOW_TOP_Y_SS, within(TOLERANCE));
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class CollisionDetection {

        @Test
        void testArticulationsDoNotCollideWithStemTips() {
            // Stem-down note: stem tip is above the notehead (seeded into extents)
            var note = createNote(-2, false, ArticulationType.STACCATO);
            var column = columnFor(note);

            // Create builder with a StemLayout that extends above the note
            var builder = new LayoutResult.Builder();
            var stemTopSs = -3.5;
            builder.putStemLayout(note,
                new LayoutResult.StemLayout(stemTopSs, 0.0, 0.0, false));

            var calculator = new VerticalStackingCalculator();
            calculator.calculate(List.of(column), detachedLine(), builder, LINE_WIDTH_SS, DocumentFonts.defaultFonts());
            var result = builder.build();

            var layout = require(
                result.getDecorationLayout(note.getArticulations().getFirst()),
                "staccato DecorationLayout");

            // Staccato top should be above the stem top (more negative)
            assertThat(layout.ySs()).isLessThan(stemTopSs);
        }

        @Test
        void testAboveStaffArticulationsReserveSpaceInExtents() {
            // Create two notes at the same X position with stem-down articulations
            var note1 = createNote(-2, false, ArticulationType.STACCATO);
            var note2 = createNote(-2, false, ArticulationType.STACCATO);

            var col1 = columnFor(note1);
            var col2 = columnFor(note2);
            // Same X position to force collision detection
            col2.setXSs(NOTE_X_SS);

            var result = stackColumns(List.of(col1, col2));

            var layout1 = require(
                result.getDecorationLayout(note1.getArticulations().getFirst()),
                "note1 staccato DecorationLayout");
            var layout2 = require(
                result.getDecorationLayout(note2.getArticulations().getFirst()),
                "note2 staccato DecorationLayout");

            // Second staccato should be above the first (higher layers stack)
            // because the first staccato reserves space via ySet
            assertThat(layout2.ySs()).isLessThan(layout1.ySs());
        }

        @Test
        void testBelowStaffArticulationsReserveSpaceInExtents() {
            // Two up-stem notes at the same X position with staccato articulations:
            // the first staccato reserves space below the staff via stackBelow's ySet(false, …),
            // so the second must stack further below (greater Y).
            var note1 = createNote(UP_STEM_STAFF_POSITION, true, ArticulationType.STACCATO);
            var note2 = createNote(UP_STEM_STAFF_POSITION, true, ArticulationType.STACCATO);

            var col1 = columnFor(note1);
            var col2 = columnFor(note2);
            // Same X position to force collision detection
            col2.setXSs(NOTE_X_SS);

            var result = stackColumns(List.of(col1, col2));

            var layout1 = require(
                result.getDecorationLayout(note1.getArticulations().getFirst()),
                "note1 staccato DecorationLayout");
            var layout2 = require(
                result.getDecorationLayout(note2.getArticulations().getFirst()),
                "note2 staccato DecorationLayout");

            // Both are below the staff, and the second stacks further below the first
            // because the first staccato reserves space via ySet(false, …).
            assertThat(layout1.ySs()).isGreaterThan(0.0);
            assertThat(layout2.ySs()).isGreaterThan(layout1.ySs());
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class IndividualGlyphDimensions {

        @Test
        void testSingleStaccatoUsesIndividualGlyphDimensions() {
            var note = createNote(-2, false, ArticulationType.STACCATO);
            var result = stackSingleColumn(note);
            var staccatoLayout = require(
                result.getDecorationLayout(note.getArticulations().getFirst()),
                "staccato DecorationLayout");

            var individualBBox = SMuFLMetadata.requireBBox(SMuFLGlyph.ARTIC_STACCATO_ABOVE);

            assertThat(staccatoLayout.widthSs())
                .isCloseTo(individualBBox.width(), within(TOLERANCE));
            assertThat(staccatoLayout.heightSs())
                .isCloseTo(individualBBox.height(), within(TOLERANCE));
        }

        @Test
        void testSingleAccentUsesIndividualGlyphDimensions() {
            var note = createNote(-2, false, ArticulationType.ACCENT);
            var result = stackSingleColumn(note);
            var accentLayout = require(
                result.getDecorationLayout(note.getArticulations().getFirst()),
                "accent DecorationLayout");

            var accentBounds = AccentShape.accent().getBounds2D();

            assertThat(accentLayout.widthSs())
                .isCloseTo(accentBounds.getWidth(), within(TOLERANCE));
            assertThat(accentLayout.heightSs())
                .isCloseTo(accentBounds.getHeight(), within(TOLERANCE));
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class DecorationLayoutProperties {

        @Test
        void testDecorationLayoutHasCorrectXPosition() {
            var note = createNote(0, false, ArticulationType.STACCATO);
            var result = stackSingleColumn(note);
            var layout = require(
                result.getDecorationLayout(note.getArticulations().getFirst()),
                "staccato DecorationLayout");

            assertThat(layout.xSs()).isCloseTo(NOTE_X_SS, within(TOLERANCE));
        }

        @Test
        void testDecorationLayoutHasPositiveDimensions() {
            var note = createNote(0, false, ArticulationType.ACCENT);
            var result = stackSingleColumn(note);
            var layout = require(
                result.getDecorationLayout(note.getArticulations().getFirst()),
                "accent DecorationLayout");

            assertThat(layout.widthSs()).isGreaterThan(0.0);
            assertThat(layout.heightSs()).isGreaterThan(0.0);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class BelowStaffLyricExtent {

        @Test
        void testBelowStaffArticulationsIncreaseBelowContentExtent() {
            // The below-content extent is the lyric-baseline anchor. Adding articulations to an
            // up-stem note places them below the staff and must push that extent further down,
            // so lyrics clear the articulations.
            var noteWithout = createNote(UP_STEM_STAFF_POSITION, true);
            var belowContentWithoutSs = stackSingleColumn(noteWithout).getBelowContentSs();

            var noteWith = createNote(UP_STEM_STAFF_POSITION, true, ArticulationType.STACCATO);
            var belowContentWithSs = stackSingleColumn(noteWith).getBelowContentSs();

            assertThat(belowContentWithSs).isGreaterThan(belowContentWithoutSs);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class PreviewPathParity {

        @Test
        void testBelowStaffPreviewMatchesFullLayout() {
            // The insertion-note preview path and the full-layout path branch independently on
            // stem direction. For an up-stem note both must place the staccato below the staff
            // (positive Y) with identical dimensions and Y.
            var previewNote = createNote(UP_STEM_STAFF_POSITION, true, ArticulationType.STACCATO);
            var previewResult =
                NoteAttachedStacker.computePreviewDecorationLayouts(previewNote, NOTE_X_SS);
            var previewLayout = require(
                previewResult.getDecorationLayout(previewNote.getArticulations().getFirst()),
                "preview staccato DecorationLayout");

            var fullNote = createNote(UP_STEM_STAFF_POSITION, true, ArticulationType.STACCATO);
            var fullLayout = require(
                stackSingleColumn(fullNote).getDecorationLayout(
                    fullNote.getArticulations().getFirst()),
                "full-layout staccato DecorationLayout");

            assertThat(previewLayout.ySs()).isGreaterThan(0.0);
            assertThat(previewLayout.ySs()).isCloseTo(fullLayout.ySs(), within(TOLERANCE));
            assertThat(previewLayout.widthSs()).isCloseTo(fullLayout.widthSs(), within(TOLERANCE));
            assertThat(previewLayout.heightSs()).isCloseTo(fullLayout.heightSs(), within(TOLERANCE));
        }

        @Test
        void testBelowStaffPreviewMatchesFullLayoutWithDivergentSeeding() {
            // The preview path always seeds note bounds via the non-beamed computeNoteBounds
            // fallback (no StemLayout is available for a note being inserted). The full-layout
            // path instead seeds from an explicit StemLayout when the builder has one (computed
            // during the beam/stem pass) — a genuinely different bound (EXPLICIT_STEM_BOTTOM_SS)
            // than what computeNoteBounds alone would produce. Verify the two paths still agree
            // on the staccato's final Y when seeded from different sources, as long as neither
            // triggers the collision branch (both stay on the ideal center-placement path).
            var previewNote = createNote(UP_STEM_STAFF_POSITION, true, ArticulationType.STACCATO);
            var previewResult =
                NoteAttachedStacker.computePreviewDecorationLayouts(previewNote, NOTE_X_SS);
            var previewLayout = require(
                previewResult.getDecorationLayout(previewNote.getArticulations().getFirst()),
                "preview staccato DecorationLayout");

            var fullNote = createNote(UP_STEM_STAFF_POSITION, true, ArticulationType.STACCATO);
            var column = columnFor(fullNote);
            var builder = new LayoutResult.Builder();
            builder.putStemLayout(fullNote,
                new LayoutResult.StemLayout(EXPLICIT_STEM_TOP_SS, EXPLICIT_STEM_BOTTOM_SS, 0.0, false));

            var calculator = new VerticalStackingCalculator();
            calculator.calculate(List.of(column), detachedLine(), builder, LINE_WIDTH_SS, DocumentFonts.defaultFonts());
            var fullLayout = require(
                builder.build().getDecorationLayout(fullNote.getArticulations().getFirst()),
                "full-layout staccato DecorationLayout");

            assertThat(previewLayout.ySs()).isCloseTo(fullLayout.ySs(), within(TOLERANCE));
        }
    }
}
