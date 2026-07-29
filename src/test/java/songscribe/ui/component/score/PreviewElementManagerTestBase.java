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

package songscribe.ui.component.score;

import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.message.MessageCenter;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.ui.Mode;
import songscribe.ui.ViewScale;
import songscribe.ui.component.ScoreView;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.playback.PlaybackController;

/**
 * Shared static-mock fixture for tests that drive {@link PreviewElementManager}
 * via {@link #lc}. Each subclass inherits a fresh {@link Song} / {@link Line},
 * stubbed {@link EditModeManager}, {@link ScoreView}, {@link PlaybackController},
 * {@link StaffElement}, and {@link MessageCenter} statics, plus the cursor reset in tearDown.
 */
abstract class PreviewElementManagerTestBase extends UnitTest {

    /** Line width large enough that no insertion check ever rejects an element. */
    static final double WIDE_LINE_SS = 100.0;

    protected MockedStatic<MessageCenter> messageCenterMock;
    protected MockedStatic<EditModeManager> editModeManagerMock;
    protected MockedStatic<ScoreView> scoreMock;
    protected MockedStatic<PlaybackController> playbackMock;
    protected MockedStatic<StaffElement> staffElementMock;

    protected LineComponent lc;
    protected Song song;
    protected Line line;

    @BeforeEach
    void baseSetUp() {
        // Construct model objects before mocking MessageCenter so constructor
        // subscriptions reach the real bus.
        song = new Song();
        line = song.getLine(0);

        messageCenterMock = mockStatic(MessageCenter.class);
        editModeManagerMock = mockStatic(EditModeManager.class);
        scoreMock = mockStatic(ScoreView.class);
        playbackMock = mockStatic(PlaybackController.class);
        staffElementMock = mockStatic(StaffElement.class, CALLS_REAL_METHODS);

        lc = mock(LineComponent.class);
        var score = mock(ScoreView.class);

        when(lc.getScoreView()).thenReturn(score);
        when(lc.getViewScale()).thenReturn(ViewScale.IDENTITY);
        when(lc.getLine()).thenReturn(line);
        when(score.getMode()).thenReturn(Mode.EDIT);

        editModeManagerMock.when(EditModeManager::hasPreviewElement).thenReturn(true);
        editModeManagerMock
            .when(() -> EditModeManager.elementWasModified(any(Line.class), anyInt()))
            .thenReturn(false);

        playbackMock.when(PlaybackController::isPlaying).thenReturn(false);

        staffElementMock.when(() -> StaffElement.defaultDirection(any())).thenReturn(StaffElement.Direction.UP);

        PreviewElementManager.setCurrentPreviewLine(lc);
        PreviewElementManager.setCurrentStaffPosition(0);
    }

    /** Stubs {@link EditModeManager#getPreviewElement()} to return {@code element}. */
    void setPreviewElement(StaffElement element) {
        editModeManagerMock.when(EditModeManager::getPreviewElement).thenReturn(element);
    }

    @AfterEach
    void baseTearDown() {
        PreviewElementManager.setCurrentPreviewLine(null);
        PreviewElementManager.setCurrentXIndex(-1);
        PreviewElementManager.setXPosSsMatchesElement(false);
        PreviewElementManager.setCurrentSlideZone(null);
        PreviewElementManager.clearPendingTempoPrompt();

        staffElementMock.close();
        playbackMock.close();
        scoreMock.close();
        editModeManagerMock.close();
        messageCenterMock.close();
    }
}
