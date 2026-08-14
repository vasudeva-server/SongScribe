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

package songscribe.ui.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.MainFrameMockTest;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.dom.ElementType;
import songscribe.dom.Key;
import songscribe.dom.KeySignatureElement;
import songscribe.dom.KeyType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementFactory;
import songscribe.layout.NoteGeometry;
import songscribe.ui.MusicEditOperations;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.ScoreViewController;
import songscribe.ui.selection.SelectionCoordinator;
import songscribe.undo.UndoTestSupport;

/**
 * Exercises the two promises the mid-line key-change flow makes: which positions
 * {@link KeySignatureChangeAction#acceptsInsertionIndex} takes, and what
 * {@link ScoreViewController#insertKeySignature} writes at one it took.
 *
 * <p><b>The predicate's refusals</b> — one case each, over a single fixture line whose indices
 * span all of them: the head of the line, both indices at the end of it, an index beyond it, and
 * all three indices touching a barline-plus-key-signature pair already on the line. The last is
 * three distinct cases, not one: a predicate that looked only at the key signature would take the
 * index in front of its barline, and that is the row that catches it.
 *
 * <p>The two end-of-line rows are a pair on purpose. The index of the last element governs one
 * element and the position past it governs none, so a predicate refusing the first while taking
 * the second would be incoherent — and that is exactly what it did until the rule was made
 * symmetric.
 *
 * <p><b>The predicate's acceptances</b> — an ordinary interior index on either side of the pair.
 *
 * <p><b>The implicit barline</b> — both sides of it. A position that does not already follow a
 * barline or repeat takes two elements, barline first; one that does takes the key signature
 * alone. The second is what catches an unconditional barline insert, which the first cannot see.
 *
 * <p><b>One undo step</b> — the two elements enter inside one bracket, so one undo takes back
 * both. {@code captureSingleBatch} is what asserts the single step; a second bracket would post a
 * second notification and fail there rather than in the assertions.
 *
 * <p><b>Boundaries</b> — an insertion index below 1 or above
 * {@link Line#effectiveElementCount()} throws.
 *
 * <p><b>No position guard on the action</b> — it is invocable with nothing selected, because
 * that is how the flow starts. Every position rule lives in the predicate instead.
 *
 * <p>The dialog the accepted index opens is {@code KeySignatureChangeDialogTest}'s subject, and
 * the hit targets that reach an existing key signature are {@code LayoutHitTesterTest}'s; neither
 * is restated here.
 */
class KeySignatureChangeActionTest extends MainFrameMockTest {

    /** The key every fixture writes, chosen so it differs from a new song's default. */
    private static final Key INSERTED_KEY = new Key(KeyType.SHARPS, 2);

    /** One sharp: F. The reconciliation fixtures put F naturals under it. */
    private static final Key F_SHARP_KEY = new Key(KeyType.SHARPS, 1);

    /** The staff position the reconciliation fixtures write, matching {@link #F_SHARP_KEY}. */
    private static final int F_STAFF_POSITION = 3;

    /** Enough Fs that notes fall on both sides of {@link #INSERTION_INDEX}. */
    private static final int RECONCILED_NOTE_COUNT = 4;

    /** A barline and the key signature behind it: the unit the predicate refuses to touch. */
    private static final int PAIR_ELEMENT_COUNT = 2;

    private static final int NOTES_BEFORE_PAIR = 3;

    /**
     * Enough notes after the pair that an ordinary interior index exists between the pair and the
     * line's last element, whose own index the predicate refuses.
     */
    private static final int NOTES_AFTER_PAIR = 3;

    private static final int PAIR_BARLINE_INDEX = NOTES_BEFORE_PAIR;
    private static final int PAIR_KEY_INDEX = PAIR_BARLINE_INDEX + 1;

    private static final int PREDICATE_FIXTURE_ELEMENT_COUNT =
        NOTES_BEFORE_PAIR + PAIR_ELEMENT_COUNT + NOTES_AFTER_PAIR;

    /** Notes in the insertion fixtures — enough to leave room on both sides of the insertion. */
    private static final int INSERTION_FIXTURE_NOTE_COUNT = 4;

    /** An interior index of the insertion fixtures, with notes before and after it. */
    private static final int INSERTION_INDEX = 2;

    /**
     * Projecting a line reads accidental widths out of a static table that has to be built first.
     * Without this the class passes only when some earlier test class happens to have built it.
     */
    @BeforeAll
    static void initializeNoteGeometry() {
        NoteGeometry.initializeAccidentalWidths();
    }

    /** The element types of {@code line}, terminal excluded — what an insertion changes. */
    private static List<ElementType> effectiveTypesOf(Line line) {
        var types = new ArrayList<ElementType>();

        for (var index = 0; index < line.effectiveElementCount(); index++) {
            types.add(line.getElement(index).getType());
        }

        return types;
    }

    private static ScoreViewController controllerFor(ScoreView scoreView) {
        return new ScoreViewController(
            scoreView,
            mock(MusicEditOperations.class),
            mock(SelectionCoordinator.class),
            mock(ClipboardManager.class));
    }

    @Test
    void testEnabledWithNothingSelectedSoAPlacementCanBeStarted() {
        when(mockEnv().score().getSelectedLine()).thenReturn(-1);
        var action = KeySignatureChangeAction.createAction(mainFrame());

        action.musicSelectionDidChange(new MusicSelectionDidChangeNotification(mockEnv().score()));

        assertThat(action.isEnabled()).isTrue();
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AcceptsInsertionIndex {

        /**
         * A line of notes with one barline-plus-key-signature pair in the middle of it, so every
         * index the table names exists on the same line.
         */
        private Line fixtureLine() {
            var song = new Song();
            var line = song.getLine(0);

            song.withoutMutationTracking(() -> {
                for (var i = 0; i < NOTES_BEFORE_PAIR; i++) {
                    line.addElement(StaffElementFactory.crotchet());
                }

                line.addElement(StaffElementFactory.singleBarline());
                line.addElement(new KeySignatureElement(INSERTED_KEY));

                for (var i = 0; i < NOTES_AFTER_PAIR; i++) {
                    line.addElement(StaffElementFactory.crotchet());
                }
            });

            return line;
        }

        @Test
        void testFixtureLineHoldsTheIndicesTheCaseTableNames() {
            var line = fixtureLine();

            assertThat(line.effectiveElementCount())
                .as("the case table's indices are derived from this count")
                .isEqualTo(PREDICATE_FIXTURE_ELEMENT_COUNT);
            assertThat(line.getElement(PAIR_KEY_INDEX).getType()).isEqualTo(ElementType.KEY_SIGNATURE);
            assertThat(line.getElement(PAIR_BARLINE_INDEX).getType().isBarLine()).isTrue();
        }

        @ParameterizedTest(name = "{0} is {1}")
        @MethodSource("songscribe.ui.action.KeySignatureChangeActionTest#insertionIndexCases")
        void testIndexIsAcceptedOnlyWhereAKeyChangeMayBeWritten(int index, boolean accepted) {
            var action = KeySignatureChangeAction.createAction(mainFrame());

            assertThat(action.acceptsInsertionIndex(fixtureLine(), index)).isEqualTo(accepted);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class InsertKeySignature {

        /** A line of plain notes: no index on it already follows a barline. */
        private Song noteOnlySong() {
            var song = new Song();
            UndoTestSupport.addCrotchets(song, song.getLine(0), INSERTION_FIXTURE_NOTE_COUNT);
            return song;
        }

        @Test
        void testInsertingWhereNoBarlinePrecedesWritesBarlineThenKeySignature() {
            var song = noteOnlySong();
            var line = song.getLine(0);
            var scoreView = UndoTestSupport.scoreViewFor(song);
            var controller = controllerFor(scoreView);
            var typesBefore = effectiveTypesOf(line);

            controller.insertKeySignature(line, INSERTION_INDEX, INSERTED_KEY);

            var expected = new ArrayList<>(typesBefore);
            expected.addAll(
                INSERTION_INDEX, List.of(ElementType.SINGLE_BARLINE, ElementType.KEY_SIGNATURE));

            assertThat(effectiveTypesOf(line)).isEqualTo(expected);

            var written = line.getElement(INSERTION_INDEX + 1);
            assertThat(written).isInstanceOf(KeySignatureElement.class);
            assertThat(((KeySignatureElement) written).getKey()).isEqualTo(INSERTED_KEY);
        }

        @Test
        void testOneUndoTakesBackBothTheBarlineAndTheKeySignature() {
            var song = noteOnlySong();
            var line = song.getLine(0);
            var scoreView = UndoTestSupport.scoreViewFor(song);
            var controller = controllerFor(scoreView);
            var typesBefore = effectiveTypesOf(line);

            // captureSingleBatch fails outright if the edit posts more than one notification,
            // which is what "one undo step" means from the user's side.
            var batch = UndoTestSupport.captureSingleBatch(
                song, () -> controller.insertKeySignature(line, INSERTION_INDEX, INSERTED_KEY));

            UndoTestSupport.replayUndo(scoreView, batch);

            assertThat(effectiveTypesOf(line)).isEqualTo(typesBefore);
        }

        @Test
        void testInsertingAfterABarlineWritesTheKeySignatureAlone() {
            var song = new Song();
            var line = song.getLine(0);

            song.withoutMutationTracking(() -> {
                line.addElement(StaffElementFactory.crotchet());
                line.addElement(StaffElementFactory.singleBarline());
                line.addElement(StaffElementFactory.crotchet());
            });

            var barlineIndex = 1;
            var scoreView = UndoTestSupport.scoreViewFor(song);
            var controller = controllerFor(scoreView);
            var typesBefore = effectiveTypesOf(line);

            controller.insertKeySignature(line, barlineIndex + 1, INSERTED_KEY);

            var expected = new ArrayList<>(typesBefore);
            expected.add(barlineIndex + 1, ElementType.KEY_SIGNATURE);

            assertThat(effectiveTypesOf(line)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "index {0}")
        @MethodSource("songscribe.ui.action.KeySignatureChangeActionTest#outOfBoundsInsertionIndexes")
        void testInsertionIndexOutsideTheLineThrows(int index) {
            var song = noteOnlySong();
            var line = song.getLine(0);
            var controller = controllerFor(UndoTestSupport.scoreViewFor(song));

            assertThatThrownBy(() -> controller.insertKeySignature(line, index, INSERTED_KEY))
                .isInstanceOf(IndexOutOfBoundsException.class);
        }

        /**
         * A line in C major holding F naturals, with an F sharp key signature written into the
         * middle of it. Every F after the insertion point would start sounding as F sharp, so the
         * reconciliation must give each an explicit natural; the F before it must be left alone.
         */
        private Song fSharpInsertedIntoCMajor() {
            var song = new Song();
            var line = song.getLine(0);

            song.withoutMutationTracking(() -> {
                line.setKey(new Key(KeyType.NONE, 0));

                for (var i = 0; i < RECONCILED_NOTE_COUNT; i++) {
                    line.addElement(StaffElementFactory.note(F_STAFF_POSITION));
                }
            });

            return song;
        }

        /** Every note's sounding pitch, in element order, for comparison across an edit. */
        private static List<Integer> soundingPitchesOf(Line line) {
            var pitches = new ArrayList<Integer>();

            for (var index = 0; index < line.effectiveElementCount(); index++) {
                var element = line.getElement(index);

                if (element.getType().isNote()) {
                    pitches.add(element.getPitch());
                }
            }

            return pitches;
        }

        @Test
        void testEveryNoteKeepsItsSoundingPitch() {
            // The promise is about pitch, not about written accidentals: the reconciliation writes
            // an accidental only where one is needed, so the second F of a bar rides the first
            // one's natural and carries nothing itself. Asserting the notation would pin the
            // arithmetic rather than the contract.
            var song = fSharpInsertedIntoCMajor();
            var line = song.getLine(0);
            var controller = controllerFor(UndoTestSupport.scoreViewFor(song));
            var pitchesBefore = soundingPitchesOf(line);

            controller.insertKeySignature(line, INSERTION_INDEX, F_SHARP_KEY);

            assertThat(soundingPitchesOf(line))
                .as("an inserted key signature must move no pitch the notator did not change")
                .isEqualTo(pitchesBefore);
        }

        @Test
        void testNotesBeforeTheInsertionAreLeftAlone() {
            // The other side of the same promise: the change takes effect from its own index
            // forward, so a reconciliation that re-spelled the whole line would be wrong. These
            // notes need no accidental because the key they run in has not moved.
            var song = fSharpInsertedIntoCMajor();
            var line = song.getLine(0);
            var controller = controllerFor(UndoTestSupport.scoreViewFor(song));

            controller.insertKeySignature(line, INSERTION_INDEX, F_SHARP_KEY);

            for (var index = 0; index < INSERTION_INDEX; index++) {
                assertThat(line.getElement(index).getAccidental())
                    .as("the F at index %d is before the change and must be untouched", index)
                    .isNull();
            }
        }

        @Test
        void testALineInheritingTheMovedKeyIsReconciledToo() {
            // The tail of the reach. The inserted key moves the key this line leaves off in, so
            // the next line inherits it — and its notes move pitch with nothing on that line
            // changed. Reconciling only the host line is the failure this catches.
            var song = fSharpInsertedIntoCMajor();
            var line = song.getLine(0);
            var nextLine = new Line(song);

            song.withoutMutationTracking(() -> {
                song.addLine(nextLine);
                nextLine.addElement(StaffElementFactory.note(F_STAFF_POSITION));
            });

            var controller = controllerFor(UndoTestSupport.scoreViewFor(song));

            controller.insertKeySignature(line, INSERTION_INDEX, F_SHARP_KEY);

            assertThat(nextLine.getElement(0).getAccidental())
                .as("the next line inherits the inserted key, so its F must be spelled natural")
                .isEqualTo(StaffElement.Accidental.NATURAL);
        }

        @Test
        void testAKeySignatureAlreadyAfterTheInsertionPointStillEndsTheLine() {
            // The inserted key reaches the next line only when no key signature already stands
            // after it. Here one does, so the next line's F is governed by that later key — C
            // major — and needs no natural.
            var song = fSharpInsertedIntoCMajor();
            var line = song.getLine(0);
            var nextLine = new Line(song);

            song.withoutMutationTracking(() -> {
                line.addElement(StaffElementFactory.singleBarline());
                line.addElement(new KeySignatureElement(new Key(KeyType.NONE, 0)));
                song.addLine(nextLine);
                nextLine.addElement(StaffElementFactory.note(F_STAFF_POSITION));
            });

            var controller = controllerFor(UndoTestSupport.scoreViewFor(song));

            controller.insertKeySignature(line, INSERTION_INDEX, F_SHARP_KEY);

            assertThat(nextLine.getElement(0).getAccidental())
                .as("the later key signature ends the line in C major, so nothing moved here")
                .isNull();
        }
    }

    static Stream<Arguments> insertionIndexCases() {
        return Stream.of(
            Arguments.of(Named.of("the head of the line", 0), false),
            Arguments.of(Named.of("an ordinary interior index", 1), true),
            Arguments.of(
                Named.of("immediately before the pair's barline", PAIR_BARLINE_INDEX), false),
            Arguments.of(
                Named.of("between the pair's barline and its key signature", PAIR_KEY_INDEX), false),
            Arguments.of(
                Named.of("immediately after the pair's key signature", PAIR_KEY_INDEX + 1), false),
            Arguments.of(
                Named.of("an ordinary interior index past the pair", PAIR_KEY_INDEX + 2), true),
            Arguments.of(
                Named.of("the index of the line's last element", PREDICATE_FIXTURE_ELEMENT_COUNT - 1),
                false),
            Arguments.of(
                Named.of("the position past the last element", PREDICATE_FIXTURE_ELEMENT_COUNT),
                false),
            Arguments.of(
                Named.of("an index beyond the line", PREDICATE_FIXTURE_ELEMENT_COUNT + 1), false)
        );
    }

    static Stream<Arguments> outOfBoundsInsertionIndexes() {
        return Stream.of(
            Arguments.of(Named.of("the head of the line, where no key signature may sit", 0)),
            Arguments.of(
                Named.of("one past the end of the line", INSERTION_FIXTURE_NOTE_COUNT + 1))
        );
    }
}
