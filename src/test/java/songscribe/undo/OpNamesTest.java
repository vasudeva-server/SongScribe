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

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.ArticulationType;
import songscribe.dom.Attachment;
import songscribe.dom.BeatChange;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.Crescendo;
import songscribe.dom.Diminuendo;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.Duration;
import songscribe.dom.ElementType;
import songscribe.dom.FermataAttachment;
import songscribe.dom.Hairpin;
import songscribe.dom.SlideZone;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementFactory;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;
import songscribe.undo.OpNames;

/**
 * Exercises {@link OpNames}: which name an edit is given, for each way the name depends on
 * what the edit acted on. The methods are pure functions of their arguments, so every case
 * is a data row and one shared assertion, with a fixture no larger than the argument the
 * method under test declares — a bare {@link StaffElement} pair to anchor a hairpin, never a
 * {@code Line} or a {@code Song}.
 *
 * <p><b>{@link OpNames#deleteLabel} — the three classes of input it distinguishes.</b> One
 * element of a category yields the singular name, several of one category the plural, and a
 * mix the generic name. One case per category, the categories being the five the class
 * documents; the note/grace folding is a case of the second class, not a fourth class,
 * because the promise is that they share a category. Nothing is asserted for an empty list:
 * the contract states a non-empty precondition and promises nothing beyond it.
 *
 * <p><b>{@link OpNames#addLabel} — one case per category, plus the two that are not
 * categories.</b> A grace note is named separately rather than folding into {@code Note},
 * which is the one place the two labels' taxonomies deliberately differ. A type in no
 * category throws, which is the clause that keeps a future {@link ElementType} from being
 * quietly labelled as something it is not. That last case is not present today.
 *
 * <p>Neither of those two tables claims to enumerate its domain, and neither can: the
 * category is {@code OpNames.Category}, which is private and therefore not something a test
 * may assert against. A new category is caught by review, not by this class.
 *
 * <p><b>The subtype labels</b> — slide, hairpin, articulation and attachment names are each
 * chosen from a small closed set, and each set <em>is</em> enumerated in full here: both
 * slide subtypes in each direction, the two hairpin kinds, the two articulation types, and
 * the five attachment kinds. Each of those five tables carries a companion test asserting
 * its rows are exactly the domain — the enum's constants, or the leaves of the sealed
 * hierarchy — so growing the domain fails this class rather than leaving the claim above
 * quietly false. That is not hypothetical: three of these sets carried the claim and did
 * not enumerate anything until a coverage run said so.
 *
 * <p><b>The fixed names</b> — {@code deleteLineLabel}, {@code deleteEndingLabel} and the five
 * simple {@code remove*Label} methods each promise exactly one name for one specific edit,
 * with no argument to vary. Each still gets its own case: a fixed method untested is a
 * contract clause untested, the same as any other.
 *
 * <p><b>{@link OpNames#lyricLabel} — a transition, not a value.</b> The three classes are
 * empty → non-empty, non-empty → empty, and everything else; the third is asserted with two
 * different non-empty strings, which is what distinguishes it from the first two.
 *
 * <p>Expected names are resolved through the same {@link Strings} constants production uses.
 * The promise is which name is chosen for a given input, not what that name reads as in one
 * locale, and a test spelling out the English would fail on a translation that changed
 * nothing about the promise.
 *
 * <p>Every nested class here is one method's contract, driven by a {@code record} case table
 * over a single {@code @ParameterizedTest} rather than one hand-written {@code @Test} per
 * case: the algorithm under test is identical across cases, so only the data — inputs,
 * expected name, and a description — should vary. See {@code testing-unit.md}.
 */
class OpNamesTest extends UnitTest {

    @Nested
    class DeleteLabel {

        private record DeleteLabelCase(String description, List<ElementType> types, String expectedKey) {}

        @ParameterizedTest(name = "{0}")
        @MethodSource("cases")
        void testDeleteLabelMatchesExpectedName(DeleteLabelCase testCase) {
            assertThat(OpNames.deleteLabel(testCase.types()))
                .isEqualTo(Strings.get(testCase.expectedKey()));
        }

        static Stream<DeleteLabelCase> cases() {
            return Stream.of(
                new DeleteLabelCase("single note is singular",
                    List.of(ElementType.CROTCHET), Strings.ACTION_EDIT_OP_DELETE_NOTE),
                new DeleteLabelCase("multiple notes is plural",
                    List.of(ElementType.CROTCHET, ElementType.QUAVER), Strings.ACTION_EDIT_OP_DELETE_NOTES),
                new DeleteLabelCase("note and grace note fold to the note plural",
                    List.of(ElementType.CROTCHET, ElementType.GRACE_QUAVER), Strings.ACTION_EDIT_OP_DELETE_NOTES),
                new DeleteLabelCase("a mix of categories is generic",
                    List.of(ElementType.CROTCHET, ElementType.SINGLE_BARLINE), Strings.ACTION_EDIT_OP_DELETE_ELEMENTS),
                new DeleteLabelCase("single rest is singular",
                    List.of(ElementType.CROTCHET_REST), Strings.ACTION_EDIT_OP_DELETE_REST),
                new DeleteLabelCase("multiple rests is plural",
                    List.of(ElementType.CROTCHET_REST, ElementType.QUAVER_REST), Strings.ACTION_EDIT_OP_DELETE_RESTS),
                new DeleteLabelCase("single barline",
                    List.of(ElementType.SINGLE_BARLINE), Strings.ACTION_EDIT_OP_DELETE_BARLINE),
                new DeleteLabelCase("multiple barlines is plural",
                    List.of(ElementType.SINGLE_BARLINE, ElementType.DOUBLE_BARLINE), Strings.ACTION_EDIT_OP_DELETE_BARLINES),
                new DeleteLabelCase("single repeat",
                    List.of(ElementType.REPEAT_LEFT), Strings.ACTION_EDIT_OP_DELETE_REPEAT),
                new DeleteLabelCase("multiple repeats is plural",
                    List.of(ElementType.REPEAT_LEFT, ElementType.REPEAT_RIGHT), Strings.ACTION_EDIT_OP_DELETE_REPEATS),
                new DeleteLabelCase("single breath mark",
                    List.of(ElementType.BREATH_MARK), Strings.ACTION_EDIT_OP_DELETE_BREATH_MARK),
                new DeleteLabelCase("multiple breath marks is plural",
                    List.of(ElementType.BREATH_MARK, ElementType.BREATH_MARK), Strings.ACTION_EDIT_OP_DELETE_BREATH_MARKS)
            );
        }
    }

    @Nested
    class AddLabel {

        private record AddLabelCase(String description, ElementType type, String expectedKey) {}

        @ParameterizedTest(name = "{0}")
        @MethodSource("cases")
        void testAddLabelMatchesExpectedName(AddLabelCase testCase) {
            assertThat(OpNames.addLabel(testCase.type()))
                .isEqualTo(Strings.get(testCase.expectedKey()));
        }

        static Stream<AddLabelCase> cases() {
            return Stream.of(
                new AddLabelCase("note", ElementType.CROTCHET, Strings.ACTION_EDIT_OP_ADD_NOTE),
                new AddLabelCase("rest", ElementType.CROTCHET_REST, Strings.ACTION_EDIT_OP_ADD_REST),
                new AddLabelCase("barline", ElementType.SINGLE_BARLINE, Strings.ACTION_EDIT_OP_ADD_BARLINE),
                new AddLabelCase("repeat", ElementType.REPEAT_LEFT, Strings.ACTION_EDIT_OP_ADD_REPEAT),
                new AddLabelCase("breath mark", ElementType.BREATH_MARK, Strings.ACTION_EDIT_OP_ADD_BREATH_MARK),
                new AddLabelCase("grace note does not fold into note",
                    ElementType.GRACE_QUAVER, Strings.ACTION_EDIT_OP_ADD_GRACE_NOTE)
            );
        }
    }

    @Nested
    class SlideLabel {

        private record AddSlideCase(String description, SlideZone zone, String expectedKey) {}

        @ParameterizedTest(name = "{0}")
        @MethodSource("addCases")
        void testAddSlideLabelMatchesExpectedName(AddSlideCase testCase) {
            assertThat(OpNames.addSlideLabel(testCase.zone()))
                .isEqualTo(Strings.get(testCase.expectedKey()));
        }

        static Stream<AddSlideCase> addCases() {
            return Stream.of(
                new AddSlideCase("glissando", SlideZone.GLISSANDO, Strings.ACTION_EDIT_OP_ADD_GLISSANDO),
                new AddSlideCase("fall", SlideZone.FALL, Strings.ACTION_EDIT_OP_ADD_FALL)
            );
        }

        @Test
        void testAddCasesEnumerateEverySlideZone() {
            assertThat(addCases().map(AddSlideCase::zone))
                .containsExactlyInAnyOrder(SlideZone.values());
        }

        private record DeleteSlideCase(String description, StaffElement.Slide slide, String expectedKey) {}

        @ParameterizedTest(name = "{0}")
        @MethodSource("deleteCases")
        void testDeleteSlideLabelMatchesExpectedName(DeleteSlideCase testCase) {
            assertThat(OpNames.deleteSlideLabel(testCase.slide()))
                .isEqualTo(Strings.get(testCase.expectedKey()));
        }

        static Stream<DeleteSlideCase> deleteCases() {
            return Stream.of(
                new DeleteSlideCase("glissando", new StaffElement.Glissando(), Strings.ACTION_EDIT_OP_DELETE_GLISSANDO),
                new DeleteSlideCase("fall", new StaffElement.Fall(), Strings.ACTION_EDIT_OP_DELETE_FALL)
            );
        }

        @Test
        void testDeleteCasesEnumerateEverySlideSubtype() {
            assertThat(deleteCases().<Class<?>>map(testCase -> testCase.slide().getClass()))
                .containsExactlyInAnyOrder(StaffElement.Slide.class.getPermittedSubclasses());
        }
    }

    @Nested
    class HairpinLabel {

        private record HairpinLabelCase(String description, Hairpin hairpin, String expectedKey) {}

        @ParameterizedTest(name = "{0}")
        @MethodSource("cases")
        void testDeleteHairpinLabelMatchesExpectedName(HairpinLabelCase testCase) {
            assertThat(OpNames.deleteHairpinLabel(testCase.hairpin()))
                .isEqualTo(Strings.get(testCase.expectedKey()));
        }

        static Stream<HairpinLabelCase> cases() {
            return Stream.of(
                new HairpinLabelCase("crescendo",
                    new Crescendo(StaffElementFactory.crotchet(), StaffElementFactory.crotchet()),
                    Strings.ACTION_EDIT_OP_DELETE_CRESCENDO),
                new HairpinLabelCase("diminuendo",
                    new Diminuendo(StaffElementFactory.crotchet(), StaffElementFactory.crotchet()),
                    Strings.ACTION_EDIT_OP_DELETE_DIMINUENDO)
            );
        }

        @Test
        void testCasesEnumerateEveryHairpinKind() {
            assertThat(cases().<Class<?>>map(testCase -> testCase.hairpin().getClass()))
                .containsExactlyInAnyOrder(Hairpin.class.getPermittedSubclasses());
        }
    }

    @Nested
    class ArticulationLabel {

        private record ArticulationLabelCase(String description, ArticulationType type, String expectedKey) {}

        @ParameterizedTest(name = "{0}")
        @MethodSource("cases")
        void testRemoveArticulationLabelMatchesExpectedName(ArticulationLabelCase testCase) {
            assertThat(OpNames.removeArticulationLabel(testCase.type()))
                .isEqualTo(Strings.get(testCase.expectedKey()));
        }

        static Stream<ArticulationLabelCase> cases() {
            return Stream.of(
                new ArticulationLabelCase("staccato", ArticulationType.STACCATO, Strings.ACTION_EDIT_OP_REMOVE_STACCATO),
                new ArticulationLabelCase("accent", ArticulationType.ACCENT, Strings.ACTION_EDIT_OP_REMOVE_ACCENT)
            );
        }

        @Test
        void testCasesEnumerateEveryArticulationType() {
            assertThat(cases().map(ArticulationLabelCase::type))
                .containsExactlyInAnyOrder(ArticulationType.values());
        }
    }

    @Nested
    class AttachmentLabel {

        private record AttachmentLabelCase(String description, Attachment attachment, String expectedKey) {}

        @ParameterizedTest(name = "{0}")
        @MethodSource("cases")
        void testRemoveAttachmentLabelMatchesExpectedName(AttachmentLabelCase testCase) {
            assertThat(OpNames.removeAttachmentLabel(testCase.attachment()))
                .isEqualTo(Strings.get(testCase.expectedKey()));
        }

        static Stream<AttachmentLabelCase> cases() {
            return Stream.of(
                new AttachmentLabelCase("fermata", new FermataAttachment(), Strings.ACTION_EDIT_OP_REMOVE_FERMATA),
                new AttachmentLabelCase("dynamic",
                    new DynamicAttachment(DynamicAttachment.DynamicType.FORTE), Strings.ACTION_EDIT_OP_REMOVE_DYNAMIC),
                new AttachmentLabelCase("annotation",
                    new AnnotationAttachment("text"), Strings.ACTION_EDIT_OP_REMOVE_ANNOTATION),
                new AttachmentLabelCase("tempo change",
                    new TempoChangeAttachment(new Tempo()), Strings.ACTION_EDIT_OP_REMOVE_TEMPO_CHANGE),
                new AttachmentLabelCase("beat change",
                    new BeatChangeAttachment(new BeatChange(Duration.QUAVER, Duration.QUAVER)),
                    Strings.ACTION_EDIT_OP_REMOVE_BEAT_CHANGE)
            );
        }

        @Test
        void testCasesEnumerateEveryAttachmentKind() {
            assertThat(cases().<Class<?>>map(testCase -> testCase.attachment().getClass()))
                .containsExactlyInAnyOrderElementsOf(sealedLeavesOf(Attachment.class).toList());
        }
    }

    /**
     * The concrete types a sealed hierarchy can actually be instantiated as — its permitted
     * subclasses, followed down through any that are themselves sealed. {@link Attachment}
     * permits {@code MetronomeAttachment}, which permits two more, so its direct permitted
     * set is not the domain a caller sees.
     *
     * @return every leaf of the hierarchy rooted at {@code sealedRoot}, or {@code sealedRoot}
     *         itself when it permits nothing
     */
    private static Stream<Class<?>> sealedLeavesOf(Class<?> sealedRoot) {
        var permitted = sealedRoot.getPermittedSubclasses();

        if (permitted == null) {
            return Stream.of(sealedRoot);
        }

        return Arrays.stream(permitted).flatMap(OpNamesTest::sealedLeavesOf);
    }

    /** Every {@link OpNames} method that takes no argument and names exactly one edit. */
    @Nested
    class FixedLabel {

        private record FixedLabelCase(String description, Supplier<String> label, String expectedKey) {}

        @ParameterizedTest(name = "{0}")
        @MethodSource("cases")
        void testFixedLabelMatchesExpectedName(FixedLabelCase testCase) {
            assertThat(testCase.label().get())
                .isEqualTo(Strings.get(testCase.expectedKey()));
        }

        static Stream<FixedLabelCase> cases() {
            return Stream.of(
                new FixedLabelCase("delete line", OpNames::deleteLineLabel, Strings.ACTION_EDIT_OP_DELETE_LINE),
                new FixedLabelCase("delete ending", OpNames::deleteEndingLabel, Strings.ACTION_EDIT_OP_DELETE_ENDING),
                new FixedLabelCase("remove tie", OpNames::removeTieLabel, Strings.ACTION_EDIT_OP_REMOVE_TIE),
                new FixedLabelCase("remove beam", OpNames::removeBeamLabel, Strings.ACTION_EDIT_OP_REMOVE_BEAM),
                new FixedLabelCase("remove tuplet", OpNames::removeTupletLabel, Strings.ACTION_EDIT_OP_REMOVE_TUPLET),
                new FixedLabelCase("remove trill", OpNames::removeTrillLabel, Strings.ACTION_EDIT_OP_REMOVE_TRILL),
                new FixedLabelCase("remove accidental",
                    OpNames::removeAccidentalLabel, Strings.ACTION_EDIT_OP_REMOVE_ACCIDENTAL)
            );
        }
    }

    @Nested
    class LyricLabel {

        private record LyricLabelCase(String description, String beforeText, String afterText, String expectedKey) {}

        @ParameterizedTest(name = "{0}")
        @MethodSource("cases")
        void testLyricLabelMatchesExpectedName(LyricLabelCase testCase) {
            assertThat(OpNames.lyricLabel(testCase.beforeText(), testCase.afterText()))
                .isEqualTo(Strings.get(testCase.expectedKey()));
        }

        static Stream<LyricLabelCase> cases() {
            return Stream.of(
                new LyricLabelCase("empty to non-empty is add", "", "word", Strings.ACTION_EDIT_OP_ADD_LYRIC),
                new LyricLabelCase("non-empty to empty is delete", "word", "", Strings.ACTION_EDIT_OP_DELETE_LYRIC),
                new LyricLabelCase("non-empty to non-empty is edit", "word", "other", Strings.ACTION_EDIT_OP_EDIT_LYRIC)
            );
        }
    }
}
