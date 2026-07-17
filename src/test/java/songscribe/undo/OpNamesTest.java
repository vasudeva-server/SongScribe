/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package songscribe.undo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;
import songscribe.undo.OpNames;

/**
 * Unit tests for {@link OpNames} — the pure Tier-B label-assembly helper. Each test
 * drives one branch of a helper and asserts it resolves to the expected
 * {@code Strings.*} label, proving the {@code ElementType -> category} taxonomy is
 * decoded correctly (single vs. plural vs. mixed, note/grace folding, slide subtype,
 * lyric add/edit/delete transitions).
 */
class OpNamesTest extends UnitTest {

    @Nested
    class DeleteLabel {

        @Test
        void testSingleNoteIsSingular() {
            assertThat(OpNames.deleteLabel(List.of(ElementType.CROTCHET)))
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_DELETE_NOTE));
        }

        @Test
        void testMultipleNotesIsPlural() {
            assertThat(OpNames.deleteLabel(List.of(ElementType.CROTCHET, ElementType.QUAVER)))
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_DELETE_NOTES));
        }

        @Test
        void testNoteAndGraceFoldToNotePlural() {
            // A pitched note and a grace note share the NOTE category, so two of them
            // yield the plural "Delete Notes" rather than the generic "Delete Elements".
            assertThat(OpNames.deleteLabel(List.of(ElementType.CROTCHET, ElementType.GRACE_QUAVER)))
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_DELETE_NOTES));
        }

        @Test
        void testMixedCategoriesIsGeneric() {
            assertThat(OpNames.deleteLabel(List.of(ElementType.CROTCHET, ElementType.SINGLE_BARLINE)))
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_DELETE_ELEMENTS));
        }

        @Test
        void testSingleRestIsSingular() {
            assertThat(OpNames.deleteLabel(List.of(ElementType.CROTCHET_REST)))
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_DELETE_REST));
        }

        @Test
        void testMultipleRestsIsPlural() {
            assertThat(OpNames.deleteLabel(List.of(ElementType.CROTCHET_REST, ElementType.QUAVER_REST)))
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_DELETE_RESTS));
        }

        @Test
        void testSingleBarline() {
            assertThat(OpNames.deleteLabel(List.of(ElementType.SINGLE_BARLINE)))
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_DELETE_BARLINE));
        }

        @Test
        void testSingleRepeat() {
            assertThat(OpNames.deleteLabel(List.of(ElementType.REPEAT_LEFT)))
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_DELETE_REPEAT));
        }

        @Test
        void testSingleBreathMark() {
            assertThat(OpNames.deleteLabel(List.of(ElementType.BREATH_MARK)))
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_DELETE_BREATH_MARK));
        }
    }

    @Nested
    class AddLabel {

        @Test
        void testNote() {
            assertThat(OpNames.addLabel(ElementType.CROTCHET))
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_ADD_NOTE));
        }

        @Test
        void testRest() {
            assertThat(OpNames.addLabel(ElementType.CROTCHET_REST))
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_ADD_REST));
        }

        @Test
        void testBarline() {
            assertThat(OpNames.addLabel(ElementType.SINGLE_BARLINE))
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_ADD_BARLINE));
        }

        @Test
        void testRepeat() {
            assertThat(OpNames.addLabel(ElementType.REPEAT_LEFT))
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_ADD_REPEAT));
        }

        @Test
        void testBreathMark() {
            assertThat(OpNames.addLabel(ElementType.BREATH_MARK))
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_ADD_BREATH_MARK));
        }

        @Test
        void testGraceNoteDoesNotFoldToNote() {
            // A grace note gets its own "Add Grace Note" label rather than folding
            // into the plain "Add Note" that a pitched note would produce.
            assertThat(OpNames.addLabel(ElementType.GRACE_QUAVER))
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_ADD_GRACE_NOTE));
        }
    }

    @Nested
    class SlideLabel {

        @Test
        void testAddGlissando() {
            assertThat(OpNames.addSlideLabel(false))
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_ADD_GLISSANDO));
        }

        @Test
        void testAddFall() {
            assertThat(OpNames.addSlideLabel(true))
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_ADD_FALL));
        }

        @Test
        void testDeleteGlissando() {
            assertThat(OpNames.deleteSlideLabel(new StaffElement.Glissando()))
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_DELETE_GLISSANDO));
        }

        @Test
        void testDeleteFall() {
            assertThat(OpNames.deleteSlideLabel(new StaffElement.Fall()))
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_DELETE_FALL));
        }
    }

    @Nested
    class LineAndLyricLabel {

        private static final String NON_EMPTY = "word";
        private static final String OTHER_NON_EMPTY = "other";
        private static final String EMPTY = "";

        @Test
        void testDeleteLine() {
            assertThat(OpNames.deleteLineLabel())
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_DELETE_LINE));
        }

        @Test
        void testEmptyToNonEmptyIsAdd() {
            assertThat(OpNames.lyricLabel(EMPTY, NON_EMPTY))
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_ADD_LYRIC));
        }

        @Test
        void testNonEmptyToEmptyIsDelete() {
            assertThat(OpNames.lyricLabel(NON_EMPTY, EMPTY))
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_DELETE_LYRIC));
        }

        @Test
        void testNonEmptyToNonEmptyIsEdit() {
            assertThat(OpNames.lyricLabel(NON_EMPTY, OTHER_NON_EMPTY))
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_EDIT_LYRIC));
        }
    }
}
