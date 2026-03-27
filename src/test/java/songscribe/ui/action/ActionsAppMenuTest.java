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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.ui.action.UIAction.AppMenuAction;
import songscribe.ui.component.MainFrame;

class ActionsAppMenuTest extends UnitTest {

    private MockedStatic<MainFrame> mainFrameMock;

    @BeforeEach
    void setUp() {
        mainFrameMock = mockStatic(MainFrame.class);
        var env = MockEnvHelper.setupMockEnv(mainFrameMock);

        // Actions class initialization registers keystrokes via UIUtils,
        // which needs getRootPane() on the MainFrame mock.
        var mockRootPane = mock(JRootPane.class);
        when(mockRootPane.getInputMap(anyInt())).thenReturn(new InputMap());
        when(mockRootPane.getActionMap()).thenReturn(new ActionMap());
        when(env.frame().getRootPane()).thenReturn(mockRootPane);
    }

    @AfterEach
    void tearDown() {
        mainFrameMock.close();
    }

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
