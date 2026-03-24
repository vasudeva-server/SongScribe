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

package songscribe.ui.platform.mac;

import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.notification.DialogVisibilityDidChangeNotification;

import net.engio.mbassy.listener.Handler;
import org.rococoa.cocoa.foundation.NSInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jspecify.annotations.Nullable;

/**
 * Disables the native "About SongScribe" and "Settings..." items in the macOS
 * application menu while a blocking dialog is visible.
 * <p>
 * {@code Desktop.setAboutHandler} / {@code Desktop.setPreferencesHandler} bypass
 * the Swing action system, so disabling the corresponding {@code UIAction} has no
 * effect on the native menu. This controller uses Rococoa to reach into the
 * native NSMenu and toggle the items directly.
 */
public class MacNativeMenuController {

    private static final Logger LOG = LoggerFactory.getLogger(MacNativeMenuController.class);

    private final @Nullable NSMenuItem aboutItem;
    private final @Nullable NSMenuItem settingsItem;

    public MacNativeMenuController() {
        aboutItem = findAppMenuItem("About");
        settingsItem = findAppMenuItem("Settings");

        if (aboutItem == null && settingsItem == null) {
            LOG.warn("Could not locate any native app menu items — "
                    + "MacNativeMenuController will have no effect");
        }

        MessageCenter.subscribe(this);
    }

    @Handler(priority = Message.LOW_PRIORITY)
    public void dialogVisibilityDidChange(DialogVisibilityDidChangeNotification notification) {
        var enabled = !notification.isVisible();
        setNativeItemsEnabled(enabled);
    }

    private void setNativeItemsEnabled(boolean enabled) {
        if (aboutItem != null) {
            aboutItem.setEnabled(enabled);
        }

        if (settingsItem != null) {
            settingsItem.setEnabled(enabled);
        }
    }

    /**
     * Finds a menu item in the macOS application menu whose title starts with
     * the given prefix. The app menu is the first submenu of the main menu bar.
     */
    private static @Nullable NSMenuItem findAppMenuItem(String titlePrefix) {
        try {
            var app = NSApplication.sharedApplication();
            var mainMenu = app.mainMenu();
            var appMenuItem = mainMenu.itemAtIndex(new NSInteger(0));

            if (!appMenuItem.hasSubmenu()) {
                return null;
            }

            var appMenu = appMenuItem.submenu();
            appMenu.setAutoenablesItems(false);
            var itemCount = appMenu.numberOfItems().intValue();

            for (var i = 0; i < itemCount; i++) {
                var item = appMenu.itemAtIndex(new NSInteger(i));
                var title = item.title();

                if (title != null && title.startsWith(titlePrefix)) {
                    return item;
                }
            }
        }
        catch (Exception e) {
            LOG.warn("Failed to locate native '{}' menu item", titlePrefix, e);
        }

        return null;
    }
}
