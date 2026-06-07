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
package songscribe.message.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.ui.component.ScoreView;
import songscribe.ui.selection.SelectionCoordinator;

class MusicSelectionDidChangeNotificationTest extends UnitTest {

    private ScoreView mockScoreView(@Nullable SelectionCoordinator.LyricSelection lyricSelection) {
        var scoreView = mock(ScoreView.class);
        var coordinator = mock(SelectionCoordinator.class);
        when(scoreView.getSelectionCoordinator()).thenReturn(coordinator);
        when(coordinator.getLyricSelection()).thenReturn(lyricSelection);
        return scoreView;
    }

    @Test
    void testHasLyricSelectionReturnsTrueWhenLyricSelectionIsPresent() {
        var lyricSelection = mock(SelectionCoordinator.LyricSelection.class);
        var scoreView = mockScoreView(lyricSelection);

        var notification = new MusicSelectionDidChangeNotification(scoreView);

        assertThat(notification.hasLyricSelection()).isTrue();
    }

    @Test
    void testHasLyricSelectionReturnsFalseWhenLyricSelectionIsAbsent() {
        var scoreView = mockScoreView(null);

        var notification = new MusicSelectionDidChangeNotification(scoreView);

        assertThat(notification.hasLyricSelection()).isFalse();
    }
}
