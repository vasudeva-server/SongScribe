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
import songscribe.message.notification.MusicSelectionDidChangeNotification;

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

            assertThat(action.isEnabled()).as("no selection must disable the action").isFalse();
        }

        @Test
        void testDisabledWhenSingleNoteSelected() {
            // REQUIRES_MULTIPLE_SELECTION: 1 note is not "multiple" → disabled
            when(mockEnv().score().getSelectionSize()).thenReturn(1);

            var action = AddDynamicsAction.createCrescendoAction(mainFrame());
            action.updateEnabledState();

            assertThat(action.isEnabled()).as("a single selected note must disable the action").isFalse();
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

            assertThat(action.isEnabled()).as("multiple selected notes must enable the action").isTrue();
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

            assertThat(action.isEnabled()).as("a selection containing rests must disable the action").isFalse();
        }
    }

    // Row 11: musicSelectionDidChange forwards canAddDynamicsToSelection() to the enabled
    // state, gated by updateEnabledState() and a non-null ScoreViewController

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class MusicSelectionDidChange {

        // REQUIRES_MULTIPLE_SELECTION: selectionSize must be > 1 so updateEnabledState() passes
        // and delegates the final enabled state to canAddDynamicsToSelection().
        private static final int MULTIPLE_SELECTION_SIZE = 2;

        // 1 note is not "multiple", so updateEnabledState() fails REQUIRES_MULTIPLE_SELECTION.
        private static final int SINGLE_SELECTION_SIZE = 1;

        @BeforeEach
        void setUpMultipleSelection() {
            when(mockEnv().score().getSelectionSize()).thenReturn(MULTIPLE_SELECTION_SIZE);
            when(mockEnv().coordinator().hasActiveSelection()).thenReturn(true);
            when(mockEnv().coordinator().selectionHasDurations()).thenReturn(true);
        }

        @Test
        void testDisabledWhenCanAddDynamicsToSelectionReturnsFalse() {
            // canAddDynamicsToSelection() = false is forwarded directly to setEnabled().
            when(mockEnv().ctrl().canAddDynamicsToSelection()).thenReturn(false);
            var action = AddDynamicsAction.createCrescendoAction(mainFrame());

            action.musicSelectionDidChange(new MusicSelectionDidChangeNotification(mockEnv().score()));

            assertThat(action.isEnabled())
                .as("a false result from canAddDynamicsToSelection() must disable the action")
                .isFalse();
        }

        @Test
        void testEnabledWhenCanAddDynamicsToSelectionReturnsTrue() {
            // canAddDynamicsToSelection() = true is forwarded directly to setEnabled().
            when(mockEnv().ctrl().canAddDynamicsToSelection()).thenReturn(true);
            var action = AddDynamicsAction.createDiminuendoAction(mainFrame());

            action.musicSelectionDidChange(new MusicSelectionDidChangeNotification(mockEnv().score()));

            assertThat(action.isEnabled())
                .as("a true result from canAddDynamicsToSelection() must enable the action")
                .isTrue();
        }

        @Test
        void testStaysDisabledWhenUpdateEnabledStateReturnsFalse() {
            // Single-note selection fails REQUIRES_MULTIPLE_SELECTION, so updateEnabledState()
            // returns false and canAddDynamicsToSelection() must never override that to true.
            when(mockEnv().score().getSelectionSize()).thenReturn(SINGLE_SELECTION_SIZE);
            when(mockEnv().ctrl().canAddDynamicsToSelection()).thenReturn(true);
            var action = AddDynamicsAction.createCrescendoAction(mainFrame());

            action.musicSelectionDidChange(new MusicSelectionDidChangeNotification(mockEnv().score()));

            assertThat(action.isEnabled())
                .as("a false updateEnabledState() result must not be overridden by canAddDynamicsToSelection()")
                .isFalse();
        }

        @Test
        void testEnabledStateUnchangedWhenScoreViewControllerIsNull() {
            // A null controller short-circuits the handler entirely, leaving the action's
            // enabled state at whatever it was before the notification (false, from construction).
            when(mockEnv().score().getController()).thenReturn(null);
            when(mockEnv().ctrl().canAddDynamicsToSelection()).thenReturn(true);
            var action = AddDynamicsAction.createCrescendoAction(mainFrame());

            action.musicSelectionDidChange(new MusicSelectionDidChangeNotification(mockEnv().score()));

            assertThat(action.isEnabled())
                .as("a null ScoreViewController must leave the action's enabled state untouched")
                .isFalse();
        }
    }
}
