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

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.music.ElementType;
import songscribe.music.StaffElement;
import songscribe.ui.action.AccidentalAction;
import songscribe.ui.action.DotAction;
import songscribe.ui.action.DurationArticulationAction;
import songscribe.ui.action.ElementTypeAction;
import songscribe.ui.action.FermataAction;
import songscribe.ui.action.UIAction;

class GlissandoReflectionTest extends UnitTest {

    private ElementTypeAction crotchetAction;
    private ElementTypeAction minimAction;
    private ElementTypeAction glissandoAction;
    private ElementTypeAction slideOutAction;
    private AccidentalAction sharpAction;
    private DotAction dotAction;
    private FermataAction fermataAction;
    private DurationArticulationAction staccatoAction;

    @BeforeEach
    void setUp() {
        crotchetAction = ElementTypeAction.createQuarterNoteAction();
        minimAction = ElementTypeAction.createHalfNoteAction();
        glissandoAction = ElementTypeAction.createGlissandoAction();
        slideOutAction = ElementTypeAction.createSlideOutAction();
        sharpAction = AccidentalAction.createSharpAction();
        dotAction = DotAction.createDotAction();
        fermataAction = FermataAction.createAction();
        staccatoAction = DurationArticulationAction.createStaccatoAction();
    }

    private List<UIAction.Reflectable> allActions() {
        return List.of(
            crotchetAction, minimAction,
            glissandoAction, slideOutAction,
            sharpAction, dotAction,
            fermataAction, staccatoAction
        );
    }

    private SelectionCoordinator createCoordinatorWithGlissando(StaffElement.Glissando.Type type) {
        var note1 = ElementType.CROTCHET.newInstance();
        var note2 = ElementType.CROTCHET.newInstance();
        note1.setGlissando(type);

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
        var coordinator = createCoordinatorWithGlissando(StaffElement.Glissando.Type.CONNECTED);

        // Set up a known pre-selection state: enable and select crotchet
        crotchetAction.setEnabled(true);
        crotchetAction.setSelected(true);
        sharpAction.setEnabled(true);
        sharpAction.setSelected(true);

        // Select glissando (saves state, then reflection modifies it)
        ReflectionTestHelper.selectGlissando(coordinator, 0);
        coordinator.musicSelectionDidChangeReflectSelection(null);

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
        var coordinator = createCoordinatorWithGlissando(StaffElement.Glissando.Type.CONNECTED);
        ReflectionTestHelper.selectGlissando(coordinator, 0);
        coordinator.musicSelectionDidChangeReflectSelection(null);

        assertSelectedAndEnabled(glissandoAction, true, true);
        assertSelectedAndEnabled(slideOutAction, false, false);
        assertSelectedAndEnabled(crotchetAction, false, false);
        assertSelectedAndEnabled(minimAction, false, false);
        assertSelectedAndEnabled(sharpAction, false, false);
        assertSelectedAndEnabled(dotAction, false, false);
        assertSelectedAndEnabled(fermataAction, false, false);
        assertSelectedAndEnabled(staccatoAction, false, false);
    }

    @Test
    void testNonMatchingGlissandoToolDisabled() {
        // When CONNECTED is selected, SLIDE_OUT should be disabled
        var coordinator = createCoordinatorWithGlissando(StaffElement.Glissando.Type.CONNECTED);
        ReflectionTestHelper.selectGlissando(coordinator, 0);
        coordinator.musicSelectionDidChangeReflectSelection(null);

        assertSelectedAndEnabled(glissandoAction, true, true);
        assertSelectedAndEnabled(slideOutAction, false, false);

        // And vice versa
        var coordinator2 = createCoordinatorWithGlissando(StaffElement.Glissando.Type.SLIDE_OUT);
        ReflectionTestHelper.selectGlissando(coordinator2, 0);
        coordinator2.musicSelectionDidChangeReflectSelection(null);

        assertSelectedAndEnabled(slideOutAction, true, true);
        assertSelectedAndEnabled(glissandoAction, false, false);
    }

    @Test
    void testNoteWithGlissandoSelectedDoesNotTriggerGlissandoReflection() {
        var coordinator = createCoordinatorWithGlissando(StaffElement.Glissando.Type.CONNECTED);

        // Select the note itself, not its glissando
        ReflectionTestHelper.selectNote(coordinator, 0);
        coordinator.musicSelectionDidChangeReflectSelection(null);

        // Normal reflection: crotchet should be selected (it's a crotchet note)
        assertThat(crotchetAction.isSelected()).isTrue();

        // Glissando action should NOT be selected (normal reflection, not glissando reflection)
        assertThat(glissandoAction.isSelected()).isFalse();
    }

    @Test
    void testSlideOutSelectedReflectsSlideOutAction() {
        var coordinator = createCoordinatorWithGlissando(StaffElement.Glissando.Type.SLIDE_OUT);
        ReflectionTestHelper.selectGlissando(coordinator, 0);
        coordinator.musicSelectionDidChangeReflectSelection(null);

        assertSelectedAndEnabled(slideOutAction, true, true);
        assertSelectedAndEnabled(glissandoAction, false, false);
        assertSelectedAndEnabled(crotchetAction, false, false);
        assertSelectedAndEnabled(minimAction, false, false);
        assertSelectedAndEnabled(sharpAction, false, false);
        assertSelectedAndEnabled(dotAction, false, false);
        assertSelectedAndEnabled(fermataAction, false, false);
        assertSelectedAndEnabled(staccatoAction, false, false);
    }

    @Nested
    class SaveRestore {

        @Test
        void testSaveOccursOnGlissandoSelection() {
            var coordinator = createCoordinatorWithGlissando(StaffElement.Glissando.Type.CONNECTED);

            // Set a known state before selection
            fermataAction.setEnabled(true);
            fermataAction.setSelected(true);

            // Select glissando — this should save states
            ReflectionTestHelper.selectGlissando(coordinator, 0);
            coordinator.musicSelectionDidChangeReflectSelection(null);

            // Fermata should now be disabled by reflection
            assertThat(fermataAction.isEnabled()).isFalse();
            assertThat(fermataAction.isSelected()).isFalse();

            // Restore should bring back the saved state, proving save happened
            coordinator.restoreActionStates();
            assertThat(fermataAction.isEnabled()).isTrue();
            assertThat(fermataAction.isSelected()).isTrue();
        }
    }
}
