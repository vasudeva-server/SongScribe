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
import static songscribe.dom.StaffElementFactory.createNote;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Crescendo;
import songscribe.dom.Diminuendo;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.FermataAttachment;
import songscribe.dom.Beam;
import songscribe.dom.Tuplet;
import songscribe.font.DocumentFonts;

import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.engraving.Staff;
import songscribe.layout.stacking.NoteAttachedStacker;
import songscribe.layout.stacking.StackingContext;
import songscribe.layout.stacking.StackingUtils;
import songscribe.layout.stacking.StructuralStacker;
import songscribe.layout.stacking.VerticalStackingCalculator;
import songscribe.layout.NoteGeometry;

class StructuralTierStackingTest extends UnitTest {

    private static final double LINE_WIDTH_SS = 64.0;
    private static final double TOLERANCE = 0.001;
    private static final double NOTE1_X_SS = 10.0;
    private static final double NOTE2_X_SS = 30.0;
    private static final double NOTE3_X_SS = 50.0;

    @SuppressWarnings("NullAway")
    private static <T> T require(@Nullable T value, String description) {
        assertThat(value).describedAs(description).isNotNull();
        return value;
    }

    private static ElementColumn columnFor(StaffElement note, double xSs) {
        var column = new ElementColumn(
            note, List.of(), 0.0, 1.0, -1.5, 2.0, null, 0.0, false
        );
        column.setXSs(xSs);
        return column;
    }

    private static LayoutResult stackColumns(List<ElementColumn> columns, Line line) {
        var builder = new LayoutResult.Builder();
        var calculator = new VerticalStackingCalculator();
        calculator.calculate(columns, line, builder, LINE_WIDTH_SS, DocumentFonts.defaultFonts());
        return builder.build();
    }

    /**
     * Runs only the structural stacker against fresh (zero-top) extents.
     * Use when you need exact Y control — no note-attached layer seeding occurs.
     */
    private static LayoutResult stackDirectly(List<ElementColumn> columns, Line line) {
        var builder = new LayoutResult.Builder();
        var context = new StackingContext(columns, line, builder);
        new StructuralStacker(context, new StaffExtents(LINE_WIDTH_SS)).stack();
        return builder.build();
    }

    @SuppressWarnings({ "PackageVisibleInnerClass", "DataFlowIssue" })
    @Nested
    class HairpinStacking {

        @Test
        void testCrescendoPositionedAboveNoteAttachedLayer() {
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);

            var crescendo = new Crescendo(note1, note2);
            line.addRangeElement(crescendo);

            var result = stackColumns(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)),
                line);

            var layout = require(
                result.getDecorationLayout(crescendo),
                "crescendo DecorationLayout");

            // Hairpin should be above the staff (negative Y = higher)
            assertThat(layout.ySs()).isLessThan(0.0);
        }

        @Test
        void testDiminuendoPositionedAboveNoteAttachedLayer() {
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);

            var diminuendo = new Diminuendo(note1, note2);
            line.addRangeElement(diminuendo);

            var result = stackColumns(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)),
                line);

            var layout = require(
                result.getDecorationLayout(diminuendo),
                "diminuendo DecorationLayout");

            assertThat(layout.ySs()).isLessThan(0.0);
        }

        @Test
        void testHairpinHasPositiveDimensions() {
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);

            var crescendo = new Crescendo(note1, note2);
            line.addRangeElement(crescendo);

            var result = stackColumns(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)),
                line);

            var layout = require(
                result.getDecorationLayout(crescendo),
                "crescendo DecorationLayout");

            assertThat(layout.widthSs()).isGreaterThan(0.0);
            assertThat(layout.heightSs()).isGreaterThan(0.0);
        }

        @Test
        void testHairpinPositionedAboveNoteDecorationsWhenPresent() {
            var note1 = createNote(-2, false);
            note1.addAttachment(new FermataAttachment(note1));
            var note2 = createNote(0, false);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);

            var crescendo = new Crescendo(note1, note2);
            line.addRangeElement(crescendo);

            var result = stackColumns(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)),
                line);

            var fermataLayout = require(
                result.findAttachmentDecorationLayout(note1, FermataAttachment.class),
                "fermata DecorationLayout");
            var hairpinLayout = require(
                result.getDecorationLayout(crescendo),
                "crescendo DecorationLayout");

            // Hairpin should be above fermata (more negative Y)
            assertThat(hairpinLayout.ySs()).isLessThan(fermataLayout.ySs());
        }

        @Test
        void testAdjacentHairpinsStackDiminuendoAboveCrescendo() {
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);
            var note3 = createNote(0, false);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);
            line.addElement(note3);

            // Adjacent (musically non-overlapping) hairpins: crescendo note1→note2,
            // diminuendo note2→note3. The crescendo's span extends NOTE_HEAD_WIDTH_SS
            // past note2's anchor; combined with the query margin, the diminuendo query
            // overlaps the crescendo's reservation, so the diminuendo stacks above it.
            var crescendo = new Crescendo(note1, note2);
            var diminuendo = new Diminuendo(note2, note3);
            line.addRangeElement(crescendo);
            line.addRangeElement(diminuendo);

            var result = stackColumns(
                List.of(
                    columnFor(note1, NOTE1_X_SS),
                    columnFor(note2, NOTE2_X_SS),
                    columnFor(note3, NOTE3_X_SS)),
                line);

            var crescLayout = require(
                result.getDecorationLayout(crescendo),
                "crescendo DecorationLayout");
            var dimLayout = require(
                result.getDecorationLayout(diminuendo),
                "diminuendo DecorationLayout");

            assertThat(crescLayout.ySs()).isLessThan(0.0);
            assertThat(dimLayout.ySs()).isLessThan(0.0);
            // Diminuendo stacks above (more negative Y) because its query region
            // overlaps the crescendo's reservation.
            assertThat(dimLayout.ySs()).isLessThan(crescLayout.ySs());
        }

        @Test
        void testHairpinYEqualsAnchorCeilingMinusMarginMinusHeight() {
            // Fresh structural extents (top=0.0), sp=0 → ceilingSs = anchorCeilingSs(0).
            // Single crescendo: no prior reservation → ceiling is just the anchor ceiling.
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);

            var crescendo = new Crescendo(note1, note2);
            line.addRangeElement(crescendo);

            var result = stackDirectly(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)), line);

            var ceilingSs = StackingUtils.anchorCeilingSs(0);
            var expectedYSs = ceilingSs - StructuralStacker.HAIRPIN_MARGIN_SS
                - crescendo.getContentHeightSs();
            var layout = require(result.getDecorationLayout(crescendo), "crescendo layout");
            assertThat(layout.ySs()).isCloseTo(expectedYSs, within(TOLERANCE));
        }

        @Test
        void testDiminuendoYEqualsAnchorCeilingMinusMarginMinusHeight() {
            // Diminuendo follows the same stackSpanElement path as crescendo.
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);

            var diminuendo = new Diminuendo(note1, note2);
            line.addRangeElement(diminuendo);

            var result = stackDirectly(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)), line);

            var ceilingSs = StackingUtils.anchorCeilingSs(0);
            var expectedYSs = ceilingSs - StructuralStacker.HAIRPIN_MARGIN_SS
                - diminuendo.getContentHeightSs();
            var layout = require(result.getDecorationLayout(diminuendo), "diminuendo layout");
            assertThat(layout.ySs()).isCloseTo(expectedYSs, within(TOLERANCE));
        }
    }

    @SuppressWarnings({ "PackageVisibleInnerClass", "DataFlowIssue" })
    @Nested
    class TupletStacking {

        @Test
        void testTupletRangeElementPositionedAboveStaff() {
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);

            var tuplet = new Tuplet(note1, note2, 3);
            line.addRangeElement(tuplet);

            var result = stackColumns(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)),
                line);

            var layout = require(
                result.getDecorationLayout(tuplet),
                "tuplet DecorationLayout");

            assertThat(layout.ySs()).isLessThan(0.0);
        }

        @Test
        void testTupletHasPositiveDimensions() {
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);

            var tuplet = new Tuplet(note1, note2, 3);
            line.addRangeElement(tuplet);

            var result = stackColumns(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)),
                line);

            var layout = require(
                result.getDecorationLayout(tuplet),
                "tuplet DecorationLayout");

            assertThat(layout.widthSs()).isGreaterThan(0.0);
            assertThat(layout.heightSs()).isGreaterThan(0.0);
        }

        @Test
        void testTupletPositionedAboveNoteDecorationsWhenPresent() {
            var note1 = createNote(-2, false);
            note1.addAttachment(new FermataAttachment(note1));
            var note2 = createNote(0, false);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);

            var tuplet = new Tuplet(note1, note2, 3);
            line.addRangeElement(tuplet);

            var result = stackColumns(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)),
                line);

            var fermataLayout = require(
                result.findAttachmentDecorationLayout(note1, FermataAttachment.class),
                "fermata DecorationLayout");
            var tupletLayout = require(
                result.getDecorationLayout(tuplet),
                "tuplet DecorationLayout");

            // Tuplet should be above fermata (more negative Y)
            assertThat(tupletLayout.ySs()).isLessThan(fermataLayout.ySs());
        }

        @Test
        void testTupletYEqualsAnchorCeilingMinusMarginMinusHeight() {
            // Fresh extents (top=0.0), sp=0 → ceilingSs = anchorCeilingSs(0).
            // Tuplets are stacked before hairpins, so no prior structural reservation.
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);

            var tuplet = new Tuplet(note1, note2, 3);
            line.addRangeElement(tuplet);

            var result = stackDirectly(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)), line);

            var ceilingSs = StackingUtils.anchorCeilingSs(0);
            var expectedYSs = ceilingSs - StructuralStacker.TUPLET_MARGIN_SS
                - tuplet.getContentHeightSs();
            var layout = require(result.getDecorationLayout(tuplet), "tuplet layout");
            assertThat(layout.ySs()).isCloseTo(expectedYSs, within(TOLERANCE));
        }

        @Test
        void testBracketedTupletReservesBracketedHeight() {
            // Non-beamed tuplet → bracketed. Y placement is covered by
            // testTupletYEqualsAnchorCeilingMinusMarginMinusHeight; this asserts the stacker
            // selected the bracketed height and not the number-only height.
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);

            var tuplet = new Tuplet(note1, note2, 3);
            line.addRangeElement(tuplet);

            var result = stackDirectly(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)), line);

            var layout = require(result.getDecorationLayout(tuplet), "tuplet layout");
            assertThat(layout.heightSs()).isCloseTo(Tuplet.bracketedHeightSs(), within(TOLERANCE));
            assertThat(layout.heightSs()).isNotCloseTo(Tuplet.numberOnlyHeightSs(), within(TOLERANCE));
        }

        @Test
        void testNumberOnlyTupletReservesNumberOnlyHeight() {
            // Beamed, stems-up notes → numberOnly. Asserts the stacker selected the
            // number-only height and not the bracketed height.
            var note1 = createNote(0, true);
            var note2 = createNote(0, true);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);
            line.addRangeElement(new Beam(note1, note2));

            var tuplet = new Tuplet(note1, note2, 3);
            line.addRangeElement(tuplet);

            var result = stackDirectly(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)), line);

            var layout = require(result.getDecorationLayout(tuplet), "tuplet layout");
            assertThat(layout.heightSs()).isCloseTo(Tuplet.numberOnlyHeightSs(), within(TOLERANCE));
            assertThat(layout.heightSs()).isNotCloseTo(Tuplet.bracketedHeightSs(), within(TOLERANCE));
        }

    }

    @SuppressWarnings({ "PackageVisibleInnerClass", "DataFlowIssue" })
    @Nested
    class TextDynamicsStacking {

        @Test
        void testTextDynamicsPositionedAboveStaff() {
            var note = createNote(0, false);
            note.addAttachment(new DynamicAttachment(note,
                DynamicAttachment.DynamicType.FORTE));

            var line = detachedLine();
            line.addElement(note);

            var result = stackColumns(List.of(columnFor(note, NOTE1_X_SS)), line);

            var layout = require(
                result.findAttachmentDecorationLayout(note, DynamicAttachment.class),
                "text dynamics DecorationLayout");

            assertThat(layout.ySs()).isLessThan(0.0);
        }

        @Test
        void testTextDynamicsHasPositiveDimensions() {
            var note = createNote(0, false);
            note.addAttachment(new DynamicAttachment(note,
                DynamicAttachment.DynamicType.PIANO));

            var line = detachedLine();
            line.addElement(note);

            var result = stackColumns(List.of(columnFor(note, NOTE1_X_SS)), line);

            var layout = require(
                result.findAttachmentDecorationLayout(note, DynamicAttachment.class),
                "text dynamics DecorationLayout");

            assertThat(layout.widthSs()).isGreaterThan(0.0);
            assertThat(layout.heightSs()).isGreaterThan(0.0);
        }

        @Test
        void testTextDynamicsPositionedBelowHairpins() {
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);
            note1.addAttachment(new DynamicAttachment(note1,
                DynamicAttachment.DynamicType.MEZZO_FORTE));

            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);

            var crescendo = new Crescendo(note1, note2);
            line.addRangeElement(crescendo);

            var result = stackColumns(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)),
                line);

            var dynamicsLayout = require(
                result.findAttachmentDecorationLayout(note1, DynamicAttachment.class),
                "text dynamics DecorationLayout");
            var hairpinLayout = require(
                result.getDecorationLayout(crescendo),
                "crescendo DecorationLayout");

            // Hairpins are processed before text dynamics in the structural tier,
            // so text dynamics should stack above the hairpin
            // (both are above-staff, so the text dynamics Y should be more negative)
            assertThat(dynamicsLayout.ySs())
                .describedAs("text dynamics should stack above hairpin")
                .isLessThan(hairpinLayout.ySs());
        }

        @Test
        void testTextDynamicsXIsCenteredOnNotehead() {
            // Verifies: centeredXSs = columnXSs + noteheadCenterXSs - contentWidthSs/2
            var note = createNote(0, false);
            var dynamic = new DynamicAttachment(note, DynamicAttachment.DynamicType.FORTE);
            note.addAttachment(dynamic);

            var line = detachedLine();
            line.addElement(note);

            var result = stackDirectly(List.of(columnFor(note, NOTE1_X_SS)), line);

            var layout = require(
                result.findAttachmentDecorationLayout(note, DynamicAttachment.class),
                "text dynamics DecorationLayout");

            var noteType = note.getType();
            var noteheadCenterXSs = noteType.getElementCenterXSs()
                + NoteGeometry.getNoteheadXOffsetSs(noteType, note.getDirection());
            var expectedXSs = NOTE1_X_SS + noteheadCenterXSs - dynamic.getContentWidthSs() / 2.0;
            assertThat(layout.xSs()).isCloseTo(expectedXSs, within(TOLERANCE));
        }

        @Test
        void testIsolatedTextDynamicsClampsToStaffPadding() {
            // stackDirectly runs against fresh (zero-top) extents: no real reservation under the
            // dynamic's footprint, so only the staff-padding clamp applies.
            var note = createNote(0, false);
            var dynamic = new DynamicAttachment(note, DynamicAttachment.DynamicType.FORTE);
            note.addAttachment(dynamic);

            var line = detachedLine();
            line.addElement(note);

            var result = stackDirectly(List.of(columnFor(note, NOTE1_X_SS)), line);

            var layout = require(
                result.findAttachmentDecorationLayout(note, DynamicAttachment.class),
                "text dynamics DecorationLayout");

            var expectedYSs = -Staff.STAFF_HALF_SS
                - NoteAttachedStacker.DYNAMIC_STAFF_PADDING_SS - layout.heightSs();
            assertThat(layout.ySs()).isCloseTo(expectedYSs, within(TOLERANCE));
        }

        @Test
        void testTextDynamicsClearsFermataByDynamicPadding() {
            // The structural tier starts from a copy of the note-attached layer's top extents
            // (StaffExtents.copyTopFrom), so a tier-2 fermata already placed above the staff is a
            // real reservation the dynamic's placement must clear by DYNAMIC_PADDING_SS.
            var note = createNote(0, false);
            var fermata = new FermataAttachment(note);
            note.addAttachment(fermata);
            var dynamic = new DynamicAttachment(note, DynamicAttachment.DynamicType.FORTE);
            note.addAttachment(dynamic);

            var line = detachedLine();
            line.addElement(note);

            var result = stackColumns(List.of(columnFor(note, NOTE1_X_SS)), line);

            var fermataLayout = require(
                result.findAttachmentDecorationLayout(note, FermataAttachment.class),
                "fermata DecorationLayout");
            var dynamicLayout = require(
                result.findAttachmentDecorationLayout(note, DynamicAttachment.class),
                "text dynamics DecorationLayout");

            assertThat(fermataLayout.ySs() - (dynamicLayout.ySs() + dynamicLayout.heightSs()))
                .describedAs("text dynamics must clear the fermata by DYNAMIC_PADDING_SS")
                .isCloseTo(NoteAttachedStacker.DYNAMIC_PADDING_SS, within(TOLERANCE));
        }
    }

    @SuppressWarnings({ "PackageVisibleInnerClass", "DataFlowIssue" })
    @Nested
    class SpanElementGuards {

        @Test
        void testStackSpanElementSkipsNullAnchor() {
            // stackSpanElement checks anchor/end for null before column lookup.
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);

            var crescendo = new Crescendo(note1, note2);
            crescendo.setAnchorElement(null);  // trigger null guard
            line.addRangeElement(crescendo);

            var result = stackDirectly(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)), line);

            // No layout should have been emitted for the null-anchor span element.
            assertThat(result.getDecorationLayout(crescendo)).isNull();
        }
    }

    @SuppressWarnings({ "PackageVisibleInnerClass", "DataFlowIssue" })
    @Nested
    class EndingStacking {

        @Test
        void testEndingPositionedAboveStaff() {
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);

            var ending = new Ending(note1, note2);
            line.addRangeElement(ending);

            var result = stackColumns(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)),
                line);

            var layout = require(
                result.getDecorationLayout(ending),
                "ending DecorationLayout");

            assertThat(layout.ySs()).isLessThan(0.0);
        }

        @Test
        void testEndingHasPositiveDimensions() {
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);

            var ending = new Ending(note1, note2);
            line.addRangeElement(ending);

            var result = stackColumns(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)),
                line);

            var layout = require(
                result.getDecorationLayout(ending),
                "ending DecorationLayout");

            assertThat(layout.widthSs()).isGreaterThan(0.0);
            assertThat(layout.heightSs()).isCloseTo(
                Ending.VOLTA_TICK_HEIGHT_SS, within(TOLERANCE));
        }

        @Test
        void testEndingPositionedAboveHairpins() {
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);

            var crescendo = new Crescendo(note1, note2);
            line.addRangeElement(crescendo);

            var ending = new Ending(note1, note2);
            line.addRangeElement(ending);

            var result = stackColumns(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)),
                line);

            var hairpinLayout = require(
                result.getDecorationLayout(crescendo),
                "crescendo DecorationLayout");
            var endingLayout = require(
                result.getDecorationLayout(ending),
                "ending DecorationLayout");

            // Ending should be above hairpin (more negative Y)
            assertThat(endingLayout.ySs()).isLessThan(hairpinLayout.ySs());
        }

        @Test
        void testEndingRangeElementProducesDecorationLayout() {
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);

            var ending = new Ending(note1, note2);
            line.addRangeElement(ending);

            var result = stackColumns(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)),
                line);

            var decorationLayout = require(
                result.getDecorationLayout(ending),
                "ending DecorationLayout");

            assertThat(decorationLayout.ySs()).isLessThan(0.0);
        }
    }

    @SuppressWarnings({ "PackageVisibleInnerClass", "DataFlowIssue" })
    @Nested
    class StructuralTierInteraction {

        @Test
        void testAllStructuralElementsAboveNoteAttachedLayer() {
            var note1 = createNote(-2, false);
            note1.addAttachment(new FermataAttachment(note1));
            var note2 = createNote(0, false);
            note2.addAttachment(new DynamicAttachment(note2,
                DynamicAttachment.DynamicType.FORTE));

            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);

            var crescendo = new Crescendo(note1, note2);
            line.addRangeElement(crescendo);

            var ending = new Ending(note1, note2);
            line.addRangeElement(ending);

            var result = stackColumns(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)),
                line);

            var fermataLayout = require(
                result.findAttachmentDecorationLayout(note1, FermataAttachment.class),
                "fermata DecorationLayout");
            var hairpinLayout = require(
                result.getDecorationLayout(crescendo),
                "crescendo DecorationLayout");
            var endingLayout = require(
                result.getDecorationLayout(ending),
                "ending DecorationLayout");

            // All structural elements should be above the fermata (note-attached)
            assertThat(hairpinLayout.ySs())
                .describedAs("hairpin above fermata")
                .isLessThan(fermataLayout.ySs());
            assertThat(endingLayout.ySs())
                .describedAs("ending above fermata")
                .isLessThan(fermataLayout.ySs());
        }
    }
}
