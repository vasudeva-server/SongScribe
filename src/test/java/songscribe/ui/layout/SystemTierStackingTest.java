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

package songscribe.ui.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.model.StaffElementFactory.createNote;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.font.DocumentFonts;
import songscribe.model.Annotation;
import songscribe.model.BeatChange;
import songscribe.model.Song;
import songscribe.model.Duration;
import songscribe.model.Line;
import songscribe.model.StaffElement;
import songscribe.model.Tempo;
import songscribe.ui.layout.stacking.VerticalStackingCalculator;

@SuppressWarnings({ "StaticVariableMayNotBeInitialized", "StaticVariableUsedBeforeInitialization" })
class SystemTierStackingTest extends UnitTest {

    private static final double LINE_WIDTH_SS = 64.0;
    private static final double NOTE1_X_SS = 10.0;
    private static final double NOTE2_X_SS = 30.0;

    private static Song song;

    @BeforeAll
    static void setUp() {
        song = new Song();
    }

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
        var builder = new LayoutResult.Builder();
        var calculator = new VerticalStackingCalculator();
        calculator.calculate(columns, line, builder, LINE_WIDTH_SS, DocumentFonts.defaultsFromPrefs());
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
            setupTest(() -> line.addRangeElement(crescendo));

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

            var layout = result.findAttachmentDecorationLayout(
                note, TempoChangeAttachment.class);

            assertThat(layout)
                .describedAs("tempo attachment should produce DecorationLayout")
                .isNotNull();
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
            setupTest(() -> line.addRangeElement(crescendo));

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
        void testAnnotationPositionedAboveStructuralLayer() {
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);

            var annotation = new AnnotationAttachment(note1, new Annotation("Andante"));
            note1.addAttachment(annotation);

            var line = newLine();
            populate(line, note1);
            populate(line, note2);

            var crescendo = new Crescendo(note1, note2);
            setupTest(() -> line.addRangeElement(crescendo));

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
