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
import songscribe.dom.ElementType;
import songscribe.hit.HitTarget;
import songscribe.ui.component.ScoreView;
import songscribe.ui.selection.SelectionCoordinator;

class MusicSelectionDidChangeNotificationTest extends UnitTest {

    /** The verse a selected lyric is on; any verse answers the same way. */
    private static final int VERSE = 1;

    private ScoreView mockScoreView(@Nullable HitTarget selectedTarget) {
        var scoreView = mock(ScoreView.class);
        var coordinator = mock(SelectionCoordinator.class);
        when(scoreView.getSelectionCoordinator()).thenReturn(coordinator);
        when(coordinator.getSelectedTarget()).thenReturn(selectedTarget);
        return scoreView;
    }

    @Test
    void testHasLyricSelectionReturnsTrueWhenALyricIsTheSelectedTarget() {
        var lyric = new HitTarget.Lyric(ElementType.CROTCHET.newInstance(), VERSE);
        var scoreView = mockScoreView(lyric);

        var notification = new MusicSelectionDidChangeNotification(scoreView);

        assertThat(notification.hasLyricSelection()).isTrue();
        assertThat(notification.getSelectedTarget()).isEqualTo(lyric);
    }

    @Test
    void testHasLyricSelectionReturnsFalseWhenNothingIsSelected() {
        var scoreView = mockScoreView(null);

        var notification = new MusicSelectionDidChangeNotification(scoreView);

        assertThat(notification.hasLyricSelection()).isFalse();
        assertThat(notification.getSelectedTarget()).isNull();
    }

    /**
     * The target field holds every kind of selection now, so the lyric query has to name the
     * kind it means rather than answering true for whatever happens to be there.
     */
    @Test
    void testHasLyricSelectionReturnsFalseForANonLyricTarget() {
        var scoreView = mockScoreView(new HitTarget.StaffLine());

        var notification = new MusicSelectionDidChangeNotification(scoreView);

        assertThat(notification.hasLyricSelection()).isFalse();
    }
}
