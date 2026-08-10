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

package songscribe.ui.edit;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import songscribe.UnitTest;
import songscribe.dom.Articulation;
import songscribe.dom.ArticulationType;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.message.MessageCenter;
import songscribe.message.notification.DocumentDidLoadNotification;
import songscribe.message.notification.ModeDidChangeNotification;
import songscribe.ui.action.Actions;
import songscribe.ui.action.ActionsTestSupport;
import songscribe.ui.action.UIAction;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.component.ScoreView;
import songscribe.ui.playback.PlayThread;
import songscribe.ui.selection.ActionReflector;
import songscribe.ui.selection.ReflectionTestHelper;
import songscribe.ui.selection.SelectionCoordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.quaver;

class EditModeManagerTest extends UnitTest {

    // These tests read and mutate the Actions.* constants (accent, articulation, dot,
    // accidental, rest, ...) but do not otherwise own Actions.
    @BeforeEach
    void initializeActions() {
        ActionsTestSupport.initializeActions();
    }

    // Resets the EditModeManager, GraceModeManager, and PasteModeManager singletons
    // between tests. EditModeManager.init() (called by several nested @BeforeEach
    // setups below) constructs a PasteModeManager, which sets its own static
    // instance as a side effect — that leak must be reset here too, or a later
    // test class's PasteModeManager.isActive()/getActiveInstance() calls see a
    // zombie instance wired to this test's torn-down mocks.
    //
    // Discarding the EditModeManager also discards both insertion slots, since they are
    // instance fields — no separate insertion reset is needed here, and the test has no
    // handle on the instance to call one on anyway.
    @AfterEach
    void tearDownSingletons() {
        resetEditModeManagerInstance(null);
        resetGraceModeManagerInstance(null);
        resetPasteModeManagerInstance(null);
        // Restore action state so tests don't bleed into each other.
        Actions.REST_ACTION.setSelected(false);
        Actions.DOT_ACTION_GROUP.clearSelection();
        Actions.ACCIDENTAL_ACTION_GROUP.clearSelection();
        Actions.FERMATA_ACTION.setSelected(false);
        Actions.ACCIDENTAL_IN_PARENS_ACTION.setSelected(false);
        Actions.ACCENT_ACTION.setSelected(false);
        Actions.STACCATO_ACTION.setSelected(false);
    }

    // -------------------------------------------------------------------------
    // init() + instance() — row 28
    // -------------------------------------------------------------------------

    @Nested
    class InitAndInstance {

        @Test
        void testInstanceThrowsWhenNotInitialized() {
            // INSTANCE is null (reset in @AfterEach); any static method that calls instance()
            // must throw AssertionError via the RuntimeError exit handler.
            assertThatThrownBy(EditModeManager::getGraceModeManager)
                .isInstanceOf(AssertionError.class);
        }

        @Test
        void testInstanceReturnsAfterInit() {
            EditModeManager.init(
                mock(ClipboardManager.class),
                mock(SelectionCoordinator.class),
                mock(ScoreActions.class),
                mock(ScoreView.class)
            );
            // If INSTANCE was set correctly, getGraceModeManager() returns non-null.
            assertThat(EditModeManager.getGraceModeManager()).isNotNull();
        }
    }

    // -------------------------------------------------------------------------
    // makePreviewElement() — row 29
    // -------------------------------------------------------------------------

    @Nested
    class MakePreviewElementFromActionGroup {

        @Test
        void testReturnsCrotchetWhenNothingSelected() {
            // Both groups unselected → defaults to CROTCHET.
            Actions.DURATION_ACTION_GROUP.clearSelection();
            Actions.NON_DURATION_ACTION_GROUP.clearSelection();
            var element = EditModeManager.makePreviewElement();
            assertThat(element.getType()).isEqualTo(ElementType.CROTCHET);
        }

        @Test
        void testReturnsDurationActionTypeWhenDurationGroupSelected() {
            Actions.DURATION_ACTION_GROUP.setSelected(Actions.HALF_NOTE_ACTION, true);
            var element = EditModeManager.makePreviewElement();
            assertThat(element.getType()).isEqualTo(ElementType.MINIM);
        }

        @Test
        void testFallsBackToNonDurationGroupWhenDurationIsUnselected() {
            Actions.DURATION_ACTION_GROUP.clearSelection();
            Actions.NON_DURATION_ACTION_GROUP.setSelected(Actions.REPEAT_ACTIONS[0], true);
            var element = EditModeManager.makePreviewElement();
            assertThat(element.getType()).isEqualTo(Actions.REPEAT_ACTIONS[0].getType());
        }

        /**
         * The consequence of this method reading the group's selection with no
         * {@code isEnabled()} check: the final double barline is a full member of
         * {@code NON_DURATION_ACTION_GROUP}, so while a terminal selection has it checked it
         * <em>is</em> what a preview element derived right then would be. Nothing here stops
         * that — what stops it is that leaving the selection restores the user's own pen
         * first, which is the ordering
         * {@code ScoreViewControllerTest.testModeDidChangeClearsSelectionBeforeDerivingThePreviewElement}
         * pins. This test owns the other half: that the restore really does hand the pen back.
         */
        @Test
        void testRestoringActionStatesTakesTheReflectedFinalDoubleBarlineBackOutOfThePen() {
            var userPen = ReflectionTestHelper.barlineActionFor(ElementType.SINGLE_BARLINE);
            var reflector = reflectorManaging(userPen, Actions.FINAL_DOUBLE_BARLINE_ACTION);

            Actions.DURATION_ACTION_GROUP.clearSelection();
            Actions.NON_DURATION_ACTION_GROUP.setSelected(userPen, true);

            // Selecting the terminal snapshots the pen, then reflection checks the entry
            // standing for the terminal's type — which the group answers by unchecking the pen.
            reflector.saveActionStates();
            Actions.FINAL_DOUBLE_BARLINE_ACTION.setSelected(true);

            assertThat(EditModeManager.makePreviewElement().getType())
                .as("pre-condition: enablement is not what keeps the reflected entry out")
                .isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);

            reflector.restoreActionStates();

            assertThat(EditModeManager.makePreviewElement().getType())
                .as("the restore hands the user's own pen back")
                .isEqualTo(ElementType.SINGLE_BARLINE);
        }

        /**
         * An {@link ActionReflector} whose managed set is exactly {@code actions}, reached
         * through the coordinator that owns it — the reflector's own constructor is
         * package-private to {@code songscribe.ui.selection}.
         */
        private ActionReflector reflectorManaging(UIAction... actions) {
            var coordinator = ReflectionTestHelper.createCoordinator(
                List.of(ElementType.CROTCHET.newInstance()),
                List.of(),
                List.of(actions)
            );

            return coordinator.getActionReflector();
        }
    }

    // -------------------------------------------------------------------------
    // makePreviewElement(ElementType) — row 30
    // -------------------------------------------------------------------------

    @Nested
    class MakePreviewElementFromType {

        @Test
        void testReturnsPitchedNoteWhenRestActionNotSelected() {
            Actions.REST_ACTION.setSelected(false);
            var element = EditModeManager.makePreviewElement(ElementType.CROTCHET);
            assertThat(element.getType()).isEqualTo(ElementType.CROTCHET);
        }

        @Test
        void testConvertsToRestWhenRestActionSelected() {
            Actions.REST_ACTION.setSelected(true);
            var element = EditModeManager.makePreviewElement(ElementType.CROTCHET);
            assertThat(element.getType()).isEqualTo(ElementType.CROTCHET_REST);
        }

        @Test
        void testDoesNotConvertNonPitchedNoteToRest() {
            // A non-pitched-note type (e.g. REPEAT_LEFT) must not be converted even
            // when REST_ACTION is selected.
            Actions.REST_ACTION.setSelected(true);
            var element = EditModeManager.makePreviewElement(ElementType.REPEAT_LEFT);
            assertThat(element.getType()).isEqualTo(ElementType.REPEAT_LEFT);
        }
    }

    // -------------------------------------------------------------------------
    // decorateElement() — row 31
    // -------------------------------------------------------------------------

    @Nested
    class DecorateElement {

        @Test
        void testSetsDotCountFromDotActionGroup() {
            Actions.DOT_ACTION_GROUP.setSelected(Actions.DOT_ACTION, true);
            var element = ElementType.CROTCHET.newInstance();
            EditModeManager.decorateElement(element);
            // SINGLE dot: ordinal() = 0, +1 = 1
            assertThat(element.getDotCount()).isEqualTo(1);
        }

        @Test
        void testSetsZeroDotCountWhenDotGroupUnselected() {
            Actions.DOT_ACTION_GROUP.clearSelection();
            var element = ElementType.CROTCHET.newInstance();
            EditModeManager.decorateElement(element);
            assertThat(element.getDotCount()).isEqualTo(0);
        }

        @Test
        void testSkipsNonDotDecorationsForRests() {
            // Rests must not receive accidental or articulation decorations.
            Actions.ACCIDENTAL_ACTION_GROUP.setSelected(Actions.SHARP_ACTION, true);
            Actions.ACCENT_ACTION.setSelected(true);
            var rest = ElementType.CROTCHET_REST.newInstance();
            EditModeManager.decorateElement(rest);
            assertThat(rest.getAccidental()).isNull();
            assertThat(rest.getArticulations()).isEmpty();
        }

        @Test
        void testSkipsAllDecorationsForNonNoteNonRestElements() {
            // Barlines, repeats, and other non-duration elements must not
            // receive dots, accidentals, or articulations.
            Actions.DOT_ACTION_GROUP.setSelected(Actions.DOT_ACTION, true);
            Actions.ACCIDENTAL_ACTION_GROUP.setSelected(Actions.SHARP_ACTION, true);
            Actions.ACCENT_ACTION.setSelected(true);
            Actions.STACCATO_ACTION.setSelected(true);
            var barline = ElementType.SINGLE_BARLINE.newInstance();
            EditModeManager.decorateElement(barline);
            assertThat(barline.getDotCount()).isEqualTo(0);
            assertThat(barline.getAccidental()).isNull();
            assertThat(barline.getArticulations()).isEmpty();
        }

        @Test
        void testSetsAccidentalFromAccidentalActionGroup() {
            Actions.ACCIDENTAL_ACTION_GROUP.setSelected(Actions.SHARP_ACTION, true);
            var element = ElementType.CROTCHET.newInstance();
            EditModeManager.decorateElement(element);
            assertThat(element.getAccidental()).isEqualTo(Actions.SHARP_ACTION.getAccidental());
        }

        @Test
        void testClearsAccidentalWhenAccidentalGroupUnselected() {
            Actions.ACCIDENTAL_ACTION_GROUP.clearSelection();
            var element = ElementType.CROTCHET.newInstance();
            element.setAccidental(StaffElement.Accidental.SHARP);
            EditModeManager.decorateElement(element);
            assertThat(element.getAccidental()).isNull();
        }

        @Test
        void testSetsAccidentalInParenthesesWhenActionSelected() {
            Actions.ACCIDENTAL_ACTION_GROUP.setSelected(Actions.SHARP_ACTION, true);
            Actions.ACCIDENTAL_IN_PARENS_ACTION.setSelected(true);
            var element = ElementType.CROTCHET.newInstance();
            EditModeManager.decorateElement(element);
            assertThat(element.isAccidentalInParentheses()).isTrue();
        }

        @Test
        void testAddsStaccatoArticulationWhenStaccatoActionSelected() {
            Actions.STACCATO_ACTION.setSelected(true);
            var element = ElementType.CROTCHET.newInstance();
            EditModeManager.decorateElement(element);
            var articulations = element.getArticulations();
            assertThat(articulations).hasSize(1);
            assertThat(articulations.getFirst().getType()).isEqualTo(ArticulationType.STACCATO);
        }

        /**
         * Accent and staccato are independent toggles — the articulation menu offers both
         * as check boxes, not as a one-of-several choice. Pins that: making either branch
         * in decorateElement exclude the other would fail here and nowhere else.
         */
        @Test
        void testAddsBothArticulationsWhenAccentAndStaccatoSelected() {
            Actions.ACCENT_ACTION.setSelected(true);
            Actions.STACCATO_ACTION.setSelected(true);
            var element = ElementType.CROTCHET.newInstance();
            EditModeManager.decorateElement(element);
            assertThat(element.getArticulations())
                .extracting(Articulation::getType)
                .containsExactlyInAnyOrder(ArticulationType.ACCENT, ArticulationType.STACCATO);
        }

        @Test
        void testAddsAccentArticulationWhenAccentActionSelected() {
            Actions.ACCENT_ACTION.setSelected(true);
            var element = ElementType.CROTCHET.newInstance();
            EditModeManager.decorateElement(element);
            var articulations = element.getArticulations();
            assertThat(articulations).hasSize(1);
            assertThat(articulations.getFirst().getType()).isEqualTo(ArticulationType.ACCENT);
        }

        /**
         * Pins {@code clearNoteDecorations()} to the full set of toggles this method reads.
         * A toggle added to decorateElement but not to clearNoteDecorations would leak onto
         * elements that callers built expecting them to be undecorated — the grace note's
         * host note being the case that motivated the helper.
         */
        @Test
        void testClearNoteDecorationsLeavesElementUndecorated() {
            Actions.DOT_ACTION_GROUP.setSelected(Actions.DOUBLE_DOT_ACTION, true);
            Actions.ACCIDENTAL_ACTION_GROUP.setSelected(Actions.SHARP_ACTION, true);
            Actions.ACCIDENTAL_IN_PARENS_ACTION.setSelected(true);
            Actions.STACCATO_ACTION.setSelected(true);
            Actions.ACCENT_ACTION.setSelected(true);

            Actions.clearNoteDecorations();

            var element = ElementType.CROTCHET.newInstance();
            EditModeManager.decorateElement(element);

            assertThat(element.getDotCount()).isEqualTo(0);
            assertThat(element.getAccidental()).isNull();
            assertThat(element.isAccidentalInParentheses()).isFalse();
            assertThat(element.getArticulations()).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // elementWasModified() — rows 32 and 33
    // -------------------------------------------------------------------------

    @Nested
    class ElementWasModified {

        private ScoreActions scoreActions;

        @BeforeEach
        void setUp() {
            scoreActions = mock(ScoreActions.class);
            EditModeManager.init(
                mock(ClipboardManager.class),
                mock(SelectionCoordinator.class),
                scoreActions,
                mock(ScoreView.class)
            );
        }

        @Test
        void testReturnsFalseForNonRepeatPreviewElement() {
            EditModeManager.setPreviewElement(ElementType.CROTCHET.newInstance());
            var line = lineWith(ElementType.CROTCHET);
            var result = EditModeManager.elementWasModified(line, 0);
            assertThat(result).isFalse();
        }

        @Test
        void testRepeatLeftAdjacentToRepeatRightCoalescesToRepeatLeftRight() {
            // Line: [REPEAT_RIGHT] at index 0; preview element is REPEAT_LEFT being
            // inserted at index 1 (after the REPEAT_RIGHT). elementWasModified() checks
            // whether element at (elementIndex - 1) is REPEAT_RIGHT and, if so, replaces
            // it with REPEAT_LEFT_RIGHT.
            var line = lineWith(ElementType.REPEAT_RIGHT);
            EditModeManager.setPreviewElement(ElementType.REPEAT_LEFT.newInstance());
            // elementIndex=1 means the new element would go at index 1; element at 0 is REPEAT_RIGHT.
            var result = EditModeManager.elementWasModified(line, 1);
            assertThat(result).isTrue();
            assertThat(line.getElement(0).getType()).isEqualTo(ElementType.REPEAT_LEFT_RIGHT);
            verify(scoreActions).clearSelection();
        }

        @Test
        void testRepeatRightAdjacentToRepeatLeftCoalescesToRepeatLeftRight() {
            // Line: [REPEAT_LEFT] at index 0; preview element is REPEAT_RIGHT being
            // inserted at index 0 (before the REPEAT_LEFT). elementWasModified() checks
            // whether element at elementIndex is REPEAT_LEFT and, if so, replaces it.
            var line = lineWith(ElementType.REPEAT_LEFT);
            EditModeManager.setPreviewElement(ElementType.REPEAT_RIGHT.newInstance());
            // elementIndex=0: element at index 0 is REPEAT_LEFT → coalesce.
            var result = EditModeManager.elementWasModified(line, 0);
            assertThat(result).isTrue();
            assertThat(line.getElement(0).getType()).isEqualTo(ElementType.REPEAT_LEFT_RIGHT);
            verify(scoreActions).clearSelection();
        }

        @Test
        void testRepeatLeftNotAdjacentToRepeatRightReturnsFalse() {
            // Inserting REPEAT_LEFT but the previous element is NOT REPEAT_RIGHT.
            var line = lineWith(ElementType.CROTCHET);
            EditModeManager.setPreviewElement(ElementType.REPEAT_LEFT.newInstance());
            var result = EditModeManager.elementWasModified(line, 1);
            assertThat(result).isFalse();
        }

        @Test
        void testRepeatRightNotAdjacentToRepeatLeftReturnsFalse() {
            // Inserting REPEAT_RIGHT but the element at index is NOT REPEAT_LEFT.
            var line = lineWith(ElementType.CROTCHET);
            EditModeManager.setPreviewElement(ElementType.REPEAT_RIGHT.newInstance());
            var result = EditModeManager.elementWasModified(line, 0);
            assertThat(result).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // previewElementDidChange() — rows 35 and 36
    // -------------------------------------------------------------------------

    @Nested
    class PreviewElementDidChange {

        private ScoreActions scoreActions;

        @BeforeEach
        void setUp() {
            scoreActions = mock(ScoreActions.class);
            EditModeManager.init(
                mock(ClipboardManager.class),
                mock(SelectionCoordinator.class),
                scoreActions,
                mock(ScoreView.class)
            );
            // These tests exercise non-playback behavior. Disable note playback so a real
            // PlayThread is never started (playInsertedNote is shared static state that a
            // sibling test may have left enabled). A leaked PlayThread sleeps, then sends a
            // stray NOTE_OFF to whatever MidiController.midiReceiver is current — contaminating
            // unrelated tests such as PlayThreadTest.
            EditModeManager.setPlayInsertedNote(false);
        }

        @Test
        void testTurnOffAccidentalInParensAfterInsert() {
            // Set up: ACCIDENTAL_IN_PARENS selected before the insert.
            Actions.ACCIDENTAL_IN_PARENS_ACTION.setSelected(true);
            var previewElement = ElementType.CROTCHET.newInstance();
            EditModeManager.setPreviewElement(previewElement);
            var line = lineWith(ElementType.CROTCHET);
            // Index 0 is the inserted element.
            EditModeManager.previewElementDidChange(line, 0);
            assertThat(Actions.ACCIDENTAL_IN_PARENS_ACTION.isSelected())
                .as("ACCIDENTAL_IN_PARENS_ACTION should be turned off after insert")
                .isFalse();
        }

        @Test
        void testCallsScoreActionsCallbacksAfterInsert() {
            // Verify setPreviewElement, drawWidthIfWiderLine, and repaint are all called.
            var previewElement = ElementType.CROTCHET.newInstance();
            EditModeManager.setPreviewElement(previewElement);
            var line = lineWith(ElementType.CROTCHET);
            EditModeManager.previewElementDidChange(line, 0);
            verify(scoreActions).setPreviewElement(any(StaffElement.class));
            verify(scoreActions).drawWidthIfWiderLine(line, false);
            verify(scoreActions).repaint();
        }

        @Test
        void testDoesNothingWhenPreviewElementIsNull() {
            // previewElement is null → method returns early without touching scoreActions.
            EditModeManager.setPreviewElement(null);
            var line = lineWith(ElementType.CROTCHET);
            EditModeManager.previewElementDidChange(line, 0);
            verify(scoreActions, never()).setPreviewElement(
                any()
            );
        }
    }

    @Nested
    class PreviewElementDidChangePlayThread {

        @BeforeEach
        void setUp() {
            EditModeManager.init(
                mock(ClipboardManager.class),
                mock(SelectionCoordinator.class),
                mock(ScoreActions.class),
                mock(ScoreView.class)
            );
        }

        @Test
        void testStartsPlayThreadWhenPlayInsertedNoteAndInsertedElementIsNote() {
            EditModeManager.setPlayInsertedNote(true);
            EditModeManager.setPreviewElement(ElementType.CROTCHET.newInstance());
            var line = lineWith(ElementType.CROTCHET);
            try (
                var playThreadConstruction =
                     mockConstruction(PlayThread.class)) {
                EditModeManager.previewElementDidChange(line, 0);
                assertThat(playThreadConstruction.constructed())
                    .as("PlayThread should be constructed when playInsertedNote=true and element is a note")
                    .hasSize(1);
                verify(playThreadConstruction.constructed().getFirst()).start();
            }
        }

        @Test
        void testDoesNotStartPlayThreadWhenPlayInsertedNoteFalse() {
            EditModeManager.setPlayInsertedNote(false);
            EditModeManager.setPreviewElement(ElementType.CROTCHET.newInstance());
            var line = lineWith(ElementType.CROTCHET);
            try (
                var playThreadConstruction =
                     mockConstruction(PlayThread.class)) {
                EditModeManager.previewElementDidChange(line, 0);
                assertThat(playThreadConstruction.constructed())
                    .as("PlayThread should not be constructed when playInsertedNote=false")
                    .isEmpty();
            }
        }

        @Test
        void testDoesNotStartPlayThreadWhenInsertedElementIsNotNote() {
            // A REST element's type.isNote() returns false → no PlayThread.
            EditModeManager.setPlayInsertedNote(true);
            EditModeManager.setPreviewElement(ElementType.CROTCHET_REST.newInstance());
            var line = lineWith(ElementType.CROTCHET_REST);
            try (
                var playThreadConstruction =
                     mockConstruction(PlayThread.class)) {
                EditModeManager.previewElementDidChange(line, 0);
                assertThat(playThreadConstruction.constructed())
                    .as("PlayThread should not be constructed when inserted element is a rest")
                    .isEmpty();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Insertion target lifecycle (refs #668)
    //
    // These drive a real Song and the real message bus on purpose: the whole point of
    // the two-slot design is *when* the SongDidChangeNotification arrives relative to
    // the modification bracket, and a hand-built notification would not reproduce that.
    // -------------------------------------------------------------------------

    @Nested
    class LastInsertion {

        private Song song;
        private Line line;

        @BeforeEach
        void setUp() {
            song = new Song();
            line = song.getLine(0);
            EditModeManager.init(
                mock(ClipboardManager.class),
                mock(SelectionCoordinator.class),
                mock(ScoreActions.class),
                mock(ScoreView.class)
            );
            // Keep a real PlayThread out of these tests; see PreviewElementDidChange.setUp.
            EditModeManager.setPlayInsertedNote(false);
            EditModeManager.setPreviewElement(ElementType.QUAVER.newInstance());
        }

        @Test
        void testLastInsertionIsNullBeforeAnyPlacement() {
            assertThat(EditModeManager.getLastInsertion())
                .as("nothing has been placed yet")
                .isNull();
        }

        /**
         * The arm must stay invisible for as long as any bracket is open — grace mode
         * nests a bracket inside the one handleClick opens, and only the outermost close
         * posts the notification that promotes the pending slot.
         */
        @Test
        void testArmBecomesVisibleOnlyWhenTheOutermostBracketCommits() {
            var note = ElementType.QUAVER.newInstance();

            song.withModification(() -> {
                song.withModification(() -> {
                    line.addElement(note);
                    EditModeManager.previewElementDidChange(line, indexOf(note));

                    assertThat(EditModeManager.getLastInsertion())
                        .as("the arm must not be visible inside the nested bracket")
                        .isNull();
                });

                assertThat(EditModeManager.getLastInsertion())
                    .as("the arm must not be visible while the outer bracket is still open")
                    .isNull();
            });

            assertThat(EditModeManager.getLastInsertion())
                .as("the arm becomes visible once the outermost bracket commits")
                .isNotNull();
        }

        /**
         * The append path arms the element it just added, and that arm survives the
         * notification its own bracket posts.
         */
        @Test
        void testAppendPathArmsTheAppendedElement() {
            var note = ElementType.QUAVER.newInstance();

            song.withModification(() -> {
                line.addElement(note);
                EditModeManager.previewElementDidChange(line, indexOf(note));
            });

            assertArmedElementIs(note);
        }

        /**
         * The insert path arms the element it inserted, not the one it displaced.
         */
        @Test
        void testInsertPathArmsTheInsertedElement() {
            var insertIndex = 1;
            var note = ElementType.QUAVER.newInstance();

            song.withoutMutationTracking(() -> {
                line.addElement(ElementType.CROTCHET.newInstance());
                line.addElement(ElementType.CROTCHET.newInstance());
            });

            song.withModification(() -> {
                line.addElement(insertIndex, note);
                EditModeManager.previewElementDidChange(line, insertIndex);
            });

            assertArmedElementIs(note);
        }

        /**
         * The replace path calls previewElementDidChange <em>after</em> its
         * {@code elementIndex--} correction — the correction it applies when replacing a
         * host note orphans the grace note ahead of it and removes it. The armed index
         * must therefore be the post-decrement one, which is where the replacement
         * actually ended up.
         */
        @Test
        void testReplacePathArmsThePostDecrementIndex() {
            var hostIndex = 1;
            var replacement = ElementType.CROTCHET_REST.newInstance();

            song.withoutMutationTracking(() -> {
                line.addElement(ElementType.QUAVER.newInstance());
                line.addElement(ElementType.CROTCHET.newInstance());
            });

            song.withModification(() -> {
                var elementIndex = hostIndex;
                line.setElement(elementIndex, replacement);
                // The orphaned element ahead of the replacement goes, shifting it down one.
                line.removeElement(elementIndex - 1);
                elementIndex--;
                EditModeManager.previewElementDidChange(line, elementIndex);
            });

            var insertion = requireLastInsertion();
            assertThat(insertion.elementIndex())
                .as("the armed index is the post-decrement one")
                .isEqualTo(hostIndex - 1);
            assertArmedElementIs(replacement);
        }

        @Test
        void testUnrelatedSongChangeClearsTheTarget() {
            placeNoteAtEnd();

            song.withModification(() -> line.addElement(ElementType.CROTCHET.newInstance()));

            assertThat(EditModeManager.getLastInsertion())
                .as("a song change that was not a placement clears the target")
                .isNull();
        }

        @Test
        void testModeChangeClearsTheTarget() {
            placeNoteAtEnd();

            MessageCenter.post(new ModeDidChangeNotification(Actions.SELECT_MODE_ACTION));

            assertThat(EditModeManager.getLastInsertion())
                .as("leaving edit mode invalidates the target")
                .isNull();
        }

        @Test
        void testDocumentLoadClearsTheTarget() {
            placeNoteAtEnd();

            MessageCenter.post(new DocumentDidLoadNotification(song));

            assertThat(EditModeManager.getLastInsertion())
                .as("a newly loaded document has no target")
                .isNull();
        }

        /**
         * Deleting the line the target lives in must clear it. A target that survived
         * would name an index in a Line no longer attached to the song, and the next
         * {@code b} would silently mutate that detached line.
         */
        @Test
        void testDeletingTheArmedLineClearsTheTarget() {
            var secondLine = new Line(song);
            song.addLine(secondLine);

            var note = ElementType.QUAVER.newInstance();

            song.withModification(() -> {
                secondLine.addElement(note);
                EditModeManager.previewElementDidChange(secondLine, secondLine.getElements().indexOf(note));
            });

            var armed = requireLastInsertion();
            assertThat(armed.line())
                .as("pre-condition: the target is on the line about to be deleted")
                .isSameAs(secondLine);

            song.removeLine(song.indexOfLine(secondLine));

            assertThat(EditModeManager.getLastInsertion())
                .as("deleting the line holding the target clears it")
                .isNull();
        }

        /**
         * The REPEAT merge bailout: {@code elementWasModified} replaces the neighbouring
         * REPEAT_LEFT with a merged REPEAT_LEFT_RIGHT and the caller bails out, arming
         * that merged element instead of inserting anything. The commit still follows, so
         * the arm survives — and what it points at is not beamable, which is why the
         * bailout can arm freely.
         */
        @Test
        void testRepeatMergeBailoutArmsANonBeamableElement() {
            var mergeIndex = 0;

            song.withoutMutationTracking(() -> line.addElement(ElementType.REPEAT_LEFT.newInstance()));

            EditModeManager.setPreviewElement(ElementType.REPEAT_RIGHT.newInstance());

            song.withModification(() -> {
                assertThat(EditModeManager.elementWasModified(line, mergeIndex))
                    .as("pre-condition: the REPEAT_RIGHT preview merges with the REPEAT_LEFT")
                    .isTrue();
                EditModeManager.previewElementDidChange(line, mergeIndex);
            });

            var insertion = requireLastInsertion();
            assertThat(insertion.elementIndex()).isEqualTo(mergeIndex);

            var armedType = insertion.line().getElement(insertion.elementIndex()).getType();
            assertThat(armedType)
                .as("the bailout arms the merged repeat")
                .isEqualTo(ElementType.REPEAT_LEFT_RIGHT);
            assertThat(armedType.isBeamable())
                .as("a merged repeat is not beamable, so arming it is harmless")
                .isFalse();
        }

        /**
         * A placement made while mutation tracking is suspended must not arm. Nothing is
         * posted while tracking is off, so an arm made there would never be claimed by the
         * edit that made it — it would sit in the pending slot until some later, unrelated
         * edit's commit adopted it as that edit's own placement.
         */
        @Test
        void testUntrackedPlacementDoesNotArm() {
            var note = ElementType.QUAVER.newInstance();

            song.withoutMutationTracking(() -> {
                line.addElement(note);
                EditModeManager.previewElementDidChange(line, indexOf(note));
            });

            song.withModification(() -> line.addElement(ElementType.CROTCHET.newInstance()));

            assertThat(EditModeManager.getLastInsertion())
                .as("an untracked placement leaves nothing for a later edit to adopt")
                .isNull();
        }

        /**
         * The grace-note round trip: entering grace mode places the note untracked, because
         * a lone grace note is not undoable, and cancelling removes it the same way. Neither
         * step posts anything. If the placement armed, the arm would outlive the note itself
         * and the next ordinary edit would inherit an index naming an element that no longer
         * exists — every element after it having shifted down by one.
         */
        @Test
        void testCancelledUntrackedPlacementLeavesNoTargetBehind() {
            var graceNote = ElementType.QUAVER.newInstance();

            song.withoutMutationTracking(() -> {
                line.addElement(graceNote);
                EditModeManager.previewElementDidChange(line, indexOf(graceNote));
            });

            song.withoutMutationTracking(() -> line.removeElement(indexOf(graceNote)));

            song.withModification(() -> line.addElement(ElementType.CROTCHET.newInstance()));

            assertThat(EditModeManager.getLastInsertion())
                .as("the cancelled placement must not resurface as the next edit's target")
                .isNull();
        }

        /**
         * The last-insertion keys act after their own edit has already committed — message
         * posting is synchronous — so by the time they can respond, the commit has consumed
         * the empty pending slot and cleared the target. They write the visible slot directly
         * instead, which is what makes the keys repeat: press {@code t} twice and the pair
         * ties, then unties.
         */
        @Test
        void testSetLastInsertionPointsTheKeysAtAnElementAfterACommitClearedTheTarget() {
            var note = quaver();

            song.withoutMutationTracking(() -> line.addElement(note));

            // Every edit but a placement arms nothing, so its commit clears the target.
            song.withModification(() -> line.addElement(crotchet()));

            assertThat(EditModeManager.getLastInsertion())
                .as("pre-condition: the commit left no target")
                .isNull();

            EditModeManager.setLastInsertion(line, indexOf(note));

            assertArmedElementIs(note);
        }

        /**
         * Writing the visible slot is not arming: it holds until the next song change, which
         * consumes the empty pending slot and clears it, exactly as a promoted arm would be.
         */
        @Test
        void testASetTargetIsClearedByTheNextUnrelatedEdit() {
            var note = quaver();

            song.withoutMutationTracking(() -> line.addElement(note));
            EditModeManager.setLastInsertion(line, indexOf(note));

            song.withModification(() -> line.addElement(crotchet()));

            assertThat(EditModeManager.getLastInsertion())
                .as("an unrelated edit takes the keys off the element they were pointed at")
                .isNull();
        }

        /** Places a quaver at the end of the line and commits, the way the append path does. */
        private void placeNoteAtEnd() {
            var note = ElementType.QUAVER.newInstance();

            song.withModification(() -> {
                line.addElement(note);
                EditModeManager.previewElementDidChange(line, indexOf(note));
            });

            assertArmedElementIs(note);
        }

        private int indexOf(StaffElement element) {
            return line.getElements().indexOf(element);
        }

        private EditModeManager.Insertion requireLastInsertion() {
            var insertion = EditModeManager.getLastInsertion();

            assertThat(insertion).as("expected a live insertion target").isNotNull();

            return insertion;
        }

        private void assertArmedElementIs(StaffElement expected) {
            var insertion = requireLastInsertion();
            assertThat(insertion.line())
                .as("the target names the line the element went into")
                .isSameAs(line);
            assertThat(insertion.line().getElement(insertion.elementIndex()))
                .as("the target index resolves to the placed element")
                .isSameAs(expected);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void resetEditModeManagerInstance(@Nullable EditModeManager value) {
        EditModeManager.setInstance(value);
    }

    private static void resetGraceModeManagerInstance(@Nullable GraceModeManager value) {
        GraceModeManager.setInstance(value);
    }

    private static void resetPasteModeManagerInstance(@Nullable PasteModeManager value) {
        PasteModeManager.setInstance(value);
    }
}
