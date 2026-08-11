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
import static songscribe.dom.StaffElementFactory.repeatRight;
import static songscribe.dom.StaffElementFactory.semibreve;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.Crescendo;
import songscribe.dom.TempoChangeAttachment;
import songscribe.font.DocumentFonts;
import songscribe.dom.Annotation;
import songscribe.dom.BeatChange;
import songscribe.dom.Song;
import songscribe.dom.Duration;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.dom.Tempo;
import songscribe.font.FontKey;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.layout.stacking.StackingUtils;
import songscribe.layout.stacking.SystemStacker;
import songscribe.layout.stacking.VerticalStackingCalculator;

@SuppressWarnings({ "StaticVariableMayNotBeInitialized", "StaticVariableUsedBeforeInitialization" })
class SystemTierStackingTest extends UnitTest {

    private static final double LINE_WIDTH_SS = 64.0;
    private static final double NOTE1_X_SS = 10.0;
    private static final double NOTE2_X_SS = 30.0;
    private static final double NOTE3_X_SS = 50.0;
    private static final double TOLERANCE = 0.001;

    /**
     * How much larger the annotation font is made than the attribution font in
     * {@link BeatChangeStacking#testBeatChangeWidthUsesTheResolvedAnnotationFontNotTheAttributionFont}
     * — large enough that the two fonts cannot produce the same content width by coincidence.
     */
    private static final int ANNOTATION_FONT_SIZE_DELTA_PX = 12;

    private static Song song;

    @BeforeAll
    static void setUp() {
        song = new Song();
    }

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

    private static Line newLine() {
        return new Line(song);
    }

    /**
     * Adds elements to {@code line} without mutation tracking. Layout tests do not
     * care about the mutation system and would otherwise have to wrap every
     * {@code addElement} call in a modification bracket.
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

    private static LayoutResult stackColumns(List<ElementColumn> columns, Line line) {
        return stackColumns(columns, line, DocumentFonts.defaultFonts());
    }

    private static LayoutResult stackColumns(
        List<ElementColumn> columns, Line line, DocumentFonts fonts) {

        var builder = new LayoutResultBuilder();
        var calculator = new VerticalStackingCalculator();
        calculator.calculate(columns, line, builder, LINE_WIDTH_SS, fonts);
        return builder.build();
    }

    @SuppressWarnings({ "PackageVisibleInnerClass", "DataFlowIssue" })
    @Nested
    class TempoStacking {

        @Test
        void testTempoAttachmentPositionedAboveStaff() {
            var note = createNote(0, false);
            var tempo = new TempoChangeAttachment(note, new Tempo());
            note.addAttachment(tempo);

            var line = newLine();
            populate(line,note);

            var result = stackColumns(List.of(columnFor(note, NOTE1_X_SS)), line);

            var layout = require(
                result.getDecorationLayout(tempo),
                "tempo DecorationLayout");

            // Tempo should be above the staff (negative Y = higher)
            assertThat(layout.ySs()).isLessThan(0.0);
        }

        @Test
        void testTempoAttachmentHasPositiveDimensions() {
            var note = createNote(0, false);
            var tempo = new TempoChangeAttachment(note, new Tempo());
            note.addAttachment(tempo);

            var line = newLine();
            populate(line,note);

            var result = stackColumns(List.of(columnFor(note, NOTE1_X_SS)), line);

            var layout = require(
                result.getDecorationLayout(tempo),
                "tempo DecorationLayout");

            assertThat(layout.widthSs()).isGreaterThan(0.0);
            assertThat(layout.heightSs()).isGreaterThan(0.0);
        }

        @Test
        void testTempoPositionedAboveStructuralLayer() {
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);

            var tempo = new TempoChangeAttachment(note1, new Tempo());
            note1.addAttachment(tempo);

            var line = newLine();
            populate(line,note1);
            populate(line,note2);

            // Add a hairpin in the structural layer
            var crescendo = new Crescendo(note1, note2);
            setupTest(() -> line.addSpan(crescendo));

            var result = stackColumns(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)),
                line);

            var tempoLayout = require(
                result.getDecorationLayout(tempo),
                "tempo DecorationLayout");
            var hairpinLayout = require(
                result.getDecorationLayout(crescendo),
                "crescendo DecorationLayout");

            // Tempo (system tier) should be above hairpin (structural tier)
            assertThat(tempoLayout.ySs())
                .describedAs("tempo should stack above hairpin")
                .isLessThan(hairpinLayout.ySs());
        }

        @Test
        void testTempoAttachmentProducesLayout() {
            var note = createNote(0, false);
            note.addAttachment(new TempoChangeAttachment(
                note, new Tempo(140, Duration.CROTCHET, "Allegro", true)));

            var line = newLine();
            populate(line, note);

            var result = stackColumns(List.of(columnFor(note, NOTE1_X_SS)), line);

            var layout = require(
                result.findAttachmentDecorationLayout(note, TempoChangeAttachment.class),
                "tempo attachment should produce DecorationLayout");

            // stackMetronomeAttachment passes column.getXSs() as xSs without adjustment
            assertThat(layout.xSs())
                .describedAs("tempo X matches column position")
                .isEqualTo(NOTE1_X_SS);
        }

        /**
         * A tempo change showing neither the metronome mark nor a description has nothing to
         * draw, so it must be skipped rather than stacked.
         * <p>
         * Stacking it would place it from an empty collision-region list, and the placement
         * routine derives the Y by taking the minimum across those regions starting from
         * {@code Double.MAX_VALUE} — with no regions to visit, that starting value survives into
         * the layout. The song-level tempo mark has always guarded this; the per-note path did
         * not.
         */
        @Test
        void testTempoAttachmentWithNothingToDrawIsSkippedRatherThanStackedAtInfinity() {
            var note = createNote(0, false);
            note.addAttachment(new TempoChangeAttachment(
                note, new Tempo(140, Duration.CROTCHET, "", false)));

            var line = newLine();
            populate(line, note);

            var result = stackColumns(List.of(columnFor(note, NOTE1_X_SS)), line);

            assertThat(result.findAttachmentDecorationLayout(note, TempoChangeAttachment.class))
                .describedAs("a tempo change with no glyph and no description reserves nothing")
                .isNull();
        }

        @Test
        void testTempoAttachmentExactYPlacementAtAnchor() {
            var note = createNote(0, false);
            var tempo = new TempoChangeAttachment(note, new Tempo(120, Duration.CROTCHET, "Allegro", true));
            note.addAttachment(tempo);

            var line = newLine();
            populate(line, note);

            var result = stackColumns(List.of(columnFor(note, NOTE1_X_SS)), line);

            var layout = require(result.getDecorationLayout(tempo), "tempo DecorationLayout");

            // Oracle: for fresh extents (yGet = 0.0) and a note on the middle line (staffPosition=0),
            // anchorCeilingSs = STAFF_TOP_Y_SS < 0, so regionCeilingSs = anchorCeilingSs.
            // elementY = min over regions of (anchorSs - marginSs - yOffsetSs - heightSs).
            //
            // The stacker resolves fonts.getAnnotationFont() (issue #735 divergence 3), so the
            // oracle must build the same MetronomeContent the stacker builds, not a fixture of
            // its own, or a font mismatch here would go undetected.
            var annotationFont = DocumentFonts.defaultFonts().getAnnotationFont();
            var content = MetronomeContent.forTempo(tempo.getTempo(), annotationFont);
            var anchorSs = StackingUtils.anchorCeilingSs(note);
            var expectedYSs = Double.MAX_VALUE;

            for (var region : content.regions()) {
                var regionYSs = anchorSs - SystemStacker.TEMPO_MARGIN_SS
                    - region.yOffsetSs() - region.heightSs();
                expectedYSs = Math.min(expectedYSs, regionYSs);
            }

            assertThat(layout.ySs())
                .describedAs("tempo Y = anchor - margin - region extents")
                .isCloseTo(expectedYSs, within(TOLERANCE));
        }
    }

    @SuppressWarnings({ "PackageVisibleInnerClass", "DataFlowIssue" })
    @Nested
    class BeatChangeStacking {

        @Test
        void testBeatChangeAttachmentPositionedAboveStaff() {
            var note = createNote(0, false);
            var beatChange = new BeatChangeAttachment(note, new BeatChange(Duration.CROTCHET, Duration.CROTCHET));
            note.addAttachment(beatChange);

            var line = newLine();
            populate(line, note);

            var result = stackColumns(List.of(columnFor(note, NOTE1_X_SS)), line);

            var layout = require(
                result.getDecorationLayout(beatChange),
                "beat change DecorationLayout");

            // Beat change should be above the staff (negative Y = higher)
            assertThat(layout.ySs()).isLessThan(0.0);
        }

        @Test
        void testBeatChangeAttachmentHasPositiveDimensions() {
            var note = createNote(0, false);
            var beatChange = new BeatChangeAttachment(note, new BeatChange(Duration.CROTCHET, Duration.CROTCHET));
            note.addAttachment(beatChange);

            var line = newLine();
            populate(line, note);

            var result = stackColumns(List.of(columnFor(note, NOTE1_X_SS)), line);

            var layout = require(
                result.getDecorationLayout(beatChange),
                "beat change DecorationLayout");

            assertThat(layout.widthSs()).isGreaterThan(0.0);
            assertThat(layout.heightSs()).isGreaterThan(0.0);
        }

        @Test
        void testBeatChangePositionedAboveStructuralLayer() {
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);

            var beatChange = new BeatChangeAttachment(note1, new BeatChange(Duration.CROTCHET, Duration.CROTCHET));
            note1.addAttachment(beatChange);

            var line = newLine();
            populate(line, note1);
            populate(line, note2);

            var crescendo = new Crescendo(note1, note2);
            setupTest(() -> line.addSpan(crescendo));

            var result = stackColumns(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)),
                line);

            var beatChangeLayout = require(
                result.getDecorationLayout(beatChange),
                "beat change DecorationLayout");
            var hairpinLayout = require(
                result.getDecorationLayout(crescendo),
                "crescendo DecorationLayout");

            // Beat change (system tier) should be above hairpin (structural tier)
            assertThat(beatChangeLayout.ySs())
                .describedAs("beat change should stack above hairpin")
                .isLessThan(hairpinLayout.ySs());
        }

        // Regression for issue #735 divergence 3: every layout path measured with the
        // attribution font while every render path drew with the annotation font. The two are
        // invisibly identical in system-defaults.json, so only a font swap this large catches a
        // revert back to the attribution font.
        @Test
        void testBeatChangeWidthUsesTheResolvedAnnotationFontNotTheAttributionFont() {
            var fonts = DocumentFonts.defaultFonts();
            var attributionFont = fonts.getAttributionFont();
            fonts.setFont(
                FontKey.ANNOTATION,
                attributionFont.getName(),
                attributionFont.getSize() + ANNOTATION_FONT_SIZE_DELTA_PX);

            var note = createNote(0, false);
            var beatChange =
                new BeatChangeAttachment(note, new BeatChange(Duration.CROTCHET, Duration.CROTCHET));
            note.addAttachment(beatChange);

            var line = newLine();
            populate(line, note);

            var result = stackColumns(List.of(columnFor(note, NOTE1_X_SS)), line, fonts);

            var layout = require(
                result.getDecorationLayout(beatChange),
                "beat change DecorationLayout");

            var annotationFont = fonts.getAnnotationFont();
            var annotationWidthSs =
                MetronomeContent.forBeatChange(beatChange.getBeatChange(), annotationFont).widthSs();
            var attributionWidthSs =
                MetronomeContent.forBeatChange(beatChange.getBeatChange(), attributionFont).widthSs();

            assertThat(layout.widthSs())
                .describedAs("stacked width must match the resolved annotation-font content")
                .isCloseTo(annotationWidthSs, within(TOLERANCE));
            assertThat(layout.widthSs())
                .describedAs("precondition: the two fonts must produce different widths, "
                    + "or this test cannot fail")
                .isNotCloseTo(attributionWidthSs, within(TOLERANCE));
        }

        /**
         * The real stacker must attach the typeset content to the layout it registers. Every
         * renderer reads the marking off {@code DecorationLayout.content()} and throws when it
         * is absent, so a stacker that stopped passing it through would throw on the first
         * repaint of any beat change — and no test that builds its own layout would notice.
         */
        @Test
        void testStackedBeatChangeLayoutCarriesItsContent() {
            var fonts = DocumentFonts.defaultFonts();
            var note = createNote(0, false);
            var beatChange =
                new BeatChangeAttachment(note, new BeatChange(Duration.CROTCHET, Duration.CROTCHET));
            note.addAttachment(beatChange);

            var line = newLine();
            populate(line, note);

            var result = stackColumns(List.of(columnFor(note, NOTE1_X_SS)), line, fonts);

            var layout = require(
                result.getDecorationLayout(beatChange),
                "beat change DecorationLayout");

            assertThat(layout.content())
                .describedAs("the stacker must attach the content the renderers read back")
                .isNotNull();
            assertThat(layout.content().widthSs()).isCloseTo(layout.widthSs(), within(TOLERANCE));
        }

    }

    @SuppressWarnings({ "PackageVisibleInnerClass", "DataFlowIssue" })
    @Nested
    class AnnotationStacking {

        @Test
        void testAnnotationPositionedAboveStaff() {
            var note = createNote(0, false);
            var annotation = new AnnotationAttachment(note, new Annotation("Andante"));
            note.addAttachment(annotation);

            var line = newLine();
            populate(line, note);

            var result = stackColumns(List.of(columnFor(note, NOTE1_X_SS)), line);

            var layout = require(
                result.getDecorationLayout(annotation),
                "annotation DecorationLayout");

            // Annotation should be above the staff (negative Y = higher)
            assertThat(layout.ySs()).isLessThan(0.0);
        }

        @Test
        void testAnnotationHasPositiveDimensions() {
            var note = createNote(0, false);
            var annotation = new AnnotationAttachment(note, new Annotation("Andante"));
            note.addAttachment(annotation);

            var line = newLine();
            populate(line, note);

            var result = stackColumns(List.of(columnFor(note, NOTE1_X_SS)), line);

            var layout = require(
                result.getDecorationLayout(annotation),
                "annotation DecorationLayout");

            assertThat(layout.widthSs()).isGreaterThan(0.0);
            assertThat(layout.heightSs()).isGreaterThan(0.0);
        }

        @Test
        void testAnnotationXAlignmentProducesFormulaPositions() {
            var fonts = DocumentFonts.defaultFonts();
            var noteLeft = createNote(0, false);
            var noteCenter = createNote(0, false);
            var noteRight = createNote(0, false);

            // 0.5f = center alignment, 1.0f = right alignment (Component.CENTER/RIGHT_ALIGNMENT)
            var annotLeft = new AnnotationAttachment(noteLeft, new Annotation("Andante molto"));
            var annotCenter = new AnnotationAttachment(noteCenter, new Annotation("Andante molto", 0.5f));
            var annotRight = new AnnotationAttachment(noteRight, new Annotation("Andante molto", 1.0f));

            noteLeft.addAttachment(annotLeft);
            noteCenter.addAttachment(annotCenter);
            noteRight.addAttachment(annotRight);

            var line = newLine();
            populate(line, noteLeft, noteCenter, noteRight);

            var result = stackColumns(
                List.of(
                    columnFor(noteLeft, NOTE1_X_SS),
                    columnFor(noteCenter, NOTE2_X_SS),
                    columnFor(noteRight, NOTE3_X_SS)),
                line);

            var annotationFont = fonts.getAnnotationFont();
            var annotWidthSs = annotLeft.computeContentWidthSs(annotationFont);
            var noteheadWidthSs = noteLeft.getType().getElementWidthSs();

            var layoutLeft = require(result.getDecorationLayout(annotLeft), "left annotation layout");
            var layoutCenter = require(result.getDecorationLayout(annotCenter), "center annotation layout");
            var layoutRight = require(result.getDecorationLayout(annotRight), "right annotation layout");

            // Formula: xSs = columnXSs + xAlignment * (noteheadWidthSs - annotWidthSs)
            assertThat(layoutLeft.xSs())
                .describedAs("left-aligned (0.0): x = columnX")
                .isCloseTo(NOTE1_X_SS, within(TOLERANCE));

            assertThat(layoutCenter.xSs())
                .describedAs("center-aligned (0.5): x = columnX + 0.5 × (noteheadWidth - annotWidth)")
                .isCloseTo(NOTE2_X_SS + 0.5 * (noteheadWidthSs - annotWidthSs), within(TOLERANCE));

            assertThat(layoutRight.xSs())
                .describedAs("right-aligned (1.0): x = columnX + noteheadWidth - annotWidth")
                .isCloseTo(NOTE3_X_SS + noteheadWidthSs - annotWidthSs, within(TOLERANCE));
        }

        // A right-aligned annotation is pushed right by the width of the head it sits over, so on a
        // whole note it must use the wider whole notehead. Before #694 every note type was anchored
        // with the black-notehead width, leaving an annotation over a whole note visibly left of
        // where it belongs. The expected width comes from the font metadata, so this fails if a
        // whole note is measured as a black notehead again.
        @Test
        void testRightAlignedAnnotationOnAWholeNoteAnchorsToTheWholeNoteheadWidth() {
            var fonts = DocumentFonts.defaultFonts();
            var wholeNote = semibreve();
            var annotation = new AnnotationAttachment(wholeNote, new Annotation("Andante molto", 1.0f));
            wholeNote.addAttachment(annotation);

            var line = newLine();
            populate(line, wholeNote);

            var result = stackColumns(List.of(columnFor(wholeNote, NOTE1_X_SS)), line);

            var annotWidthSs = annotation.computeContentWidthSs(fonts.getAnnotationFont());
            var wholeNoteheadWidthSs = SMuFLMetadata.requireBBox(SMuFLGlyph.NOTEHEAD_WHOLE).right();
            var layout = require(result.getDecorationLayout(annotation), "whole-note annotation layout");

            assertThat(layout.xSs())
                .describedAs("right-aligned (1.0): x = columnX + wholeNoteheadWidth - annotWidth")
                .isCloseTo(NOTE1_X_SS + wholeNoteheadWidthSs - annotWidthSs, within(TOLERANCE));

            assertThat(wholeNoteheadWidthSs)
                .as("precondition: a whole notehead is wider than a black one, or this proves nothing")
                .isGreaterThan(SMuFLMetadata.requireBBox(SMuFLGlyph.NOTEHEAD_BLACK).right());
        }

        @Test
        void testAnnotationOnRepeatBarlineAnchorsToElementWidth() {
            // Repeats have no SMuFL glyph to measure, so the anchor falls back to
            // the element width (see issue #661 — legacy files with "fine" on a repeat).
            var repeat = repeatRight();
            var annotation = new AnnotationAttachment(repeat, new Annotation("fine", 1.0f));
            repeat.addAttachment(annotation);

            var line = newLine();
            populate(line, repeat);

            var result = stackColumns(List.of(columnFor(repeat, NOTE1_X_SS)), line);

            var layout = require(
                result.getDecorationLayout(annotation),
                "annotation DecorationLayout");

            var annotWidthSs = annotation.computeContentWidthSs(
                DocumentFonts.defaultFonts().getAnnotationFont());

            assertThat(layout.xSs())
                .describedAs("right-aligned (1.0): x = columnX + elementWidth - annotWidth")
                .isCloseTo(
                    NOTE1_X_SS + ElementType.REPEAT_RIGHT.getElementWidthSs() - annotWidthSs,
                    within(TOLERANCE));
        }

        @Test
        void testAnnotationPositionedAboveStructuralLayer() {
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);

            var annotation = new AnnotationAttachment(note1, new Annotation("Andante"));
            note1.addAttachment(annotation);

            var line = newLine();
            populate(line, note1);
            populate(line, note2);

            var crescendo = new Crescendo(note1, note2);
            setupTest(() -> line.addSpan(crescendo));

            var result = stackColumns(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)),
                line);

            var annotationLayout = require(
                result.getDecorationLayout(annotation),
                "annotation DecorationLayout");
            var hairpinLayout = require(
                result.getDecorationLayout(crescendo),
                "crescendo DecorationLayout");

            // Annotation (system tier) should be above hairpin (structural tier)
            assertThat(annotationLayout.ySs())
                .describedAs("annotation should stack above hairpin")
                .isLessThan(hairpinLayout.ySs());
        }

    }
}
