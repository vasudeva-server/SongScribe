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

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.ui.action.ModeAction;

class ModeDidChangeNotificationTest extends UnitTest {

    @Test
    void testIsAdjustmentModeReturnsTrueWhenCommandStartsWithAdjust() {
        var action = mock(ModeAction.class);
        when(action.getActionCommand()).thenReturn("adjust-note-mode");

        var notification = new ModeDidChangeNotification(action);

        assertThat(notification.isAdjustmentMode()).isTrue();
    }

    @Test
    void testIsAdjustmentModeReturnsFalseWhenCommandDoesNotStartWithAdjust() {
        var action = mock(ModeAction.class);
        when(action.getActionCommand()).thenReturn("edit-mode");

        var notification = new ModeDidChangeNotification(action);

        assertThat(notification.isAdjustmentMode()).isFalse();
    }
}
