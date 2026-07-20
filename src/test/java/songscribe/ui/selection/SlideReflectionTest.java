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

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.dom.ElementType;
import songscribe.layout.Ending;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.ui.component.ScoreView;
import songscribe.ui.action.AccidentalAction;
import songscribe.ui.action.DotAction;
import songscribe.ui.action.DurationArticulationAction;
import songscribe.ui.action.ElementTypeAction;
import songscribe.ui.action.FermataAction;
import songscribe.ui.action.UIAction;

class SlideReflectionTest extends MainFrameMockTest {

    private ElementTypeAction crotchetAction;
    private ElementTypeAction minimAction;
    private ElementTypeAction glissandoAction;
    private ElementTypeAction fallAction;
    private AccidentalAction sharpAction;
    private DotAction dotAction;
    private FermataAction fermataAction;
    private DurationArticulationAction staccatoAction;

    @BeforeEach
    void setUp() {
        var mainFrame = mainFrame();
        crotchetAction = ElementTypeAction.createQuarterNoteAction(mainFrame);
        minimAction = ElementTypeAction.createHalfNoteAction(mainFrame);
        glissandoAction = ElementTypeAction.createGlissandoAction(mainFrame);
        fallAction = ElementTypeAction.createFallAction(mainFrame);
        sharpAction = AccidentalAction.createSharpAction(mainFrame);
        dotAction = DotAction.createDotAction(mainFrame);
        fermataAction = FermataAction.createAction(mainFrame);
        staccatoAction = DurationArticulationAction.createStaccatoAction(mainFrame);
    }

    private List<UIAction.Reflectable> allActions() {
        return List.of(
            crotchetAction, minimAction,
            glissandoAction, fallAction,
            sharpAction, dotAction,
            fermataAction, staccatoAction
        );
    }

    private SelectionCoordinator createCoordinatorWithGlissando() {
        var note1 = ElementType.CROTCHET.newInstance();
        var note2 = ElementType.CROTCHET.newInstance();
        note1.setGlissando();

        return ReflectionTestHelper.createCoordinator(
            List.of(note1, note2), allActions()
        );
    }

    private SelectionCoordinator createCoordinatorWithFall() {
        var note1 = ElementType.CROTCHET.newInstance();
        var note2 = ElementType.CROTCHET.newInstance();
        note1.setFall();

        return ReflectionTestHelper.createCoordinator(
            List.of(note1, note2), allActions()
        );
    }

    private void assertSelectedAndEnabled(UIAction action, boolean selected, boolean enabled) {
        if (action instanceof UIAction.Selectable selectable) {
            assertThat(selectable.isSelected())
                .as("%s selected", action.getClass().getSimpleName())
                .isEqualTo(selected);
        }

        assertThat(action.isEnabled())
            .as("%s enabled", action.getClass().getSimpleName())
            .isEqualTo(enabled);
    }

    @Test
    void testClearGlissandoSelectionRestoresState() {
        var coordinator = createCoordinatorWithGlissando();

        // Set up a known pre-selection state: enable and select crotchet
        crotchetAction.setEnabled(true);
        crotchetAction.setSelected(true);
        sharpAction.setEnabled(true);
        sharpAction.setSelected(true);

        // Select glissando (saves state, then reflection modifies it)
        ReflectionTestHelper.selectGlissando(coordinator, 0);
        coordinator.triggerReflection();

        // Verify reflection changed state
        assertThat(crotchetAction.isEnabled()).isFalse();
        assertThat(glissandoAction.isSelected()).isTrue();

        // Clear selection and restore
        ReflectionTestHelper.clearSelection(coordinator);

        // Verify pre-selection state is restored
        assertThat(crotchetAction.isEnabled()).isTrue();
        assertThat(crotchetAction.isSelected()).isTrue();
        assertThat(sharpAction.isEnabled()).isTrue();
        assertThat(sharpAction.isSelected()).isTrue();
    }

    @Test
    void testConnectedGlissandoSelectedReflectsGlissandoAction() {
        var coordinator = createCoordinatorWithGlissando();
        ReflectionTestHelper.selectGlissando(coordinator, 0);
        coordinator.triggerReflection();

        assertSelectedAndEnabled(glissandoAction, true, true);
        assertSelectedAndEnabled(fallAction, false, false);
        assertSelectedAndEnabled(crotchetAction, false, false);
        assertSelectedAndEnabled(minimAction, false, false);
        assertSelectedAndEnabled(sharpAction, false, false);
        assertSelectedAndEnabled(dotAction, false, false);
        assertSelectedAndEnabled(fermataAction, false, false);
        assertSelectedAndEnabled(staccatoAction, false, false);
    }

    @Test
    void testNonMatchingGlissandoToolDisabled() {
        // When CONNECTED is selected, fall should be disabled
        var coordinator = createCoordinatorWithGlissando();
        ReflectionTestHelper.selectGlissando(coordinator, 0);
        coordinator.triggerReflection();

        assertSelectedAndEnabled(glissandoAction, true, true);
        assertSelectedAndEnabled(fallAction, false, false);

        // And vice versa
        var coordinator2 = createCoordinatorWithFall();
        ReflectionTestHelper.selectGlissando(coordinator2, 0);
        coordinator2.triggerReflection();

        assertSelectedAndEnabled(fallAction, true, true);
        assertSelectedAndEnabled(glissandoAction, false, false);
    }

    @Test
    void testNoteWithGlissandoSelectedDoesNotTriggerGlissandoReflection() {
        var coordinator = createCoordinatorWithGlissando();

        // Select the note itself, not its glissando
        ReflectionTestHelper.selectNote(coordinator, 0);
        coordinator.triggerReflection();

        // Normal reflection: crotchet should be selected (it's a crotchet note)
        assertThat(crotchetAction.isSelected()).isTrue();

        // Glissando action should NOT be selected (normal reflection, not glissando reflection)
        assertThat(glissandoAction.isSelected()).isFalse();
    }

    @Test
    void testFallSelectedReflectsFallAction() {
        var coordinator = createCoordinatorWithFall();
        ReflectionTestHelper.selectGlissando(coordinator, 0);
        coordinator.triggerReflection();

        assertSelectedAndEnabled(fallAction, true, true);
        assertSelectedAndEnabled(glissandoAction, false, false);
        assertSelectedAndEnabled(crotchetAction, false, false);
        assertSelectedAndEnabled(minimAction, false, false);
        assertSelectedAndEnabled(sharpAction, false, false);
        assertSelectedAndEnabled(dotAction, false, false);
        assertSelectedAndEnabled(fermataAction, false, false);
        assertSelectedAndEnabled(staccatoAction, false, false);
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SaveRestore {

        @Test
        void testSaveOccursOnGlissandoSelection() {
            var coordinator = createCoordinatorWithGlissando();

            // Set a known state before selection
            fermataAction.setEnabled(true);
            fermataAction.setSelected(true);

            // Select glissando — this should save states
            ReflectionTestHelper.selectGlissando(coordinator, 0);
            coordinator.triggerReflection();

            // Fermata should now be disabled by reflection
            assertThat(fermataAction.isEnabled()).isFalse();
            assertThat(fermataAction.isSelected()).isFalse();

            // Restore should bring back the saved state, proving save happened
            coordinator.restoreActionStates();
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
            var coordinator = createCoordinatorWithGlissando();
            var ending = new Ending(
                ElementType.CROTCHET.newInstance(), ElementType.CROTCHET.newInstance());

            fermataAction.setEnabled(true);
            fermataAction.setSelected(true);
            ReflectionTestHelper.selectEnding(coordinator, ending);

            // Drive the actions to a new state, as reflection would.
            fermataAction.setEnabled(false);
            fermataAction.setSelected(false);

            var scoreView = mock(ScoreView.class);
            when(scoreView.getSelectionSize()).thenReturn(0);
            when(scoreView.getSelectionCoordinator()).thenReturn(coordinator);

            coordinator.musicSelectionDidChangeSaveRestoreActionStates(
                new MusicSelectionDidChangeNotification(scoreView));

            // A restore here would resurrect the pre-selection state.
            assertThat(fermataAction.isEnabled())
                .as("action state is saved, not restored, while an ending is selected")
                .isFalse();
            assertThat(fermataAction.isSelected()).isFalse();
        }
    }
}
