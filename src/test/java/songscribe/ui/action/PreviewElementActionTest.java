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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.MainFrameMockTest;
import songscribe.message.MessageCenter;
import songscribe.message.command.UpdatePreviewElementCommand;
import songscribe.ui.selection.ElementSelection;

// Rows 56-58: PreviewElementAction.actionPerformed — keyboard-shortcut toggle and
// command-posting behaviour. DotAction (a concrete non-overriding subclass) is
// used so the parent's actionPerformed body runs without further indirection.
class PreviewElementActionTest extends MainFrameMockTest {

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

        // Row 56: when the event source is a JRootPane (keyboard shortcut path),
        // toggleOnKeyboardShortcut flips the selected state. Swing buttons toggle
        // themselves; keyboard shortcuts need explicit toggling.
        @Test
        void testActionPerformedTogglesSelectionWhenSourceIsJRootPane() {
            var action = DotAction.createDotAction(mainFrame());
            assertThat(action.isSelected()).isFalse();

            action.actionPerformed(
                new ActionEvent(new JRootPane(), ActionEvent.ACTION_PERFORMED, "add-dot")
            );

            assertThat(action.isSelected()).isTrue();
        }

        // Row 57: when a selection is active, applyToSelectionIfActive() returns true
        // and the method returns early — no UpdatePreviewElementCommand is posted.
        @Test
        void testActionPerformedWithActiveSelectionPostsNoUpdatePreviewElementCommand() {
            // Non-null selection makes applyToSelectionIfActive() return true.
            when(mockEnv().coordinator().getSelection())
                .thenReturn(mock(ElementSelection.class));

            var action = DotAction.createDotAction(mainFrame());
            action.actionPerformed(
                new ActionEvent(new JButton(), ActionEvent.ACTION_PERFORMED, "add-dot")
            );

            // UIAction's constructor calls MessageCenter.subscribe, which is fine.
            // What must NOT happen is a post() — the command would mean no selection was applied.
            messageMock.verify(() -> MessageCenter.post(any()), never());
        }

        // Row 58: when no selection is active, applyToSelectionIfActive() returns false
        // and an UpdatePreviewElementCommand is posted to update the preview element.
        @Test
        void testActionPerformedWithNoSelectionPostsUpdatePreviewElementCommand() {
            // coordinator.getSelection() returns null by default — no active selection.

            var action = DotAction.createDotAction(mainFrame());
            action.actionPerformed(
                new ActionEvent(new JButton(), ActionEvent.ACTION_PERFORMED, "add-dot")
            );

            messageMock.verify(() -> MessageCenter.post(any(UpdatePreviewElementCommand.class)));
        }
    }
}
