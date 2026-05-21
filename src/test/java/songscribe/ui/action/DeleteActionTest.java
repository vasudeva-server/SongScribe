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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.dom.ElementType;
import songscribe.ui.component.MainFrame;
import songscribe.ui.playback.PlaybackController;
import songscribe.ui.selection.SelectionCoordinator;

class DeleteActionTest extends UnitTest {

    private MockedStatic<MainFrame> mainFrameMock;
    private MockedStatic<PlaybackController> playbackControllerMock;
    private MockEnvHelper.MockEnv env;

    @BeforeEach
    void setUp() {
        mainFrameMock = mockStatic(MainFrame.class);
        env = MockEnvHelper.setupMockEnv(mainFrameMock);
        // Must follow MainFrame setup: PlaybackController's static initializer creates
        // playback actions that call MainFrame.getInstance().getRootPane().
        playbackControllerMock = mockStatic(PlaybackController.class);
        playbackControllerMock.when(PlaybackController::isPlaying).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        playbackControllerMock.close();
        mainFrameMock.close();
    }

    @Test
    void testDeleteEnabledForLyricSelection() {
        var element = ElementType.CROTCHET.newInstance();
        when(env.coordinator().hasLyricSelection()).thenReturn(true);
        when(env.coordinator().getLyricSelection()).thenReturn(
            new SelectionCoordinator.LyricSelection(element, 1));

        var action = DeleteAction.createAction(MainFrame.getInstance());
        action.setEnabled(false);

        action.musicSelectionDidChange(new MusicSelectionDidChangeNotification(env.score()));

        assertThat(action.isEnabled()).isTrue();
    }
}
