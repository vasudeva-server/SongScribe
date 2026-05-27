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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.Crescendo;
import songscribe.dom.FermataAttachment;
import songscribe.dom.TempoChangeAttachment;
import songscribe.dom.Trill;
import songscribe.font.DocumentFonts;
import songscribe.dom.Song;

import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.dom.Tempo;
import songscribe.layout.stacking.VerticalStackingCalculator;

/**
 * Tests that manual user offsets are applied correctly post-layout,
 * without re-running collision detection.
 */
@SuppressWarnings({ "StaticVariableMayNotBeInitialized", "StaticVariableUsedBeforeInitialization" })
class ManualOffsetStackingTest extends UnitTest {

    private static Song song;

    @BeforeAll
    static void setUp() {
        song = new Song();
    }

    private static final double LINE_WIDTH_SS = 64.0;
    private static final double TOLERANCE = 0.001;
    private static final double NOTE1_X_SS = 10.0;
    private static final double NOTE2_X_SS = 30.0;

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

    private static ElementColumn columnFor(StaffElement note) {
        return columnFor(note, NOTE1_X_SS);
    }

    private static LayoutResult stackColumns(List<ElementColumn> columns, Line line) {
        var builder = new LayoutResult.Builder();
        var calculator = new VerticalStackingCalculator();
        calculator.calculate(columns, line, builder, LINE_WIDTH_SS, DocumentFonts.defaultsFromPrefs());
        return builder.build();
    }

    private static Line newLine() {
        return new Line(song);
    }

    /**
     * Runs test-setup code without mutation tracking. Layout tests do not care
     * about the mutation system and would otherwise have to wrap every
     * {@code addElement} / {@code addRangeElement} call in a modification bracket.
     */
    private static void setupTest(Runnable body) {
        song.withoutMutationTracking(body);
    }

    private static void populate(Line target, StaffElement... elements) {
        setupTest(() -> {
            for (var element : elements) {
                target.addElement(element);
            }
        });
    }

    private static LayoutResult stackColumns(List<ElementColumn> columns) {
        return stackColumns(columns, newLine());
    }

    @SuppressWarnings({ "PackageVisibleInnerClass", "DataFlowIssue" })
    @Nested
    class AnnotationOffsets {

        @Test
        void testAnnotationAttachmentUserOffsetApplied() {
            var note = createNote(0, false);
            var attachment = new AnnotationAttachment("test");
            var yOffsetSs = -1.5;
            attachment.setUserYOffsetSs(yOffsetSs);
            note.addAttachment(attachment);

            var line = newLine();
            populate(line,note);

            var resultWithOffset = stackColumns(List.of(columnFor(note)), line);
            var layoutWithOffset = require(
                resultWithOffset.findAttachmentDecorationLayout(note, AnnotationAttachment.class),
                "annotation DecorationLayout with offset");

            // Create a baseline without offset for comparison
            var note2 = createNote(0, false);
            var attachment2 = new AnnotationAttachment("test");
            note2.addAttachment(attachment2);

            var line2 = newLine();
            populate(line2,note2);

            var resultBaseline = stackColumns(List.of(columnFor(note2)), line2);
            var layoutBaseline = require(
                resultBaseline.findAttachmentDecorationLayout(note2, AnnotationAttachment.class),
                "annotation DecorationLayout baseline");

            assertThat(layoutWithOffset.ySs())
                .isCloseTo(layoutBaseline.ySs() + yOffsetSs, within(TOLERANCE));
        }

        @Test
        void testAnnotationAttachmentXOffsetApplied() {
            var note = createNote(0, false);
            var attachment = new AnnotationAttachment("test");
            var xOffsetSs = 2.0;
            attachment.setUserXOffsetSs(xOffsetSs);
            note.addAttachment(attachment);

            var line = newLine();
            populate(line,note);

            var resultWithOffset = stackColumns(List.of(columnFor(note)), line);
            var layoutWithOffset = require(
                resultWithOffset.findAttachmentDecorationLayout(note, AnnotationAttachment.class),
                "annotation DecorationLayout with offset");

            assertThat(layoutWithOffset.xSs())
                .isCloseTo(NOTE1_X_SS + xOffsetSs, within(TOLERANCE));
        }

    }

    @SuppressWarnings({ "PackageVisibleInnerClass", "DataFlowIssue" })
    @Nested
    class FermataOffsets {

        @Test
        void testFermataUserYOffsetApplied() {
            var note = createNote(0, false);
            var fermata = new FermataAttachment(note);
            var yOffsetSs = -1.0;
            fermata.setUserYOffsetSs(yOffsetSs);
            note.addAttachment(fermata);

            var line = newLine();
            populate(line,note);

            var resultWithOffset = stackColumns(List.of(columnFor(note)), line);
            var layoutWithOffset = require(
                resultWithOffset.findAttachmentDecorationLayout(note, FermataAttachment.class),
                "fermata DecorationLayout with offset");

            // Create baseline
            var note2 = createNote(0, false);
            note2.addAttachment(new FermataAttachment(note2));

            var line2 = newLine();
            populate(line2,note2);

            var resultBaseline = stackColumns(List.of(columnFor(note2)), line2);
            var layoutBaseline = require(
                resultBaseline.findAttachmentDecorationLayout(note2, FermataAttachment.class),
                "fermata DecorationLayout baseline");

            assertThat(layoutWithOffset.ySs())
                .isCloseTo(layoutBaseline.ySs() + yOffsetSs, within(TOLERANCE));
        }
    }

    @SuppressWarnings({ "PackageVisibleInnerClass", "DataFlowIssue" })
    @Nested
    class HairpinOffsets {

        @Test
        void testHairpinYShiftApplied() {
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);
            var line = newLine();
            populate(line, note1);
            populate(line, note2);

            var yShiftSs = -1.5;
            var crescendo = new Crescendo(note1, note2);
            crescendo.setYShiftSs(yShiftSs);
            setupTest(() -> line.addRangeElement(crescendo));

            var result = stackColumns(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)),
                line);

            var layout = require(
                result.getDecorationLayout(crescendo),
                "crescendo DecorationLayout with y shift");

            // Create baseline without shift
            var note3 = createNote(0, false);
            var note4 = createNote(0, false);
            var line2 = newLine();
            populate(line2, note3);
            populate(line2, note4);

            var baselineCrescendo = new Crescendo(note3, note4);
            setupTest(() -> line2.addRangeElement(baselineCrescendo));

            var baselineResult = stackColumns(
                List.of(columnFor(note3, NOTE1_X_SS), columnFor(note4, NOTE2_X_SS)),
                line2);

            var baselineLayout = require(
                baselineResult.getDecorationLayout(baselineCrescendo),
                "crescendo DecorationLayout baseline");

            assertThat(layout.ySs())
                .isCloseTo(baselineLayout.ySs() + yShiftSs, within(TOLERANCE));
        }

        @Test
        void testHairpinXShiftsApplied() {
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);
            var line = newLine();
            populate(line, note1);
            populate(line, note2);

            var x1ShiftSs = 0.5;
            var x2ShiftSs = -0.5;
            var crescendo = new Crescendo(note1, note2);
            crescendo.setX1ShiftSs(x1ShiftSs);
            crescendo.setX2ShiftSs(x2ShiftSs);
            setupTest(() -> line.addRangeElement(crescendo));

            var result = stackColumns(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)),
                line);

            var layout = require(
                result.getDecorationLayout(crescendo),
                "crescendo DecorationLayout with x shifts");

            // Create baseline without shift
            var note3 = createNote(0, false);
            var note4 = createNote(0, false);
            var line2 = newLine();
            populate(line2, note3);
            populate(line2, note4);

            var baselineCrescendo = new Crescendo(note3, note4);
            setupTest(() -> line2.addRangeElement(baselineCrescendo));

            var baselineResult = stackColumns(
                List.of(columnFor(note3, NOTE1_X_SS), columnFor(note4, NOTE2_X_SS)),
                line2);

            var baselineLayout = require(
                baselineResult.getDecorationLayout(baselineCrescendo),
                "crescendo DecorationLayout baseline");

            // x1 shifts the left edge; x2 shifts the right edge independently
            assertThat(layout.xSs())
                .isCloseTo(baselineLayout.xSs() + x1ShiftSs, within(TOLERANCE));
            assertThat(layout.xSs() + layout.widthSs())
                .isCloseTo(baselineLayout.xSs() + baselineLayout.widthSs() + x2ShiftSs, within(TOLERANCE));
        }
    }

    @SuppressWarnings({ "PackageVisibleInnerClass", "DataFlowIssue" })
    @Nested
    class NoCollisionRerun {

        @Test
        void testOffsetDoesNotAffectOtherElementPositions() {
            // Place a tempo with a large downward offset and an annotation on the same note.
            // The annotation should NOT move to accommodate the offset tempo — collision
            // detection is not re-run after offsets are applied.
            var note = createNote(0, false);
            var tempo = new TempoChangeAttachment(note, new Tempo());
            var largeDownwardOffsetSs = 5.0;
            tempo.setUserYOffsetSs(largeDownwardOffsetSs);
            note.addAttachment(tempo);

            var annAttach = new AnnotationAttachment("test");
            note.addAttachment(annAttach);

            var line = newLine();
            populate(line,note);

            var resultWithOffset = stackColumns(List.of(columnFor(note)), line);

            // Get annotation position with the offset tempo
            var annotationLayout = require(
                resultWithOffset.findAttachmentDecorationLayout(note, AnnotationAttachment.class),
                "annotation with offset tempo");

            // Now create a baseline where tempo has no offset
            var note2 = createNote(0, false);
            var tempo2 = new TempoChangeAttachment(note2, new Tempo());
            note2.addAttachment(tempo2);

            var annAttach2 = new AnnotationAttachment("test");
            note2.addAttachment(annAttach2);

            var line2 = newLine();
            populate(line2,note2);

            var baselineResult = stackColumns(List.of(columnFor(note2)), line2);
            var baselineAnnotation = require(
                baselineResult.findAttachmentDecorationLayout(note2, AnnotationAttachment.class),
                "annotation baseline");

            // Annotation Y should be the same regardless of tempo offset
            assertThat(annotationLayout.ySs())
                .describedAs("annotation Y should not be affected by tempo manual offset")
                .isCloseTo(baselineAnnotation.ySs(), within(TOLERANCE));
        }
    }

    @SuppressWarnings({ "PackageVisibleInnerClass", "DataFlowIssue" })
    @Nested
    class TempoOffsets {

        @Test
        void testTempoUserYOffsetApplied() {
            var note = createNote(0, false);
            var tempo = new TempoChangeAttachment(note, new Tempo());
            var yOffsetSs = -2.0;
            tempo.setUserYOffsetSs(yOffsetSs);
            note.addAttachment(tempo);

            var line = newLine();
            populate(line,note);

            var resultWithOffset = stackColumns(List.of(columnFor(note)), line);
            var layoutWithOffset = require(
                resultWithOffset.findAttachmentDecorationLayout(note, TempoChangeAttachment.class),
                "tempo DecorationLayout with offset");

            // Create baseline
            var note2 = createNote(0, false);
            note2.addAttachment(new TempoChangeAttachment(note2, new Tempo()));

            var line2 = newLine();
            populate(line2,note2);

            var resultBaseline = stackColumns(List.of(columnFor(note2)), line2);
            var layoutBaseline = require(
                resultBaseline.findAttachmentDecorationLayout(note2, TempoChangeAttachment.class),
                "tempo DecorationLayout baseline");

            assertThat(layoutWithOffset.ySs())
                .isCloseTo(layoutBaseline.ySs() + yOffsetSs, within(TOLERANCE));
        }

        @Test
        void testTempoUserXOffsetApplied() {
            var note = createNote(0, false);
            var tempo = new TempoChangeAttachment(note, new Tempo());
            var xOffsetSs = 3.0;
            tempo.setUserXOffsetSs(xOffsetSs);
            note.addAttachment(tempo);

            var line = newLine();
            populate(line,note);

            var result = stackColumns(List.of(columnFor(note)), line);
            var layout = require(
                result.findAttachmentDecorationLayout(note, TempoChangeAttachment.class),
                "tempo DecorationLayout with x offset");

            assertThat(layout.xSs())
                .isCloseTo(NOTE1_X_SS + xOffsetSs, within(TOLERANCE));
        }
    }

    @SuppressWarnings({ "PackageVisibleInnerClass", "DataFlowIssue" })
    @Nested
    class TrillOffsets {

        @Test
        void testTrillYPositionApplied() {
            var note = createNote(0, false);
            var line = newLine();
            populate(line,note);

            var yPositionSs = -3;
            var trill = new Trill(note, note);
            trill.setYPositionSs(yPositionSs);
            setupTest(() -> line.addRangeElement(trill));

            var resultWithOffset = stackColumns(List.of(columnFor(note)), line);
            var layoutWithOffset = require(
                resultWithOffset.getDecorationLayout(trill),
                "trill DecorationLayout with yPosition");

            // Create baseline without yPosition
            var note2 = createNote(0, false);
            var line2 = newLine();
            populate(line2,note2);

            var trill2 = new Trill(note2, note2);
            setupTest(() -> line2.addRangeElement(trill2));

            var resultBaseline = stackColumns(List.of(columnFor(note2)), line2);
            var layoutBaseline = require(
                resultBaseline.getDecorationLayout(trill2),
                "trill DecorationLayout baseline");

            assertThat(layoutWithOffset.ySs())
                .isCloseTo(layoutBaseline.ySs() + yPositionSs, within(TOLERANCE));
        }
    }

    @SuppressWarnings({ "PackageVisibleInnerClass", "DataFlowIssue" })
    @Nested
    class EndingOffsets {

        @Test
        void testEndingYPositionApplied() {
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);
            var line = newLine();
            populate(line,note1);
            populate(line,note2);

            var yPositionSs = -2;
            var ending = new Ending(note1, note2);
            ending.setYPositionSs(yPositionSs);
            setupTest(() -> line.addRangeElement(ending));

            var resultWithOffset = stackColumns(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)),
                line);
            var layoutWithOffset = require(
                resultWithOffset.getDecorationLayout(ending),
                "ending DecorationLayout with yPosition");

            // Create baseline
            var note3 = createNote(0, false);
            var note4 = createNote(0, false);
            var line2 = newLine();
            populate(line2,note3);
            populate(line2,note4);

            var ending2 = new Ending(note3, note4);
            setupTest(() -> line2.addRangeElement(ending2));

            var resultBaseline = stackColumns(
                List.of(columnFor(note3, NOTE1_X_SS), columnFor(note4, NOTE2_X_SS)),
                line2);
            var layoutBaseline = require(
                resultBaseline.getDecorationLayout(ending2),
                "ending DecorationLayout baseline");

            assertThat(layoutWithOffset.ySs())
                .isCloseTo(layoutBaseline.ySs() + yPositionSs, within(TOLERANCE));
        }
    }
}
