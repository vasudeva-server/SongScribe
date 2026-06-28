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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import songscribe.MainFrameMockTest;
import songscribe.message.MessageCenter;
import songscribe.message.command.PasteboardOpCommand;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.dom.ElementType;
import songscribe.ui.playback.PlaybackController;
import songscribe.ui.selection.SelectionCoordinator;

class DeleteActionTest extends MainFrameMockTest {

    private MockedStatic<PlaybackController> playbackControllerMock;

    @BeforeEach
    void setUp() {
        // Must follow MainFrame setup: PlaybackController's static initializer creates
        // playback actions that call MainFrame.getInstance().getRootPane().
        playbackControllerMock = mockStatic(PlaybackController.class);
        playbackControllerMock.when(PlaybackController::isPlaying).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        playbackControllerMock.close();
    }

    // Row 3: updateEnabledState — all four enablement branches + all-false case

    @Test
    void testDeleteEnabledForLyricSelection() {
        var element = ElementType.CROTCHET.newInstance();
        when(mockEnv().coordinator().hasLyricSelection()).thenReturn(true);
        when(mockEnv().coordinator().getLyricSelection()).thenReturn(
            new SelectionCoordinator.LyricSelection(element, 1));

        var action = DeleteAction.createAction(mainFrame());
        action.setEnabled(false);

        action.musicSelectionDidChange(new MusicSelectionDidChangeNotification(mockEnv().score()));

        assertThat(action.isEnabled()).isTrue();
    }

    @Test
    void testDeleteEnabledForActiveSelection() {
        when(mockEnv().coordinator().hasActiveSelection()).thenReturn(true);

        var action = DeleteAction.createAction(mainFrame());
        action.setEnabled(false);

        action.musicSelectionDidChange(new MusicSelectionDidChangeNotification(mockEnv().score()));

        assertThat(action.isEnabled()).isTrue();
    }

    @Test
    void testDeleteEnabledForGlissandoSelection() {
        when(mockEnv().coordinator().hasSlideSelection()).thenReturn(true);

        var action = DeleteAction.createAction(mainFrame());
        action.setEnabled(false);

        action.musicSelectionDidChange(new MusicSelectionDidChangeNotification(mockEnv().score()));

        assertThat(action.isEnabled()).isTrue();
    }

    @Test
    void testDeleteEnabledWhenCanDeleteLine() {
        when(mockEnv().score().canDeleteLine()).thenReturn(true);

        var action = DeleteAction.createAction(mainFrame());
        action.setEnabled(false);

        action.musicSelectionDidChange(new MusicSelectionDidChangeNotification(mockEnv().score()));

        assertThat(action.isEnabled()).isTrue();
    }

    @Test
    void testDeleteDisabledWhenNothingToDelete() {
        // All four predicates return false (default mock state)
        var action = DeleteAction.createAction(mainFrame());
        action.setEnabled(true);

        action.musicSelectionDidChange(new MusicSelectionDidChangeNotification(mockEnv().score()));

        assertThat(action.isEnabled()).isFalse();
    }

    // Row 4: actionPerformed dispatches PasteboardOpCommand(DELETE)

    @Test
    void testActionPerformedDispatchesDeleteCommand() {
        try (var messageCenterMock = mockStatic(MessageCenter.class)) {
            var action = DeleteAction.createAction(mainFrame());
            action.actionPerformed(
                new ActionEvent(action, ActionEvent.ACTION_PERFORMED, "edit-delete"));

            var captor = ArgumentCaptor.forClass(PasteboardOpCommand.class);
            messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
            assertThat(captor.getValue().getOperation())
                .isEqualTo(PasteboardAction.Operation.DELETE);
        }
    }
}
