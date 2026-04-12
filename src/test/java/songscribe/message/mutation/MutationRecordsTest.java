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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.music.ElementType;
import songscribe.music.KeyType;
import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.music.Tempo;
import songscribe.ui.layout.Ending;
import songscribe.ui.layout.RangeElement;

class MutationRecordsTest extends UnitTest {

    @Nested
    class ElementMutations {

        @Test
        void testElementDeletionExposesFields() {
            var line = new Line();
            var deleted = ElementType.CROTCHET.newInstance();
            var mutation = new ElementDeletion(line, 4, deleted);

            assertThat(mutation.line()).isSameAs(line);
            assertThat(mutation.index()).isEqualTo(4);
            assertThat(mutation.deletedElement()).isSameAs(deleted);
            assertThat(mutation.getLine()).isSameAs(line);
        }

        @Test
        void testElementInsertionExposesFields() {
            var line = new Line();
            var element = ElementType.CROTCHET.newInstance();
            var mutation = new ElementInsertion(line, 2, element);

            assertThat(mutation.line()).isSameAs(line);
            assertThat(mutation.index()).isEqualTo(2);
            assertThat(mutation.element()).isSameAs(element);
            assertThat(mutation.getLine()).isSameAs(line);
        }

        @Test
        void testElementModificationExposesFields() {
            var line = new Line();
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
            var line = new Line();
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

    @Nested
    class LineScopedInterfaceMembership {

        @Test
        void testCompositionScopedMutationsAreNotLineScoped() {
            assertThat(new MetadataChange(MetadataField.TITLE, "a", "b"))
                .isNotInstanceOf(LineScopedMutation.class);
            assertThat(new LayoutChange(LayoutField.LINE_WIDTH_SS, 1.0, 2.0))
                .isNotInstanceOf(LineScopedMutation.class);
            assertThat(new LyricsChange(LyricsField.MAIN, "a", "b"))
                .isNotInstanceOf(LineScopedMutation.class);
            assertThat(new LineInsertion(0, new Line()))
                .isNotInstanceOf(LineScopedMutation.class);
            assertThat(new LineDeletion(0, new Line()))
                .isNotInstanceOf(LineScopedMutation.class);
        }

        @Test
        void testElementMutationsAreLineScoped() {
            var line = new Line();
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

    @Nested
    class LineLevelMutations {

        @Test
        void testLineKeyChangeExposesFields() {
            var line = new Line();
            var mutation = new LineKeyChange(line, KeyField.KEY_TYPE, KeyType.FLATS, KeyType.SHARPS);

            assertThat(mutation.line()).isSameAs(line);
            assertThat(mutation.field()).isEqualTo(KeyField.KEY_TYPE);
            assertThat(mutation.oldValue()).isEqualTo(KeyType.FLATS);
            assertThat(mutation.newValue()).isEqualTo(KeyType.SHARPS);
            assertThat(mutation.getLine()).isSameAs(line);
        }

        @Test
        void testLineLayoutChangeExposesFields() {
            var line = new Line();
            var mutation = new LineLayoutChange(line, LineLayoutField.LYRICS_Y_POS_SS, 1.0, 2.0);

            assertThat(mutation.line()).isSameAs(line);
            assertThat(mutation.field()).isEqualTo(LineLayoutField.LYRICS_Y_POS_SS);
            assertThat(mutation.oldValue()).isEqualTo(1.0);
            assertThat(mutation.newValue()).isEqualTo(2.0);
            assertThat(mutation.getLine()).isSameAs(line);
        }
    }

    @Nested
    class PropertyMutations {

        @Test
        void testFontChangeExposesFields() {
            var oldFont = new Font("Dialog", Font.PLAIN, 12);
            var newFont = new Font("Dialog", Font.BOLD, 14);
            var mutation = new FontChange(FontField.LYRICS, oldFont, newFont);

            assertThat(mutation.field()).isEqualTo(FontField.LYRICS);
            assertThat(mutation.oldFont()).isSameAs(oldFont);
            assertThat(mutation.newFont()).isSameAs(newFont);
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

    @Nested
    class RangeElementMutations {

        @Test
        void testRangeElementAdditionExposesFields() {
            var line = new Line();
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
            var line = new Line();
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

    @Nested
    class StructuralMutations {

        @Test
        void testLineDeletionExposesFields() {
            var line = new Line();
            var mutation = new LineDeletion(7, line);

            assertThat(mutation.lineIndex()).isEqualTo(7);
            assertThat(mutation.deletedLine()).isSameAs(line);
        }

        @Test
        void testLineInsertionExposesFields() {
            var line = new Line();
            var mutation = new LineInsertion(3, line);

            assertThat(mutation.lineIndex()).isEqualTo(3);
            assertThat(mutation.line()).isSameAs(line);
        }
    }
}
