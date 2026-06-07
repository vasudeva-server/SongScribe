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
package songscribe.message.mutation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import module java.desktop;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.font.DocumentFonts;
import songscribe.dom.Beam;
import songscribe.dom.ElementType;
import songscribe.dom.KeyType;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.dom.Tempo;
import songscribe.dom.Crescendo;
import songscribe.dom.Tuplet;
import songscribe.dom.Diminuendo;
import songscribe.layout.Ending;
import songscribe.dom.RangeElement;
import songscribe.dom.Tie;

class MutationRecordsTest extends UnitTest {

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ElementMutations {

        @Test
        void testElementDeletionExposesFields() {
            var line = detachedLine();
            var deleted = ElementType.CROTCHET.newInstance();
            var mutation = new ElementDeletion(line, 4, deleted);

            assertThat(mutation.line()).isSameAs(line);
            assertThat(mutation.index()).isEqualTo(4);
            assertThat(mutation.deletedElement()).isSameAs(deleted);
            assertThat(mutation.getLine()).isSameAs(line);
        }

        @Test
        void testElementInsertionExposesFields() {
            var line = detachedLine();
            var element = ElementType.CROTCHET.newInstance();
            var mutation = new ElementInsertion(line, 2, element);

            assertThat(mutation.line()).isSameAs(line);
            assertThat(mutation.index()).isEqualTo(2);
            assertThat(mutation.element()).isSameAs(element);
            assertThat(mutation.getLine()).isSameAs(line);
        }

        @Test
        void testElementModificationExposesFields() {
            var line = detachedLine();
            var beforeClone = ElementType.CROTCHET.newInstance();
            var fields = EnumSet.of(ElementField.PITCH);
            var mutation = new ElementModification(line, 1, fields, beforeClone);

            assertThat(mutation.line()).isSameAs(line);
            assertThat(mutation.index()).isEqualTo(1);
            assertThat(mutation.fields()).isEqualTo(fields);
            assertThat(mutation.beforeElement()).isSameAs(beforeClone);
            assertThat(mutation.getLine()).isSameAs(line);
        }

        @Test
        void testElementRangeDeletionExposesFields() {
            var line = detachedLine();
            var first = ElementType.CROTCHET.newInstance();
            var second = ElementType.QUAVER.newInstance();
            var deleted = List.<StaffElement>of(first, second);
            var mutation = new ElementRangeDeletion(line, 2, 3, deleted);

            assertThat(mutation.line()).isSameAs(line);
            assertThat(mutation.from()).isEqualTo(2);
            assertThat(mutation.to()).isEqualTo(3);
            assertThat(mutation.deletedElements()).containsExactly(first, second);
            assertThat(mutation.getLine()).isSameAs(line);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SpanMutations {

        static Stream<Arguments> spanMutations() {
            var line = detachedLine();
            var e0 = ElementType.CROTCHET.newInstance();
            var e1 = ElementType.CROTCHET.newInstance();
            line.addElement(e0);
            line.addElement(e1);
            var tie1 = new Tie(e0, e1);
            var tie2 = new Tie(e0, e1);
            return Stream.of(
                Arguments.of("BeamingAddition",    new BeamingAddition(line, new Beam(e0, e1)), line),
                Arguments.of("BeamingRemoval",     new BeamingRemoval(line, new Beam(e0, e1)), line),
                Arguments.of("TieAddition",        new TieAddition(line, tie1), line),
                Arguments.of("TieRemoval",         new TieRemoval(line, tie2), line),
                Arguments.of("TupletAddition",     new TupletAddition(line, new Tuplet(e0, e1, 3)), line),
                Arguments.of("TupletRemoval",      new TupletRemoval(line, new Tuplet(e0, e1, 5)), line),
                Arguments.of("CrescendoAddition",  new CrescendoAddition(line, new Crescendo(e0, e1)), line),
                Arguments.of("CrescendoRemoval",   new CrescendoRemoval(line, new Crescendo(e0, e1)), line),
                Arguments.of("DiminuendoAddition", new DiminuendoAddition(line, new Diminuendo(e0, e1)), line),
                Arguments.of("DiminuendoRemoval",  new DiminuendoRemoval(line, new Diminuendo(e0, e1)), line)
            );
        }

        @ParameterizedTest(name = "{0} is line-scoped and exposes line")
        @MethodSource("spanMutations")
        void testSpanMutationIsLineScoped(String name, Mutation mutation, Line line) {
            assertThat(mutation).isInstanceOf(LineScopedMutation.class);
            assertThat(((LineScopedMutation) mutation).getLine()).isSameAs(line);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class LineScopedInterfaceMembership {

        @Test
        void testSongScopedMutationsAreNotLineScoped() {
            assertThat(new MetadataChange(MetadataField.TITLE, "a", "b"))
                .isNotInstanceOf(LineScopedMutation.class);
            assertThat(new LayoutChange(LayoutField.LINE_WIDTH_SS, 1.0, 2.0))
                .isNotInstanceOf(LineScopedMutation.class);
            assertThat(new LyricsChange(LyricsField.UNDER, "a", "b"))
                .isNotInstanceOf(LineScopedMutation.class);
            assertThat(new FontChange(DocumentFonts.defaultsFromPrefs(), DocumentFonts.defaultsFromPrefs()))
                .isNotInstanceOf(LineScopedMutation.class);
            assertThat(new LineInsertion(0, detachedLine()))
                .isNotInstanceOf(LineScopedMutation.class);
            assertThat(new LineDeletion(0, detachedLine()))
                .isNotInstanceOf(LineScopedMutation.class);
        }

        @Test
        void testElementMutationsAreLineScoped() {
            var line = detachedLine();
            assertThat(new ElementInsertion(line, 0, ElementType.CROTCHET.newInstance()))
                .isInstanceOf(LineScopedMutation.class);
            assertThat(new ElementDeletion(line, 0, ElementType.CROTCHET.newInstance()))
                .isInstanceOf(LineScopedMutation.class);
            assertThat(new ElementRangeDeletion(line, 0, 0, List.of(ElementType.CROTCHET.newInstance())))
                .isInstanceOf(LineScopedMutation.class);
            assertThat(new ElementModification(line, 0, EnumSet.of(ElementField.PITCH), ElementType.CROTCHET.newInstance()))
                .isInstanceOf(LineScopedMutation.class);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class LineLevelMutations {

        @Test
        void testLineKeyChangeExposesFields() {
            var line = detachedLine();
            var mutation = new LineKeyChange(line, KeyField.KEY_TYPE, KeyType.FLATS, KeyType.SHARPS);

            assertThat(mutation.line()).isSameAs(line);
            assertThat(mutation.field()).isEqualTo(KeyField.KEY_TYPE);
            assertThat(mutation.oldValue()).isEqualTo(KeyType.FLATS);
            assertThat(mutation.newValue()).isEqualTo(KeyType.SHARPS);
            assertThat(mutation.getLine()).isSameAs(line);
        }

        @Test
        void testLineLayoutChangeExposesFields() {
            var line = detachedLine();
            var mutation = new LineLayoutChange(line, LineLayoutField.LYRICS_Y_POS_SS, 1.0, 2.0);

            assertThat(mutation.line()).isSameAs(line);
            assertThat(mutation.field()).isEqualTo(LineLayoutField.LYRICS_Y_POS_SS);
            assertThat(mutation.oldValue()).isEqualTo(1.0);
            assertThat(mutation.newValue()).isEqualTo(2.0);
            assertThat(mutation.getLine()).isSameAs(line);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class PropertyMutations {

        @Test
        void testFontChangeExposesFields() {
            var oldFonts = DocumentFonts.defaultsFromPrefs();
            var newFonts = DocumentFonts.defaultsFromPrefs();
            var mutation = new FontChange(oldFonts, newFonts);

            assertThat(mutation.oldFonts()).isSameAs(oldFonts);
            assertThat(mutation.newFonts()).isSameAs(newFonts);
        }

        @Test
        void testLayoutChangeExposesFields() {
            var mutation = new LayoutChange(LayoutField.LINE_WIDTH_SS, 50.0, 60.0);

            assertThat(mutation.field()).isEqualTo(LayoutField.LINE_WIDTH_SS);
            assertThat(mutation.oldValue()).isEqualTo(50.0);
            assertThat(mutation.newValue()).isEqualTo(60.0);
        }

        @Test
        void testLayoutChangeAcceptsNullValues() {
            var mutation = new LayoutChange(LayoutField.ATTRIBUTION_START_Y_SS, null, 5.0);

            assertThat(mutation.oldValue()).isNull();
            assertThat(mutation.newValue()).isEqualTo(5.0);
        }

        @Test
        void testLyricsChangeExposesFields() {
            var mutation = new LyricsChange(LyricsField.UNDER, "old", "new");

            assertThat(mutation.field()).isEqualTo(LyricsField.UNDER);
            assertThat(mutation.oldText()).isEqualTo("old");
            assertThat(mutation.newText()).isEqualTo("new");
        }

        @Test
        void testMetadataChangeExposesFields() {
            var oldTempo = new Tempo();
            var newTempo = new Tempo();
            var mutation = new MetadataChange(MetadataField.TEMPO, oldTempo, newTempo);

            assertThat(mutation.field()).isEqualTo(MetadataField.TEMPO);
            assertThat(mutation.oldValue()).isSameAs(oldTempo);
            assertThat(mutation.newValue()).isSameAs(newTempo);
        }

        @Test
        void testMetadataChangeAcceptsNullValues() {
            var mutation = new MetadataChange(MetadataField.TITLE, null, "Some Title");

            assertThat(mutation.oldValue()).isNull();
            assertThat(mutation.newValue()).isEqualTo("Some Title");
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class RangeElementMutations {

        @Test
        void testRangeElementAdditionExposesFields() {
            var line = detachedLine();
            RangeElement element = new Ending(
                ElementType.CROTCHET.newInstance(),
                ElementType.CROTCHET.newInstance()
            );
            var mutation = new RangeElementAddition(line, element);

            assertThat(mutation.line()).isSameAs(line);
            assertThat(mutation.element()).isSameAs(element);
            assertThat(mutation.getLine()).isSameAs(line);
        }

        @Test
        void testRangeElementRemovalExposesFields() {
            var line = detachedLine();
            RangeElement element = new Ending(
                ElementType.CROTCHET.newInstance(),
                ElementType.CROTCHET.newInstance()
            );
            var mutation = new RangeElementRemoval(line, element);

            assertThat(mutation.line()).isSameAs(line);
            assertThat(mutation.element()).isSameAs(element);
            assertThat(mutation.getLine()).isSameAs(line);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class StructuralMutations {

        @Test
        void testLineDeletionExposesFields() {
            var line = detachedLine();
            var mutation = new LineDeletion(7, line);

            assertThat(mutation.lineIndex()).isEqualTo(7);
            assertThat(mutation.deletedLine()).isSameAs(line);
        }

        @Test
        void testLineInsertionExposesFields() {
            var line = detachedLine();
            var mutation = new LineInsertion(3, line);

            assertThat(mutation.lineIndex()).isEqualTo(3);
            assertThat(mutation.line()).isSameAs(line);
        }
    }

    @Test
    void testPermittedSubtypesAreExhaustive() {
        // Reflectively verify that the permits list exactly matches the known
        // set of concrete Mutation subtypes, so additions to either side are
        // caught at test time.
        var permittedNames = Arrays.stream(Mutation.class.getPermittedSubclasses())
            .map(Class::getSimpleName)
            .collect(Collectors.toSet());
        var expectedNames = Set.of(
            "ElementInsertion", "ElementDeletion", "ElementRangeDeletion",
            "ElementModification", "ElementReplacement",
            "LineInsertion", "LineDeletion",
            "LineKeyChange", "LineLayoutChange",
            "RangeElementAddition", "RangeElementRemoval",
            "BeamingAddition", "BeamingRemoval",
            "TieAddition", "TieRemoval",
            "TupletAddition", "TupletRemoval",
            "CrescendoAddition", "CrescendoRemoval",
            "DiminuendoAddition", "DiminuendoRemoval",
            "MetadataChange", "FontChange", "LayoutChange", "LyricsChange"
        );
        assertThat(permittedNames).isEqualTo(expectedNames);
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class FieldTypeValidatorBehavior {

        @Test
        void testValidatorAcceptsNullOldAndNewValues() {
            // Null values bypass the type check — no exception should be thrown.
            assertThatNoException().isThrownBy(
                () -> new MetadataChange(MetadataField.TITLE, null, null)
            );
        }

        @Test
        void testValidatorRejectsTypeMismatchOnOldValue() {
            // TITLE expects String; passing an Integer for oldValue must throw
            // with a message identifying the record, field, parameter, and types.
            assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(
                () -> new MetadataChange(MetadataField.TITLE, 42, "x")
            ).withMessageContaining("MetadataChange")
             .withMessageContaining("TITLE")
             .withMessageContaining("oldValue")
             .withMessageContaining("String")
             .withMessageContaining("Integer");
        }

        @Test
        void testValidatorRejectsTypeMismatchOnNewValue() {
            // TITLE expects String; passing an Integer for newValue must throw
            // with a message identifying the record, field, parameter, and types.
            assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(
                () -> new MetadataChange(MetadataField.TITLE, "x", 42)
            ).withMessageContaining("MetadataChange")
             .withMessageContaining("TITLE")
             .withMessageContaining("newValue")
             .withMessageContaining("String")
             .withMessageContaining("Integer");
        }
    }

    @ParameterizedTest(name = "{0}.getExpectedType() returns {1}")
    @MethodSource("keyFieldExpectedTypes")
    void testKeyFieldReturnsExpectedType(KeyField field, Class<?> expectedType) {
        assertThat(field.getExpectedType()).isEqualTo(expectedType);
    }

    static Stream<Arguments> keyFieldExpectedTypes() {
        return Stream.of(
            Arguments.of(KeyField.ACCIDENTAL_COUNT, Integer.class),
            Arguments.of(KeyField.KEY_TYPE, KeyType.class)
        );
    }

    @ParameterizedTest(name = "{0}.getExpectedType() returns {1}")
    @MethodSource("layoutFieldExpectedTypes")
    void testLayoutFieldReturnsExpectedType(LayoutField field, Class<?> expectedType) {
        assertThat(field.getExpectedType()).isEqualTo(expectedType);
    }

    static Stream<Arguments> layoutFieldExpectedTypes() {
        return Stream.of(
            Arguments.of(LayoutField.LINE_WIDTH_SS, Double.class),
            Arguments.of(LayoutField.ROW_HEIGHT_ADJUSTMENT_SS, Double.class),
            Arguments.of(LayoutField.ATTRIBUTION_START_Y_SS, Double.class)
        );
    }

    @ParameterizedTest(name = "{0}.getExpectedType() returns {1}")
    @MethodSource("lineLayoutFieldExpectedTypes")
    void testLineLayoutFieldReturnsExpectedType(LineLayoutField field, Class<?> expectedType) {
        assertThat(field.getExpectedType()).isEqualTo(expectedType);
    }

    static Stream<Arguments> lineLayoutFieldExpectedTypes() {
        return Stream.of(
            Arguments.of(LineLayoutField.LYRICS_Y_POS_SS, Double.class),
            Arguments.of(LineLayoutField.ELEMENT_SPACING_RATIO, Float.class)
        );
    }

    @ParameterizedTest(name = "{0}.getExpectedType() returns {1}")
    @MethodSource("metadataFieldExpectedTypes")
    void testMetadataFieldReturnsExpectedType(MetadataField field, Class<?> expectedType) {
        assertThat(field.getExpectedType()).isEqualTo(expectedType);
    }

    static Stream<Arguments> metadataFieldExpectedTypes() {
        return Stream.of(
            Arguments.of(MetadataField.TITLE, String.class),
            Arguments.of(MetadataField.PLACE, String.class),
            Arguments.of(MetadataField.YEAR, String.class),
            Arguments.of(MetadataField.MONTH, Integer.class),
            Arguments.of(MetadataField.DAY, Integer.class),
            Arguments.of(MetadataField.ATTRIBUTION, String.class),
            Arguments.of(MetadataField.NUMBER, String.class),
            Arguments.of(MetadataField.TEMPO, Tempo.class),
            Arguments.of(MetadataField.DEFAULT_KEY_ACCIDENTAL_COUNT, Integer.class),
            Arguments.of(MetadataField.DEFAULT_KEY_TYPE, KeyType.class),
            Arguments.of(MetadataField.FOOTNOTES, String.class),
            Arguments.of(MetadataField.UNOFFICIAL_TRANSLATION, Boolean.class)
        );
    }

    @Test
    void testElementFieldDurationAffectingContainsExactlyDotCount() {
        // DURATION_AFFECTING drives tuplet-removal policy; guard against accidental additions.
        assertThat(ElementField.DURATION_AFFECTING).isEqualTo(EnumSet.of(ElementField.DOT_COUNT));
    }
}
