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
import songscribe.dom.SlideZone;
import songscribe.dom.StaffElement;
import songscribe.undo.OpNames;

/**
 * Exercises {@link OpNames}: which name an edit is given, for each way the name depends on
 * what the edit acted on. The methods are pure functions of their arguments, so every case
 * is a call and an assertion with no fixture.
 *
 * <p><b>{@link OpNames#deleteLabel} — the three classes of input it distinguishes.</b> One
 * element of a category yields the singular name, several of one category the plural, and a
 * mix the generic name. Each category is exercised, since the category set is finite and
 * small; the note/grace folding is a case of the second class, not a fourth class, because
 * the promise is that they share a category. Nothing is asserted for an empty list: the
 * contract states a non-empty precondition and promises nothing beyond it.
 *
 * <p><b>{@link OpNames#addLabel} — one case per category, plus the two that are not
 * categories.</b> A grace note is named separately rather than folding into {@code Note},
 * which is the one place the two labels' taxonomies deliberately differ. A type in no
 * category throws, which is the clause that keeps a future {@link ElementType} from being
 * quietly labelled as something it is not. That last case is not present today.
 *
 * <p><b>The subtype labels</b> — slide, hairpin, articulation and attachment names are each
 * chosen from a small closed set, so each set belongs here enumerated in full. Present
 * today: both slide subtypes, in each direction. Not present: the two hairpin kinds, the
 * two articulation types, the five attachment kinds, and the fixed names
 * ({@code deleteEndingLabel} and the four {@code remove*Label} methods), each of which
 * promises one specific name for one specific edit.
 *
 * <p><b>{@link OpNames#lyricLabel} — a transition, not a value.</b> The three classes are
 * empty → non-empty, non-empty → empty, and everything else; the third is asserted with two
 * different non-empty strings, which is what distinguishes it from the first two.
 *
 * <p>Expected names are resolved through the same {@link Strings} constants production uses.
 * The promise is which name is chosen for a given input, not what that name reads as in one
 * locale, and a test spelling out the English would fail on a translation that changed
 * nothing about the promise.
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
            assertThat(OpNames.addSlideLabel(SlideZone.GLISSANDO))
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_ADD_GLISSANDO));
        }

        @Test
        void testAddFall() {
            assertThat(OpNames.addSlideLabel(SlideZone.FALL))
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
