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
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.ArticulationType;
import songscribe.dom.BeatChange;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.Crescendo;
import songscribe.dom.Diminuendo;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.Duration;
import songscribe.dom.ElementType;
import songscribe.dom.FermataAttachment;
import songscribe.dom.SlideZone;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementFactory;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;
import songscribe.undo.OpNames;

/**
 * Exercises {@link OpNames}: which name an edit is given, for each way the name depends on
 * what the edit acted on. The methods are pure functions of their arguments, so every case
 * is a call and an assertion, with a fixture no larger than the argument the method under
 * test declares — a bare {@link StaffElement} pair to anchor a hairpin, never a {@code Line}
 * or a {@code Song}.
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
 * chosen from a small closed set, so each set belongs here enumerated in full: both slide
 * subtypes in each direction, the two hairpin kinds, the two articulation types, and the
 * five attachment kinds.
 *
 * <p><b>The fixed names</b> — {@code deleteEndingLabel} and the five simple
 * {@code remove*Label} methods each promise exactly one name for one specific edit, with no
 * argument to vary. Each still gets its own case: a fixed method untested is a contract
 * clause untested, the same as any other.
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
        void testMultipleBarlinesIsPlural() {
            assertThat(OpNames.deleteLabel(List.of(ElementType.SINGLE_BARLINE, ElementType.DOUBLE_BARLINE)))
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_DELETE_BARLINES));
        }

        @Test
        void testSingleRepeat() {
            assertThat(OpNames.deleteLabel(List.of(ElementType.REPEAT_LEFT)))
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_DELETE_REPEAT));
        }

        @Test
        void testMultipleRepeatsIsPlural() {
            assertThat(OpNames.deleteLabel(List.of(ElementType.REPEAT_LEFT, ElementType.REPEAT_RIGHT)))
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_DELETE_REPEATS));
        }

        @Test
        void testSingleBreathMark() {
            assertThat(OpNames.deleteLabel(List.of(ElementType.BREATH_MARK)))
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_DELETE_BREATH_MARK));
        }

        @Test
        void testMultipleBreathMarksIsPlural() {
            assertThat(OpNames.deleteLabel(List.of(ElementType.BREATH_MARK, ElementType.BREATH_MARK)))
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_DELETE_BREATH_MARKS));
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
        void testDeleteEnding() {
            assertThat(OpNames.deleteEndingLabel())
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_DELETE_ENDING));
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

    @Nested
    class HairpinLabel {

        @Test
        void testCrescendo() {
            var anchor = StaffElementFactory.crotchet();
            var end = StaffElementFactory.crotchet();
            assertThat(OpNames.deleteHairpinLabel(new Crescendo(anchor, end)))
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_DELETE_CRESCENDO));
        }

        @Test
        void testDiminuendo() {
            var anchor = StaffElementFactory.crotchet();
            var end = StaffElementFactory.crotchet();
            assertThat(OpNames.deleteHairpinLabel(new Diminuendo(anchor, end)))
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_DELETE_DIMINUENDO));
        }
    }

    @Nested
    class ArticulationLabel {

        @Test
        void testStaccato() {
            assertThat(OpNames.removeArticulationLabel(ArticulationType.STACCATO))
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_REMOVE_STACCATO));
        }

        @Test
        void testAccent() {
            assertThat(OpNames.removeArticulationLabel(ArticulationType.ACCENT))
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_REMOVE_ACCENT));
        }
    }

    @Nested
    class AttachmentLabel {

        @Test
        void testFermata() {
            assertThat(OpNames.removeAttachmentLabel(new FermataAttachment()))
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_REMOVE_FERMATA));
        }

        @Test
        void testDynamic() {
            assertThat(OpNames.removeAttachmentLabel(new DynamicAttachment(DynamicAttachment.DynamicType.FORTE)))
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_REMOVE_DYNAMIC));
        }

        @Test
        void testAnnotation() {
            assertThat(OpNames.removeAttachmentLabel(new AnnotationAttachment("text")))
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_REMOVE_ANNOTATION));
        }

        @Test
        void testTempoChange() {
            assertThat(OpNames.removeAttachmentLabel(new TempoChangeAttachment(new Tempo())))
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_REMOVE_TEMPO_CHANGE));
        }

        @Test
        void testBeatChange() {
            var beatChange = new BeatChange(Duration.QUAVER, Duration.QUAVER);
            assertThat(OpNames.removeAttachmentLabel(new BeatChangeAttachment(beatChange)))
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_REMOVE_BEAT_CHANGE));
        }
    }

    /** The simple {@code remove*Label} methods: no argument, one fixed name each. */
    @Nested
    class FixedRemovalLabel {

        @Test
        void testTie() {
            assertThat(OpNames.removeTieLabel())
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_REMOVE_TIE));
        }

        @Test
        void testBeam() {
            assertThat(OpNames.removeBeamLabel())
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_REMOVE_BEAM));
        }

        @Test
        void testTuplet() {
            assertThat(OpNames.removeTupletLabel())
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_REMOVE_TUPLET));
        }

        @Test
        void testTrill() {
            assertThat(OpNames.removeTrillLabel())
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_REMOVE_TRILL));
        }

        @Test
        void testAccidental() {
            assertThat(OpNames.removeAccidentalLabel())
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_REMOVE_ACCIDENTAL));
        }
    }
}
