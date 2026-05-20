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
import songscribe.model.Annotation;
import songscribe.model.ElementType;
import songscribe.model.Line;
import songscribe.ui.layout.AnnotationAttachment;
import songscribe.ui.layout.DynamicAttachment;
import songscribe.ui.layout.DynamicAttachment.DynamicType;

class FormatMigratorTest extends UnitTest {

    @SuppressWarnings({ "PackageVisibleInnerClass", "DataFlowIssue" })
    @Nested
    class MigrateAnnotationDynamics {

        // T45: Note with annotation "f" → converted to DynamicAttachment(FORTE)
        @Test
        void testAnnotationMatchingForteSymbol() {
            var line = lineWithAnnotation("f");
            FormatMigrator.migrateAnnotationDynamics(List.of(line));

            var dynamic = line.getElement(0).findAttachment(DynamicAttachment.class);
            assertThat(dynamic).isNotNull();

            //noinspection ConstantValue -- need for NullAway
            if (dynamic == null) {
                return;
            }

            assertThat(dynamic.getType()).isEqualTo(DynamicType.FORTE);
        }

        // T46: Note with annotation "pp" → converted to DynamicAttachment(PIANISSIMO)
        @Test
        void testAnnotationMatchingPianissimoSymbol() {
            var line = lineWithAnnotation("pp");
            FormatMigrator.migrateAnnotationDynamics(List.of(line));

            var dynamic = line.getElement(0).findAttachment(DynamicAttachment.class);
            assertThat(dynamic).isNotNull();

            //noinspection ConstantValue -- need for NullAway
            if (dynamic == null) {
                return;
            }

            assertThat(dynamic.getType()).isEqualTo(DynamicType.PIANISSIMO);
        }

        // T47: Note with annotation "forte" (non-symbol text) → not converted, annotation kept
        @Test
        void testAnnotationWithNonSymbolTextNotConverted() {
            var line = lineWithAnnotation("forte");
            FormatMigrator.migrateAnnotationDynamics(List.of(line));

            var note = line.getElement(0);
            assertThat(note.findAttachment(DynamicAttachment.class)).isNull();
            assertThat(note.findAttachment(AnnotationAttachment.class)).isNotNull();
        }

        // T48: Note with annotation "F" (wrong case) → not converted, annotation kept
        @Test
        void testAnnotationWithWrongCaseNotConverted() {
            var line = lineWithAnnotation("F");
            FormatMigrator.migrateAnnotationDynamics(List.of(line));

            var note = line.getElement(0);
            assertThat(note.findAttachment(DynamicAttachment.class)).isNull();
            assertThat(note.findAttachment(AnnotationAttachment.class)).isNotNull();
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

            assertThat(note.findAttachment(AnnotationAttachment.class)).isNull();

            var dynamic = note.findAttachment(DynamicAttachment.class);
            assertThat(dynamic).isSameAs(existingDynamic);

            if (dynamic == null) {
                return;
            }

            assertThat(dynamic.getType()).isEqualTo(DynamicType.MEZZO_FORTE);
        }

        // Verify that a matching AnnotationAttachment is removed after conversion
        @Test
        void testMatchingAnnotationAttachmentRemoved() {
            var line = lineWithAnnotation("mf");
            var note = line.getElement(0);

            FormatMigrator.migrateAnnotationDynamics(List.of(line));

            assertThat(note.findAttachment(AnnotationAttachment.class)).isNull();
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class MigrateFinalTerminal {

        // T52: FINAL_DOUBLE_BARLINE on a non-last line → stripped; last line's own final preserved.
        @Test
        void testFinalBarlineOnNonLastLineIsRemoved() {
            var nonLast = lineWith(ElementType.CROTCHET, ElementType.FINAL_DOUBLE_BARLINE);
            var last = lineWith(ElementType.CROTCHET, ElementType.FINAL_DOUBLE_BARLINE);
            FormatMigrator.migrateFinalTerminal(List.of(nonLast, last));

            assertThat(nonLast.elementCount()).isEqualTo(1);
            assertThat(nonLast.getElement(0).getType()).isEqualTo(ElementType.CROTCHET);
            assertThat(last.elementCount()).isEqualTo(2);
            assertThat(last.getElement(1).getType()).isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
        }

        // T53: Multiple FINAL_DOUBLE_BARLINE elements on a non-last line → all removed.
        @Test
        void testMultipleFinalBarlinesOnNonLastLineAllRemoved() {
            var nonLast = lineWith(
                ElementType.FINAL_DOUBLE_BARLINE,
                ElementType.CROTCHET,
                ElementType.FINAL_DOUBLE_BARLINE
            );
            var last = lineWith(ElementType.FINAL_DOUBLE_BARLINE);
            FormatMigrator.migrateFinalTerminal(List.of(nonLast, last));

            assertThat(nonLast.elementCount()).isEqualTo(1);
            assertThat(nonLast.getElement(0).getType()).isEqualTo(ElementType.CROTCHET);
        }

        // Interior REPEAT_RIGHT on a non-last line is not stripped.
        @Test
        void testRepeatRightOnNonLastLineIsPreserved() {
            var nonLast = lineWith(ElementType.CROTCHET, ElementType.REPEAT_RIGHT);
            var last = lineWith(ElementType.CROTCHET, ElementType.FINAL_DOUBLE_BARLINE);
            FormatMigrator.migrateFinalTerminal(List.of(nonLast, last));

            assertThat(nonLast.elementCount()).isEqualTo(2);
            assertThat(nonLast.getElement(1).getType()).isEqualTo(ElementType.REPEAT_RIGHT);
        }

        // T54: Last line ends in SINGLE_BARLINE → replaced with FINAL_DOUBLE_BARLINE.
        @Test
        void testSingleBarlineAtEndIsReplaced() {
            var last = lineWith(ElementType.CROTCHET, ElementType.SINGLE_BARLINE);
            FormatMigrator.migrateFinalTerminal(List.of(last));

            assertThat(last.elementCount()).isEqualTo(2);
            assertThat(last.getElement(1).getType()).isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
        }

        // T55: Last line ends in DOUBLE_BARLINE → replaced.
        @Test
        void testDoubleBarlineAtEndIsReplaced() {
            var last = lineWith(ElementType.CROTCHET, ElementType.DOUBLE_BARLINE);
            FormatMigrator.migrateFinalTerminal(List.of(last));

            assertThat(last.getElement(last.elementCount() - 1).getType())
                .isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
        }

        // T56: Last line ends in REPEAT_RIGHT → preserved as valid terminal (no-op).
        @Test
        void testRepeatRightAtEndIsPreservedAsTerminal() {
            var last = lineWith(ElementType.CROTCHET, ElementType.REPEAT_RIGHT);
            var originalRepeat = last.getElement(1);
            FormatMigrator.migrateFinalTerminal(List.of(last));

            assertThat(last.elementCount()).isEqualTo(2);
            assertThat(last.getElement(1)).isSameAs(originalRepeat);
        }

        // Misplaced FINAL_DOUBLE_BARLINE before a REPEAT_RIGHT: the barline is stripped,
        // leaving REPEAT_RIGHT as the terminal, which is a valid terminal (no-op).
        @Test
        void testMisplacedFinalBarlineBeforeRepeatRightLeavesRepeatAsTerminal() {
            var last = lineWith(
                ElementType.CROTCHET, ElementType.FINAL_DOUBLE_BARLINE, ElementType.REPEAT_RIGHT);
            var originalRepeat = last.getElement(2);
            FormatMigrator.migrateFinalTerminal(List.of(last));

            assertThat(last.elementCount()).isEqualTo(2);
            assertThat(last.getElement(0).getType()).isEqualTo(ElementType.CROTCHET);
            assertThat(last.getElement(1)).isSameAs(originalRepeat);
        }

        // T57: Last line ends in REPEAT_LEFT_RIGHT → replaced.
        @Test
        void testRepeatLeftRightAtEndIsReplaced() {
            var last = lineWith(ElementType.CROTCHET, ElementType.REPEAT_LEFT_RIGHT);
            FormatMigrator.migrateFinalTerminal(List.of(last));

            assertThat(last.getElement(last.elementCount() - 1).getType())
                .isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
        }

        // T58: Last line ends in REPEAT_LEFT (non-replaceable) → FINAL_DOUBLE_BARLINE appended.
        @Test
        void testRepeatLeftAtEndGetsBarlineAppended() {
            var last = lineWith(ElementType.CROTCHET, ElementType.REPEAT_LEFT);
            FormatMigrator.migrateFinalTerminal(List.of(last));

            assertThat(last.elementCount()).isEqualTo(3);
            assertThat(last.getElement(2).getType()).isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
        }

        // T59: Last line ends in a note → FINAL_DOUBLE_BARLINE appended.
        @Test
        void testNoteAtEndGetsFinalBarlineAppended() {
            var last = lineWith(ElementType.CROTCHET);
            FormatMigrator.migrateFinalTerminal(List.of(last));

            assertThat(last.elementCount()).isEqualTo(2);
            assertThat(last.getElement(1).getType()).isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
        }

        // T60: Last line already ends in FINAL_DOUBLE_BARLINE → no-op.
        @Test
        void testAlreadyEndsInFinalBarlineIsNoOp() {
            var last = lineWith(ElementType.CROTCHET, ElementType.FINAL_DOUBLE_BARLINE);
            var originalFinal = last.getElement(1);
            FormatMigrator.migrateFinalTerminal(List.of(last));

            assertThat(last.elementCount()).isEqualTo(2);
            assertThat(last.getElement(1)).isSameAs(originalFinal);
        }

        // T61: Last line is empty → FINAL_DOUBLE_BARLINE appended.
        @Test
        void testEmptyLastLineGetsFinalBarlineAppended() {
            var last = detachedLine();
            FormatMigrator.migrateFinalTerminal(List.of(last));

            assertThat(last.elementCount()).isEqualTo(1);
            assertThat(last.getElement(0).getType()).isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
        }

        // T62: Misplaced FINAL_DOUBLE_BARLINE (not last element) on last line → removed before install.
        @Test
        void testMisplacedFinalBarlineOnLastLineIsStrippedBeforeInstall() {
            // FINAL in middle of last line — should be stripped, then note ends it → appended.
            var last = lineWith(ElementType.FINAL_DOUBLE_BARLINE, ElementType.CROTCHET);
            FormatMigrator.migrateFinalTerminal(List.of(last));

            assertThat(last.elementCount()).isEqualTo(2);
            assertThat(last.getElement(0).getType()).isEqualTo(ElementType.CROTCHET);
            assertThat(last.getElement(1).getType()).isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
        }
    }

    // -- Helpers --

    /** Creates a line containing elements of the given types (in order), unattached to a song. */
    private static Line lineWith(ElementType... types) {
        var line = detachedLine();

        for (var type : types) {
            line.addElement(type.newInstance());
        }

        return line;
    }

    /** Creates a line containing a single crotchet with the given annotation text. */
    private static Line lineWithAnnotation(String text) {
        var line = detachedLine();
        var note = ElementType.CROTCHET.newInstance();
        line.addElement(note);
        note.addAttachment(new AnnotationAttachment(note, new Annotation(text)));
        return line;
    }
}
