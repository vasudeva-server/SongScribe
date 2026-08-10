/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package songscribe.ui.action;

import module java.desktop;

import java.util.List;

import songscribe.Strings;
import songscribe.ui.ZoomController;
import songscribe.ui.component.MainFrame;
import songscribe.util.UIUtils;

/**
 * Jumps the score zoom straight to one discrete {@link ZoomController} stop,
 * bypassing the stepping that {@link ZoomAction} does. The variants differ only
 * in their target percent and accelerator digit, so all of them are produced
 * from a single parameterized class.
 */
public final class ZoomLevelAction extends UIAction {

    /**
     * The zoom stops that get their own accelerator, paired with the digit key
     * that selects them. Every percent listed here must also appear in
     * {@link ZoomController#ZOOM_LEVEL_PERCENTS}, so the status-bar percent menu
     * has an item to hang the accelerator on.
     */
    private static final List<ZoomLevelShortcut> ZOOM_LEVEL_SHORTCUTS = List.of(
        new ZoomLevelShortcut(100, KeyEvent.VK_1),
        new ZoomLevelShortcut(200, KeyEvent.VK_2),
        new ZoomLevelShortcut(300, KeyEvent.VK_3),
        new ZoomLevelShortcut(400, KeyEvent.VK_4),
        new ZoomLevelShortcut(800, KeyEvent.VK_8)
    );

    private final int zoomPercent;

    /** Creates one action per shortcut-bearing zoom stop, in ascending percent order. */
    public static List<ZoomLevelAction> createActions(MainFrame mainFrame) {
        return ZOOM_LEVEL_SHORTCUTS.stream()
            .map(shortcut -> new ZoomLevelAction(mainFrame, shortcut))
            .toList();
    }

    private ZoomLevelAction(MainFrame mainFrame, ZoomLevelShortcut shortcut) {
        super(
            mainFrame,
            Strings.get(Strings.ACTION_ZOOM_LEVEL, shortcut.zoomPercent()),
            "zoom-level-" + shortcut.zoomPercent(),
            shortcut.virtualKey(),
            UIUtils.MENU_SHORTCUT_MASK,
            Flag.DISABLE_WHEN_EDITING_TEXT
        );

        zoomPercent = shortcut.zoomPercent();
    }

    /** The zoom stop this action selects, as an integer percent. */
    public int getZoomPercent() {
        return zoomPercent;
    }

    @Override
    protected void performAction(ActionEvent e) {
        ZoomController.setZoomPercent(zoomPercent);
    }

    private record ZoomLevelShortcut(int zoomPercent, int virtualKey) {}
}
