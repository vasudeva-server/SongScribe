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
import org.mockito.MockedStatic;

import songscribe.MainFrameMockTest;
import songscribe.message.MessageCenter;
import songscribe.message.command.RemoveDynamicsCommand;
import songscribe.message.notification.MusicSelectionDidChangeNotification;

class RemoveDynamicsActionTest extends MainFrameMockTest {

    // Row 11: musicSelectionDidChange enables/disables based on whether the selection overlaps a hairpin

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class MusicSelectionDidChange {

        // REQUIRES_SELECTION: selectionSize must be > 0 so updateEnabledState() passes
        // and delegates the final enabled state to canRemoveDynamicsFromSelection().
        private static final int NON_EMPTY_SELECTION_SIZE = 1;

        @BeforeEach
        void setUpNonEmptySelection() {
            when(mockEnv().score().getSelectionSize()).thenReturn(NON_EMPTY_SELECTION_SIZE);
        }

        @Test
        void testDisabledWhenSelectionHasNoHairpins() {
            // canRemoveDynamicsFromSelection() = false when selection contains no hairpin span
            when(mockEnv().ctrl().canRemoveDynamicsFromSelection()).thenReturn(false);
            var action = RemoveDynamicsAction.createAction(mainFrame());

            action.musicSelectionDidChange(new MusicSelectionDidChangeNotification(mockEnv().score()));

            assertThat(action.isEnabled()).isFalse();
        }

        @Test
        void testEnabledWhenSelectionOverlapsCrescendo() {
            // canRemoveDynamicsFromSelection() = true when selection overlaps a crescendo span
            when(mockEnv().ctrl().canRemoveDynamicsFromSelection()).thenReturn(true);
            var action = RemoveDynamicsAction.createAction(mainFrame());

            action.musicSelectionDidChange(new MusicSelectionDidChangeNotification(mockEnv().score()));

            assertThat(action.isEnabled()).isTrue();
        }

        @Test
        void testEnabledWhenSelectionOverlapsDiminuendo() {
            // canRemoveDynamicsFromSelection() = true when selection overlaps a diminuendo span
            when(mockEnv().ctrl().canRemoveDynamicsFromSelection()).thenReturn(true);
            var action = RemoveDynamicsAction.createAction(mainFrame());

            action.musicSelectionDidChange(new MusicSelectionDidChangeNotification(mockEnv().score()));

            assertThat(action.isEnabled()).isTrue();
        }
    }

    // Row 12: actionPerformed posts RemoveDynamicsCommand on the message bus

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
        void testActionPerformedPostsRemoveDynamicsCommand() {
            var action = RemoveDynamicsAction.createAction(mainFrame());
            var e = new ActionEvent(new JButton(), ActionEvent.ACTION_PERFORMED, "remove-dynamics");

            action.actionPerformed(e);

            // A mutant that removes the MessageCenter.post call would miss this verification.
            messageMock.verify(() -> MessageCenter.post(org.mockito.ArgumentMatchers.any(RemoveDynamicsCommand.class)));
        }
    }
}
