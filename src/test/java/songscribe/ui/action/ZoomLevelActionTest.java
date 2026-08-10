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

import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.ui.ZoomController;
import songscribe.util.UIUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

class ZoomLevelActionTest extends MainFrameMockTest {

    private record ExpectedShortcut(int zoomPercent, int virtualKey) {}

    private static final List<ExpectedShortcut> EXPECTED_SHORTCUTS = List.of(
        new ExpectedShortcut(100, KeyEvent.VK_1),
        new ExpectedShortcut(200, KeyEvent.VK_2),
        new ExpectedShortcut(300, KeyEvent.VK_3),
        new ExpectedShortcut(400, KeyEvent.VK_4),
        new ExpectedShortcut(800, KeyEvent.VK_8)
    );

    @Test
    void testActionsTargetTheExpectedPercents() {
        assertThat(Actions.ZOOM_LEVEL_ACTIONS)
            .extracting(ZoomLevelAction::getZoomPercent)
            .containsExactlyElementsOf(EXPECTED_SHORTCUTS.stream().map(ExpectedShortcut::zoomPercent).toList());
    }

    @Test
    void testEachTargetPercentIsADiscreteZoomStop() {
        // The status-bar percent menu can only advertise a shortcut on a stop it lists.
        assertThat(Actions.ZOOM_LEVEL_ACTIONS)
            .allSatisfy(action ->
                assertThat(ZoomController.ZOOM_LEVEL_PERCENTS).contains(action.getZoomPercent()));
    }

    @Test
    void testAcceleratorsAreTheMenuShortcutPlusTheMatchingDigit() {
        var expectedAccelerators = EXPECTED_SHORTCUTS.stream()
            .map(shortcut -> KeyStroke.getKeyStroke(shortcut.virtualKey(), UIUtils.MENU_SHORTCUT_MASK))
            .toList();

        assertThat(Actions.ZOOM_LEVEL_ACTIONS)
            .extracting(UIAction::getAccelerator)
            .containsExactlyElementsOf(expectedAccelerators);
    }

    @Test
    void testPerformingAnActionSetsItsPercent() {
        var action = Actions.ZOOM_LEVEL_ACTIONS.getLast();

        try (var zoomControllerMock = mockStatic(ZoomController.class)) {
            action.actionPerformed(
                new ActionEvent(action, ActionEvent.ACTION_PERFORMED, "zoom-level"));

            zoomControllerMock.verify(() -> ZoomController.setZoomPercent(action.getZoomPercent()));
        }
    }
}
