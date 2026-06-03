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

import java.lang.reflect.Field;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Articulation;
import songscribe.dom.ArticulationType;
import songscribe.dom.ElementType;
import songscribe.dom.FermataAttachment;
import songscribe.dom.StaffElement;
import songscribe.ui.action.Actions;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.selection.SelectionCoordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EditModeManagerTest extends UnitTest {

    // Resets the EditModeManager and GraceModeManager singletons between tests.
    @AfterEach
    void tearDownSingletons() throws Exception {
        resetEditModeManagerInstance(null);
        resetGraceModeManagerInstance(null);
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
                mock(ScoreActions.class)
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
            // Rests must not receive accidental, fermata, or articulation decorations.
            Actions.ACCIDENTAL_ACTION_GROUP.setSelected(Actions.SHARP_ACTION, true);
            Actions.FERMATA_ACTION.setSelected(true);
            Actions.ACCENT_ACTION.setSelected(true);
            var rest = ElementType.CROTCHET_REST.newInstance();
            EditModeManager.decorateElement(rest);
            assertThat(rest.getAccidental()).isNull();
            assertThat(rest.findAttachment(FermataAttachment.class)).isNull();
            assertThat(rest.getArticulations()).isEmpty();
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
        void testAddsFermataAttachmentWhenFermataActionSelected() {
            Actions.FERMATA_ACTION.setSelected(true);
            var element = ElementType.CROTCHET.newInstance();
            EditModeManager.decorateElement(element);
            assertThat(element.findAttachment(FermataAttachment.class)).isNotNull();
        }

        @Test
        void testRemovesFermataAttachmentWhenFermataActionNotSelected() {
            Actions.FERMATA_ACTION.setSelected(false);
            var element = ElementType.CROTCHET.newInstance();
            element.addAttachment(new FermataAttachment(element));
            EditModeManager.decorateElement(element);
            assertThat(element.findAttachment(FermataAttachment.class)).isNull();
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
                scoreActions
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
    // Helpers
    // -------------------------------------------------------------------------

    private static void resetEditModeManagerInstance(@Nullable EditModeManager value)
        throws Exception {
        var field = EditModeManager.class.getDeclaredField("INSTANCE");
        field.setAccessible(true);
        field.set(null, value);
    }

    private static void resetGraceModeManagerInstance(@Nullable GraceModeManager value)
        throws Exception {
        var field = GraceModeManager.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, value);
    }
}
