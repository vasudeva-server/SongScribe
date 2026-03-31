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

package songscribe.ui.playback;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.music.Line;
import songscribe.ui.component.Score;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.playback.PlaybackController.PlaybackState;
import songscribe.ui.selection.ElementSelection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaybackControllerTest extends UnitTest {

    @AfterEach
    void tearDown() {
        PlaybackController.state = PlaybackState.STOPPED;
        PlaybackController.previousPlayingLine = -1;
        PlaybackController.activeSelection = null;
        PlaybackController.registeredScore = null;
    }

    @Nested
    class SelectionDidChange {

        @Test
        void testClearsHighlightAndUpdatesSelectionWhenPausedWithSelection() {
            var mockScore = mock(Score.class);
            var mockLineComponent = mock(LineComponent.class);
            when(mockScore.getLineComponent(0)).thenReturn(mockLineComponent);
            PlaybackController.register(mockScore);

            PlaybackController.state = PlaybackState.PAUSED;
            PlaybackController.previousPlayingLine = 0;

            var selection = new ElementSelection(new Line(), 1, 3);
            PlaybackController.selectionDidChange(selection);

            verify(mockLineComponent).setPlayingIndices(-1, -1);
            assertThat(PlaybackController.previousPlayingLine).isEqualTo(-1);
            assertThat(PlaybackController.activeSelection).isEqualTo(selection);
            assertThat(PlaybackController.state).isEqualTo(PlaybackState.PAUSED);
        }

        @Test
        void testDoesNothingWhenPlaying() {
            PlaybackController.state = PlaybackState.PLAYING;
            var selection = new ElementSelection(new Line(), 0, 0);

            PlaybackController.selectionDidChange(selection);

            assertThat(PlaybackController.activeSelection).isNull();
            assertThat(PlaybackController.state).isEqualTo(PlaybackState.PLAYING);
        }

        @Test
        void testDoesNothingWhenStopped() {
            var selection = new ElementSelection(new Line(), 0, 0);

            PlaybackController.selectionDidChange(selection);

            assertThat(PlaybackController.activeSelection).isNull();
            assertThat(PlaybackController.state).isEqualTo(PlaybackState.STOPPED);
        }

        @Test
        void testStopsWhenSelectionClearedWhilePaused() {
            PlaybackController.state = PlaybackState.PAUSED;

            PlaybackController.selectionDidChange(null);

            assertThat(PlaybackController.state).isEqualTo(PlaybackState.STOPPED);
            assertThat(PlaybackController.activeSelection).isNull();
        }
    }
}
