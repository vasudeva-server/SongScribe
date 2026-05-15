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
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.playback.PlaybackController.PlaybackState;
import songscribe.ui.selection.ElementSelection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaybackControllerTest extends UnitTest {

    @AfterEach
    void tearDown() {
        PlaybackController.setState(PlaybackState.STOPPED);
        PlaybackController.setPreviousPlayingLine(-1);
        PlaybackController.setActiveSelection(null);
        PlaybackController.setRegisteredScore(null);
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SelectionDidChange {

        @Test
        void testClearsHighlightAndUpdatesSelectionWhenPausedWithSelection() {
            var mockScore = mock(ScoreView.class);
            var mockLineComponent = mock(LineComponent.class);
            when(mockScore.getLineComponent(0)).thenReturn(mockLineComponent);
            PlaybackController.register(mockScore);

            PlaybackController.setState(PlaybackState.PAUSED);
            PlaybackController.setPreviousPlayingLine(0);

            var selection = new ElementSelection(detachedLine(), 1, 3);
            PlaybackController.selectionDidChange(selection);

            verify(mockLineComponent).setPlayingIndices(-1, -1);
            assertThat(PlaybackController.getPreviousPlayingLine()).isEqualTo(-1);
            assertThat(PlaybackController.getActiveSelection()).isEqualTo(selection);
            assertThat(PlaybackController.getState()).isEqualTo(PlaybackState.PAUSED);
        }

        @Test
        void testDoesNothingWhenPlaying() {
            PlaybackController.setState(PlaybackState.PLAYING);
            var selection = new ElementSelection(detachedLine(), 0, 0);

            PlaybackController.selectionDidChange(selection);

            assertThat(PlaybackController.getActiveSelection()).isNull();
            assertThat(PlaybackController.getState()).isEqualTo(PlaybackState.PLAYING);
        }

        @Test
        void testDoesNothingWhenStopped() {
            var selection = new ElementSelection(detachedLine(), 0, 0);

            PlaybackController.selectionDidChange(selection);

            assertThat(PlaybackController.getActiveSelection()).isNull();
            assertThat(PlaybackController.getState()).isEqualTo(PlaybackState.STOPPED);
        }

        @Test
        void testStopsWhenSelectionClearedWhilePaused() {
            PlaybackController.setState(PlaybackState.PAUSED);

            PlaybackController.selectionDidChange(null);

            assertThat(PlaybackController.getState()).isEqualTo(PlaybackState.STOPPED);
            assertThat(PlaybackController.getActiveSelection()).isNull();
        }
    }
}
