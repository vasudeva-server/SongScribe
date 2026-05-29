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

import module java.desktop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import songscribe.MainFrameMockTest;
import songscribe.message.MessageCenter;
import songscribe.message.command.AddDynamicsCommand;

class AddDynamicsActionTest extends MainFrameMockTest {

    // Row 8: factory methods bind the isCrescendo field correctly

    @Test
    void testCreateCrescendoActionSetsCrescendoTrue() {
        var action = AddDynamicsAction.createCrescendoAction(mainFrame());
        assertThat(action.isCrescendo()).isTrue();
    }

    @Test
    void testCreateDiminuendoActionSetsCrescendoFalse() {
        var action = AddDynamicsAction.createDiminuendoAction(mainFrame());
        assertThat(action.isCrescendo()).isFalse();
    }

    // Row 9: actionPerformed posts AddDynamicsCommand with the correct isCrescendo flag

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ActionPerformed {

        private MockedStatic<MessageCenter> messageMock;

        @BeforeEach
        void setUpMessageCenter() {
            messageMock = mockStatic(MessageCenter.class);
        }

        @AfterEach
        void tearDownMessageCenter() {
            messageMock.close();
        }

        @Test
        void testActionPerformedPostsCrescendoCommand() {
            var action = AddDynamicsAction.createCrescendoAction(mainFrame());
            var e = new ActionEvent(new JButton(), ActionEvent.ACTION_PERFORMED, "add-crescendo");

            action.actionPerformed(e);

            var captor = ArgumentCaptor.forClass(AddDynamicsCommand.class);
            messageMock.verify(() -> MessageCenter.post(captor.capture()));
            assertThat(captor.getValue().isCrescendo()).isTrue();
        }

        @Test
        void testActionPerformedPostsDiminuendoCommand() {
            var action = AddDynamicsAction.createDiminuendoAction(mainFrame());
            var e = new ActionEvent(new JButton(), ActionEvent.ACTION_PERFORMED, "add-diminuendo");

            action.actionPerformed(e);

            var captor = ArgumentCaptor.forClass(AddDynamicsCommand.class);
            messageMock.verify(() -> MessageCenter.post(captor.capture()));
            // A mutant swapping the isCrescendo arg would flip this to true — this assertion catches it.
            assertThat(captor.getValue().isCrescendo()).isFalse();
        }
    }

    // Row 10: critical enablement flag combinations for REQUIRES_MULTIPLE_SELECTION,
    //         DISABLE_IN_REST_MODE, and DISABLE_WHEN_BAR_SELECTED

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class EnabledState {

        @Test
        void testDisabledWhenNoSelection() {
            // REQUIRES_MULTIPLE_SELECTION: 0 notes → disabled
            when(mockEnv().score().getSelectionSize()).thenReturn(0);

            var action = AddDynamicsAction.createCrescendoAction(mainFrame());
            action.updateEnabledState();

            assertThat(action.isEnabled()).isFalse();
        }

        @Test
        void testDisabledWhenSingleNoteSelected() {
            // REQUIRES_MULTIPLE_SELECTION: 1 note is not "multiple" → disabled
            when(mockEnv().score().getSelectionSize()).thenReturn(1);

            var action = AddDynamicsAction.createCrescendoAction(mainFrame());
            action.updateEnabledState();

            assertThat(action.isEnabled()).isFalse();
        }

        @Test
        void testEnabledWhenMultipleNotesSelected() {
            // REQUIRES_MULTIPLE_SELECTION: 2 notes → check passes.
            // With an active selection: enableFromBarSelection returns true immediately,
            // and enableFromSelection (DISABLE_WHEN_BAR_SELECTED path) defers to selectionHasDurations.
            when(mockEnv().score().getSelectionSize()).thenReturn(2);
            when(mockEnv().coordinator().hasActiveSelection()).thenReturn(true);
            when(mockEnv().coordinator().selectionHasDurations()).thenReturn(true);

            var action = AddDynamicsAction.createCrescendoAction(mainFrame());
            action.updateEnabledState();

            assertThat(action.isEnabled()).isTrue();
        }

        @Test
        void testDisabledInRestMode() {
            // DISABLE_IN_REST_MODE: selection contains rests → disabled even with multiple notes.
            when(mockEnv().score().getSelectionSize()).thenReturn(2);
            when(mockEnv().coordinator().hasActiveSelection()).thenReturn(true);
            when(mockEnv().coordinator().selectionHasDurations()).thenReturn(true);
            when(mockEnv().coordinator().selectionHasRests()).thenReturn(true);

            var action = AddDynamicsAction.createCrescendoAction(mainFrame());
            action.updateEnabledState();

            assertThat(action.isEnabled()).isFalse();
        }
    }
}
