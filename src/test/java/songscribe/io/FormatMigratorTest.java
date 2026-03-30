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

package songscribe.io;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.music.Annotation;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.ui.layout.AnnotationAttachment;
import songscribe.ui.layout.DynamicAttachment;
import songscribe.ui.layout.DynamicAttachment.DynamicType;

class FormatMigratorTest extends UnitTest {

    @Nested
    class MigrateAnnotationDynamics {

        // T45: Note with annotation "f" → converted to DynamicAttachment(FORTE)
        @Test
        void testAnnotationMatchingForteSymbol() {
            var line = lineWithAnnotation("f");
            FormatMigrator.migrateAnnotationDynamics(List.of(line));

            var dynamic = line.getElement(0).findAttachment(DynamicAttachment.class);
            assertThat(dynamic).isNotNull();
            if (dynamic == null) return;
            assertThat(dynamic.getType()).isEqualTo(DynamicType.FORTE);
        }

        // T46: Note with annotation "pp" → converted to DynamicAttachment(PIANISSIMO)
        @Test
        void testAnnotationMatchingPianissimoSymbol() {
            var line = lineWithAnnotation("pp");
            FormatMigrator.migrateAnnotationDynamics(List.of(line));

            var dynamic = line.getElement(0).findAttachment(DynamicAttachment.class);
            assertThat(dynamic).isNotNull();
            if (dynamic == null) return;
            assertThat(dynamic.getType()).isEqualTo(DynamicType.PIANISSIMO);
        }

        // T47: Note with annotation "forte" (non-symbol text) → not converted, annotation kept
        @Test
        void testAnnotationWithNonSymbolTextNotConverted() {
            var line = lineWithAnnotation("forte");
            FormatMigrator.migrateAnnotationDynamics(List.of(line));

            var note = line.getElement(0);
            assertThat(note.findAttachment(DynamicAttachment.class)).isNull();
            assertThat(note.getAnnotation()).isNotNull();
        }

        // T48: Note with annotation "F" (wrong case) → not converted, annotation kept
        @Test
        void testAnnotationWithWrongCaseNotConverted() {
            var line = lineWithAnnotation("F");
            FormatMigrator.migrateAnnotationDynamics(List.of(line));

            var note = line.getElement(0);
            assertThat(note.findAttachment(DynamicAttachment.class)).isNull();
            assertThat(note.getAnnotation()).isNotNull();
        }

        // T49: Note with annotation "f" and existing DynamicAttachment →
        //      annotation removed, existing attachment preserved (no duplicate)
        @Test
        void testAnnotationRemovedWhenDynamicAlreadyExists() {
            var line = lineWithAnnotation("f");
            var note = line.getElement(0);
            var existingDynamic = new DynamicAttachment(note, DynamicType.MEZZO_FORTE);
            note.addAttachment(existingDynamic);

            FormatMigrator.migrateAnnotationDynamics(List.of(line));

            assertThat(note.getAnnotation()).isNull();
            assertThat(note.findAttachment(AnnotationAttachment.class)).isNull();

            var dynamic = note.findAttachment(DynamicAttachment.class);
            assertThat(dynamic).isSameAs(existingDynamic);
            if (dynamic == null) return;
            assertThat(dynamic.getType()).isEqualTo(DynamicType.MEZZO_FORTE);
        }

        // Verify that a matching annotation is removed from the legacy field
        @Test
        void testMatchingAnnotationClearedFromLegacyField() {
            var line = lineWithAnnotation("mp");
            FormatMigrator.migrateAnnotationDynamics(List.of(line));

            assertThat(line.getElement(0).getAnnotation()).isNull();
        }

        // Verify that a matching AnnotationAttachment is also removed
        @Test
        void testMatchingAnnotationAttachmentRemoved() {
            var line = lineWithAnnotation("mf");
            var note = line.getElement(0);
            var annotation = note.getAnnotation();
            assertThat(annotation).isNotNull();
            if (annotation == null) return;
            note.addAttachment(new AnnotationAttachment(note, annotation));

            FormatMigrator.migrateAnnotationDynamics(List.of(line));

            assertThat(note.findAttachment(AnnotationAttachment.class)).isNull();
        }
    }

    // -- Helpers --

    /** Creates a line containing a single crotchet with the given annotation text. */
    private static Line lineWithAnnotation(String text) {
        var line = new Line();
        var note = ElementType.CROTCHET.newInstance();
        line.addElement(note);
        note.setAnnotation(new Annotation(text));
        return line;
    }
}
