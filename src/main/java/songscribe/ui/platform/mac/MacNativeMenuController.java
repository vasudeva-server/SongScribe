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

import java.util.ArrayList;
import java.util.List;

import net.engio.mbassy.listener.Handler;
import org.rococoa.cocoa.foundation.NSInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.message.MessageCenter;
import songscribe.message.notification.DialogVisibilityDidChangeNotification;
import songscribe.ui.action.Actions;
import songscribe.ui.action.UIAction.AppMenuAction;

/**
 * Disables native macOS application menu items while a blocking dialog is
 * visible.
 * <p>
 * {@code Desktop.setAboutHandler} / {@code Desktop.setPreferencesHandler} bypass
 * the Swing action system, so disabling the corresponding {@code UIAction} has no
 * effect on the native menu. This controller uses Rococoa to reach into the
 * native NSMenu and toggle the items directly.
 * <p>
 * Actions that implement {@link AppMenuAction} are discovered automatically
 * via {@link Actions#getAppMenuActions()}. Adding a new app menu action requires
 * no changes to this class.
 */
public class MacNativeMenuController {

    private static final Logger LOG = LoggerFactory.getLogger(MacNativeMenuController.class);

    private final List<NSMenuItem> managedItems;

    public MacNativeMenuController() {
        managedItems = discoverNativeItems();
        MessageCenter.subscribe(this);
    }

    /**
     * Package-private constructor for testing: accepts a pre-built managed-item
     * list and subscribes to the message bus, bypassing the native discovery chain.
     */
    MacNativeMenuController(List<NSMenuItem> managedItems) {
        this.managedItems = managedItems;
        MessageCenter.subscribe(this);
    }

    @Handler()
    public void dialogVisibilityDidChange(DialogVisibilityDidChangeNotification notification) {
        var enabled = !notification.isVisible();

        for (var item : managedItems) {
            item.setEnabled(enabled);
        }
    }

    private static List<NSMenuItem> discoverNativeItems() {
        var appMenuActions = Actions.getAppMenuActions();
        var items = new ArrayList<NSMenuItem>();

        try {
            var app = NSApplication.sharedApplication();
            var mainMenu = app.mainMenu();
            var appMenuItem = mainMenu.itemAtIndex(new NSInteger(0));

            if (!appMenuItem.hasSubmenu()) {
                LOG.warn("macOS app menu has no submenu — no native items will be managed");
                return items;
            }

            var appMenu = appMenuItem.submenu();
            appMenu.setAutoenablesItems(false);
            var itemCount = appMenu.numberOfItems().intValue();

            for (var action : appMenuActions) {
                var matched = false;

                for (var i = 0; i < itemCount; i++) {
                    var item = appMenu.itemAtIndex(new NSInteger(i));
                    var title = item.title();

                    //noinspection ConstantValue -- need for NullAway
                    if (title != null && title.startsWith(action.getNativeMenuTitle())) {
                        items.add(item);
                        matched = true;
                        break;
                    }
                }

                if (!matched) {
                    LOG.warn(
                        "No native menu item matched prefix '{}' — it will not be managed",
                        action.getNativeMenuTitle()
                    );
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to discover native app menu items", e);
        }

        return items;
    }
}
