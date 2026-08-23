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

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

import net.engio.mbassy.listener.Handler;
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
 * effect on the native menu. This controller reaches into the native NSMenu
 * through the {@link ObjC} FFM bridge and toggles the items directly.
 * <p>
 * Actions that implement {@link AppMenuAction} are discovered automatically
 * via {@link Actions#getAppMenuActions()}. Adding a new app menu action requires
 * no changes to this class.
 * <p>
 * Each managed {@code NSMenuItem} is {@code -retain}ed so its pointer stays valid
 * across runloop ticks. The controller is an application-lifetime singleton with
 * no teardown, so the retains are reclaimed by the OS at process exit rather than
 * via explicit {@code -release}.
 */
public class MacNativeMenuController {

    private static final Logger LOG = LoggerFactory.getLogger(MacNativeMenuController.class);

    // The application menu is always the first item of the main menu bar.
    private static final long APP_MENU_INDEX = 0;

    private final List<MemorySegment> managedItems;

    public MacNativeMenuController() {
        managedItems = discoverNativeItems();
        MessageCenter.subscribe(this);
    }

    @Handler()
    public void dialogVisibilityDidChange(DialogVisibilityDidChangeNotification notification) {
        var enabled = !notification.isVisible();
        var setEnabled = ObjC.selector("setEnabled:");

        for (var item : managedItems) {
            ObjC.msgSendVoid(item, setEnabled, enabled);
        }
    }

    private static List<MemorySegment> discoverNativeItems() {
        try {
            return ObjC.withAutoreleasePool(MacNativeMenuController::collectManagedItems);
        } catch (Throwable e) {
            LOG.warn("Failed to discover native app menu items", e);
            return List.of();
        }
    }

    private static List<MemorySegment> collectManagedItems() {
        var appMenuActions = Actions.getAppMenuActions();
        var items = new ArrayList<MemorySegment>();

        var app = ObjC.msgSend(ObjC.objcClass("NSApplication"), ObjC.selector("sharedApplication"));
        var mainMenu = ObjC.msgSend(app, ObjC.selector("mainMenu"));
        var appMenuItem = ObjC.msgSend(mainMenu, ObjC.selector("itemAtIndex:"), APP_MENU_INDEX);

        if (!ObjC.msgSendBoolean(appMenuItem, ObjC.selector("hasSubmenu"))) {
            LOG.warn("macOS app menu has no submenu — no native items will be managed");
            return items;
        }

        var appMenu = ObjC.msgSend(appMenuItem, ObjC.selector("submenu"));
        ObjC.msgSendVoid(appMenu, ObjC.selector("setAutoenablesItems:"), false);
        var itemCount = ObjC.msgSendLong(appMenu, ObjC.selector("numberOfItems"));

        var itemAtIndex = ObjC.selector("itemAtIndex:");
        var titleSelector = ObjC.selector("title");

        for (var action : appMenuActions) {
            var matched = false;

            for (var i = 0L; i < itemCount; i++) {
                var item = ObjC.msgSend(appMenu, itemAtIndex, i);
                var title = ObjC.nsStringToJava(ObjC.msgSend(item, titleSelector));

                if (title != null && title.startsWith(action.getNativeMenuTitle())) {
                    // Retain so the pointer outlives this autorelease pool and stays
                    // valid across runloop ticks; -retain returns the same id.
                    items.add(ObjC.msgSend(item, ObjC.selector("retain")));
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

        return items;
    }
}
