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
import org.mockito.MockedConstruction;

import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JRootPane;

import songscribe.UnitTest;
import songscribe.dom.Articulation;
import songscribe.dom.ArticulationType;
import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;
import songscribe.ui.action.Actions;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreView;
import songscribe.ui.playback.PlayThread;
import songscribe.ui.selection.SelectionCoordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EditModeManagerTest extends UnitTest {

    // These tests read and mutate the Actions.* constants (accent, articulation, dot,
    // accidental, rest, ...) but do not otherwise own Actions. Initialize them here with a
    // minimal mock frame rather than depending on some earlier test class having called
    // Actions.initialize() in the shared JVM — that hidden ordering coupling breaks the
    // moment the class runs in a fresh JVM. UnitTest's teardown unsubscribes the actions.
    @BeforeEach
    void initializeActions() {
        var mockFrame = mock(MainFrame.class);
        var mockScore = mock(ScoreView.class);
        var mockRootPane = mock(JRootPane.class);
        when(mockRootPane.getInputMap(anyInt())).thenReturn(new InputMap());
        when(mockRootPane.getActionMap()).thenReturn(new ActionMap());
        when(mockFrame.getRootPane()).thenReturn(mockRootPane);
        when(mockFrame.requireScoreView()).thenReturn(mockScore);
        when(mockFrame.getScoreView()).thenReturn(mockScore);
        when(mockScore.getSelectionCoordinator()).thenReturn(mock(SelectionCoordinator.class));
        Actions.initialize(mockFrame);
    }

    // Resets the EditModeManager, GraceModeManager, and PasteModeManager singletons
    // between tests. EditModeManager.init() (called by several nested @BeforeEach
    // setups below) constructs a PasteModeManager, which sets its own static
    // instance as a side effect — that leak must be reset here too, or a later
    // test class's PasteModeManager.isActive()/getActiveInstance() calls see a
    // zombie instance wired to this test's torn-down mocks.
    @AfterEach
    void tearDownSingletons() {
        resetEditModeManagerInstance(null);
        resetGraceModeManagerInstance(null);
        resetPasteModeManagerInstance(null);
        // Restore action state so tests don't bleed into each other.
        Actions.REST_ACTION.setSelected(false);
        Actions.DOT_ACTION_GROUP.clearSelection();
        Actions.ACCIDENTAL_ACTION_GROUP.clearSelection();
        Actions.ARTICULATION_ACTION_GROUP.clearSelection();
        Actions.FERMATA_ACTION.setSelected(false);
        Actions.ACCIDENTAL_IN_PARENS_ACTION.setSelected(false);
        Actions.ACCENT_ACTION.setSelected(false);
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
            assertThatThrownBy(() -> EditModeManager.getGraceModeManager())
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
            Actions.ARTICULATION_ACTION_GROUP.setSelected(Actions.STACCATO_ACTION, true);
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
        void testAddsStaccatoArticulationWhenArticulationGroupSelected() {
            Actions.ARTICULATION_ACTION_GROUP.setSelected(Actions.STACCATO_ACTION, true);
            var element = ElementType.CROTCHET.newInstance();
            EditModeManager.decorateElement(element);
            var articulations = element.getArticulations();
            assertThat(articulations).hasSize(1);
            assertThat(articulations.get(0).getType()).isEqualTo(ArticulationType.STACCATO);
        }

        @Test
        void testAddsAccentArticulationWhenAccentActionSelected() {
            Actions.ACCENT_ACTION.setSelected(true);
            var element = ElementType.CROTCHET.newInstance();
            EditModeManager.decorateElement(element);
            var articulations = element.getArticulations();
            assertThat(articulations).hasSize(1);
            assertThat(articulations.get(0).getType()).isEqualTo(ArticulationType.ACCENT);
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
            verify(scoreActions).setPreviewElement(org.mockito.ArgumentMatchers.any(StaffElement.class));
            verify(scoreActions).drawWidthIfWiderLine(line, false);
            verify(scoreActions).repaint();
        }

        @Test
        void testDoesNothingWhenPreviewElementIsNull() {
            // previewElement is null → method returns early without touching scoreActions.
            EditModeManager.setPreviewElement(null);
            var line = lineWith(ElementType.CROTCHET);
            EditModeManager.previewElementDidChange(line, 0);
            verify(scoreActions, org.mockito.Mockito.never()).setPreviewElement(
                org.mockito.ArgumentMatchers.any()
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
            try (MockedConstruction<PlayThread> playThreadConstruction =
                     mockConstruction(PlayThread.class)) {
                EditModeManager.previewElementDidChange(line, 0);
                assertThat(playThreadConstruction.constructed())
                    .as("PlayThread should be constructed when playInsertedNote=true and element is a note")
                    .hasSize(1);
                verify(playThreadConstruction.constructed().get(0)).start();
            }
        }

        @Test
        void testDoesNotStartPlayThreadWhenPlayInsertedNoteFalse() {
            EditModeManager.setPlayInsertedNote(false);
            EditModeManager.setPreviewElement(ElementType.CROTCHET.newInstance());
            var line = lineWith(ElementType.CROTCHET);
            try (MockedConstruction<PlayThread> playThreadConstruction =
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
            try (MockedConstruction<PlayThread> playThreadConstruction =
                     mockConstruction(PlayThread.class)) {
                EditModeManager.previewElementDidChange(line, 0);
                assertThat(playThreadConstruction.constructed())
                    .as("PlayThread should not be constructed when inserted element is a rest")
                    .isEmpty();
            }
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
