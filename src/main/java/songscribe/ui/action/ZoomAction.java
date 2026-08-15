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

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.function.IntPredicate;

import net.engio.mbassy.listener.Handler;

import songscribe.Strings;
import songscribe.message.notification.ZoomDidChangeNotification;
import songscribe.ui.ZoomController;
import songscribe.ui.component.MainFrame;
import songscribe.util.UIUtils;

/**
 * Steps the score zoom to the next discrete {@link ZoomController} stop in one
 * direction. The zoom-in and zoom-out variants differ only in their icon,
 * accelerator, the {@link ZoomController} step they invoke, and the boundary at
 * which they disable, so both are produced from a single parameterized class.
 */
public final class ZoomAction extends UIAction {

    /** Icon size (in points) shared by the zoom-in and zoom-out variants. */
    public static final int ZOOM_ICON_SIZE = 18;

    private final Runnable zoomOperation;
    private final IntPredicate enabledAtPercent;

    public static ZoomAction createZoomInAction(MainFrame mainFrame) {
        return new ZoomAction(
            mainFrame,
            Strings.get(Strings.ACTION_ZOOM_IN),
            "zoom-in",
            "@\uEF18",
            KeyEvent.VK_EQUALS,
            ZoomController::zoomIn,
            percent -> percent < ZoomController.MAX_ZOOM_PERCENT
        );
    }

    public static ZoomAction createZoomOutAction(MainFrame mainFrame) {
        return new ZoomAction(
            mainFrame,
            Strings.get(Strings.ACTION_ZOOM_OUT),
            "zoom-out",
            "@\uEF16",
            KeyEvent.VK_MINUS,
            ZoomController::zoomOut,
            percent -> percent > ZoomController.MIN_ZOOM_PERCENT
        );
    }

    private ZoomAction(
        MainFrame mainFrame,
        String name,
        String actionCommand,
        String icon,
        int virtualKey,
        Runnable zoomOperation,
        IntPredicate enabledAtPercent
    ) {
        super(
            mainFrame,
            name,
            icon, ZOOM_ICON_SIZE,
            actionCommand,
            null,
            virtualKey, UIUtils.MENU_SHORTCUT_MASK,
            Flag.DISABLE_WHEN_EDITING_TEXT
        );

        this.zoomOperation = zoomOperation;
        this.enabledAtPercent = enabledAtPercent;
        setEnabled(enabledAtPercent.test(ZoomController.getZoomPercent()));
    }

    @Override
    protected void performAction(ActionEvent e) {
        zoomOperation.run();
    }

    /**
     * Folds the direction-specific zoom boundary on top of the base enabled
     * state (which honors {@link Flag#DISABLE_WHEN_EDITING_TEXT}), so the action
     * is disabled whenever text is being edited or the zoom is already at this
     * variant's limit.
     */
    @Override
    public boolean updateEnabledState() {
        var enabledState = super.updateEnabledState()
            && enabledAtPercent.test(ZoomController.getZoomPercent());
        setEnabled(enabledState);
        return enabledState;
    }

    @Handler
    public void zoomDidChange(ZoomDidChangeNotification message) {
        updateEnabledState();
    }
}
