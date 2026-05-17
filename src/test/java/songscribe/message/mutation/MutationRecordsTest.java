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

import module java.desktop;

import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.font.DocumentFonts;
import songscribe.music.BeamSpan;
import songscribe.music.DynamicsSpan;
import songscribe.music.ElementType;
import songscribe.music.KeyType;
import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.music.Tempo;
import songscribe.music.TieSpan;
import songscribe.music.TupletSpan;
import songscribe.ui.layout.Ending;
import songscribe.ui.layout.RangeElement;

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
            return Stream.of(
                Arguments.of("BeamingAddition",    new BeamingAddition(line, new BeamSpan(1, 3)), line),
                Arguments.of("BeamingRemoval",     new BeamingRemoval(line, new BeamSpan(2, 4)), line),
                Arguments.of("TieAddition",        new TieAddition(line, new TieSpan(0, 1)), line),
                Arguments.of("TieRemoval",         new TieRemoval(line, new TieSpan(3, 4)), line),
                Arguments.of("TupletAddition",     new TupletAddition(line, new TupletSpan(0, 2, 3)), line),
                Arguments.of("TupletRemoval",      new TupletRemoval(line, new TupletSpan(1, 3, 5)), line),
                Arguments.of("CrescendoAddition",  new CrescendoAddition(line, new DynamicsSpan(0, 2)), line),
                Arguments.of("CrescendoRemoval",   new CrescendoRemoval(line, new DynamicsSpan(1, 3)), line),
                Arguments.of("DiminuendoAddition", new DiminuendoAddition(line, new DynamicsSpan(2, 4)), line),
                Arguments.of("DiminuendoRemoval",  new DiminuendoRemoval(line, new DynamicsSpan(3, 5)), line)
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
            var mutation = new LayoutChange(LayoutField.TOP_PADDING_SS, null, 5.0);

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
                ElementType.CROTCHET.newInstance(),
                Ending.Type.FIRST
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
                ElementType.CROTCHET.newInstance(),
                Ending.Type.SECOND
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
}
