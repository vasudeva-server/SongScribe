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

package songscribe.ui.selection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static songscribe.dom.StaffElementFactory.crotchet;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.dom.Ending;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.ui.component.ScoreView;
import songscribe.ui.action.AccidentalAction;
import songscribe.ui.action.ArticulationAction;
import songscribe.ui.action.DotAction;
import songscribe.ui.action.ElementTypeAction;
import songscribe.ui.action.FermataAction;
import songscribe.ui.action.UIAction;

/**
 * Covers {@link ActionReflector}'s save/restore of action states across a selection: a
 * selection snapshots the pre-selection states, and clearing it puts them back.
 */
class ActionReflectorSaveRestoreTest extends MainFrameMockTest {

    private ElementTypeAction crotchetAction;
    private ElementTypeAction minimAction;
    private AccidentalAction sharpAction;
    private DotAction dotAction;
    private FermataAction fermataAction;
    private ArticulationAction staccatoAction;

    @BeforeEach
    void setUp() {
        var mainFrame = mainFrame();
        crotchetAction = ElementTypeAction.createQuarterNoteAction(mainFrame);
        minimAction = ElementTypeAction.createHalfNoteAction(mainFrame);
        sharpAction = AccidentalAction.createSharpAction(mainFrame);
        dotAction = DotAction.createDotAction(mainFrame);
        fermataAction = FermataAction.createAction(mainFrame);
        staccatoAction = ArticulationAction.createStaccatoAction(mainFrame);
    }

    private List<UIAction.Reflectable> allActions() {
        return List.of(
            crotchetAction, minimAction,
            sharpAction, dotAction,
            fermataAction, staccatoAction
        );
    }

    private SelectionCoordinator createCoordinator() {
        return ReflectionTestHelper.createCoordinator(
            List.of(crotchet(), crotchet()),
            allActions()
        );
    }

    @Test
    void testClearSelectionRestoresState() {
        var coordinator = createCoordinator();

        // Set up a known pre-selection state: enable and select crotchet
        crotchetAction.setEnabled(true);
        crotchetAction.setSelected(true);
        sharpAction.setEnabled(true);
        sharpAction.setSelected(true);

        // Selecting saves the states.
        ReflectionTestHelper.selectNote(coordinator, 0);

        // Drive the actions to a new state, as reflection would.
        crotchetAction.setEnabled(false);
        crotchetAction.setSelected(false);
        sharpAction.setEnabled(false);
        sharpAction.setSelected(false);

        // Clear selection and restore
        ReflectionTestHelper.clearSelection(coordinator);

        // Verify pre-selection state is restored
        assertThat(crotchetAction.isEnabled()).isTrue();
        assertThat(crotchetAction.isSelected()).isTrue();
        assertThat(sharpAction.isEnabled()).isTrue();
        assertThat(sharpAction.isSelected()).isTrue();
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SaveRestore {

        @Test
        void testSaveOccursOnSelection() {
            var coordinator = createCoordinator();

            // Set a known state before selection
            fermataAction.setEnabled(true);
            fermataAction.setSelected(true);

            // Selecting should save the states.
            ReflectionTestHelper.selectNote(coordinator, 0);

            // Drive the action to a new state, as reflection would.
            fermataAction.setEnabled(false);
            fermataAction.setSelected(false);

            // Restore should bring back the saved state, proving save happened
            coordinator.getActionReflector().restoreActionStates();
            assertThat(fermataAction.isEnabled()).isTrue();
            assertThat(fermataAction.isSelected()).isTrue();
        }

        /**
         * An ending selection nulls out the element selection just as a slide selection
         * does, so the save/restore handler must treat it as a selection (save) rather
         * than as a cleared selection (restore) — otherwise selecting an ending snaps the
         * toolbar back to whatever was last saved.
         */
        @Test
        void testEndingSelectionSavesRatherThanRestoresActionStates() {
            var coordinator = createCoordinator();
            var ending = new Ending(
                crotchet(), crotchet());

            fermataAction.setEnabled(true);
            fermataAction.setSelected(true);
            ReflectionTestHelper.selectEnding(coordinator, ending);

            // Drive the actions to a new state, as reflection would.
            fermataAction.setEnabled(false);
            fermataAction.setSelected(false);

            var scoreView = mock(ScoreView.class);
            when(scoreView.getSelectionSize()).thenReturn(0);
            when(scoreView.getSelectionCoordinator()).thenReturn(coordinator);

            coordinator.getActionReflector().musicSelectionDidChangeSaveRestoreActionStates(
                new MusicSelectionDidChangeNotification(scoreView));

            // A restore here would resurrect the pre-selection state.
            assertThat(fermataAction.isEnabled())
                .as("action state is saved, not restored, while an ending is selected")
                .isFalse();
            assertThat(fermataAction.isSelected()).isFalse();
        }
    }
}
