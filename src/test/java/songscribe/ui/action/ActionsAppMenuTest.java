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

import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.ui.action.UIAction.AppMenuAction;

class ActionsAppMenuTest extends MainFrameMockTest {

    @Test
    void testGetAppMenuActionsReturnsExpectedActions() {
        var actions = Actions.getAppMenuActions();

        assertThat(actions)
            .hasSize(2)
            .satisfiesExactlyInAnyOrder(
                action -> assertThat(action).isSameAs(Actions.ABOUT_ACTION),
                action -> assertThat(action).isSameAs(Actions.PREFERENCES_ACTION)
            );
    }

    @Test
    void testGetAppMenuActionsReturnsCachedList() {
        var first = Actions.getAppMenuActions();
        var second = Actions.getAppMenuActions();

        assertThat(first).isSameAs(second);
    }

    @Test
    void testAppMenuActionsHaveCorrectNativeTitles() {
        var actions = Actions.getAppMenuActions();

        assertThat(actions)
            .extracting(AppMenuAction::getNativeMenuTitle)
            .containsExactlyInAnyOrder("About", "Settings");
    }
}
