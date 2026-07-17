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
package songscribe.ui.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.Strings;
import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;
import songscribe.dom.Tuplet;
import songscribe.ui.selection.TupletToggleInfo;

/**
 * Tests the runtime-computed {@code getUndoOpName()} overrides (Phase 4): {@link TupletAction}
 * and the barline/repeat replace {@link ElementTypeAction}. Both run on every dispatch —
 * including no-selection dispatches that only set pen/mode state — so each override must
 * null-guard the selection and return a safe base key rather than dereferencing a null
 * element.
 */
class DynamicOpNameOverrideTest extends MainFrameMockTest {

    @Nested
    class TupletLabel {

        @Test
        void testRemoveActionAlwaysNamesRemoveTuplet() {
            var action = TupletAction.createRemoveAction(mainFrame());

            assertThat(action.getUndoOpName())
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_REMOVE_TUPLET));
        }

        @Test
        void testNoExistingTupletReturnsAddSizeKey() {
            // Also the no-selection-safe path: with no tuplet at the selection the add
            // action falls back to its base "Add Triplet" key without dereferencing null.
            var action = TupletAction.createTripletAction(mainFrame());
            when(mockEnv().ctrl().canToggleTuplet())
                .thenReturn(new TupletToggleInfo(true, null, false));

            assertThat(action.getUndoOpName())
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_ADD_TRIPLET));
        }

        @Test
        void testExistingTupletReturnsChangeGradeKey() {
            var action = TupletAction.createTripletAction(mainFrame());
            when(mockEnv().ctrl().canToggleTuplet())
                .thenReturn(new TupletToggleInfo(true, mock(Tuplet.class), false));

            assertThat(action.getUndoOpName())
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_CHANGE_TUPLET_GRADE));
        }
    }

    @Nested
    class BarlineRepeatLabel {

        private ElementTypeAction singleBarlineAction() {
            var action = ElementTypeAction.createSingleBarlineAction(mainFrame());
            when(mockEnv().score().isInitialized()).thenReturn(true);
            return action;
        }

        private void selectElements(List<StaffElement> elements) {
            when(mockEnv().coordinator().getSelectedElements()).thenReturn(elements);
        }

        @Test
        void testBarlineSelectedNamesChangeBarline() {
            var action = singleBarlineAction();
            selectElements(List.of(ElementType.SINGLE_BARLINE.newInstance()));

            assertThat(action.getUndoOpName())
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_CHANGE_BARLINE));
        }

        @Test
        void testRepeatSelectedNamesChangeRepeat() {
            var action = singleBarlineAction();
            selectElements(List.of(ElementType.REPEAT_LEFT.newInstance()));

            assertThat(action.getUndoOpName())
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_CHANGE_REPEAT));
        }

        @Test
        void testNoSelectionReturnsBaseKeyWithoutThrowing() {
            // An empty selection must take the null-guard branch and return the static base
            // key (null for a barline replace action), never an NPE from a missing element.
            var action = singleBarlineAction();
            selectElements(List.of());

            assertThat(action.getUndoOpName()).isNull();
        }
    }
}
