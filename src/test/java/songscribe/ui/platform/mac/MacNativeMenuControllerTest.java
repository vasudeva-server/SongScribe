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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.rococoa.cocoa.foundation.NSInteger;

import songscribe.UnitTest;
import songscribe.message.MessageCenter;
import songscribe.message.notification.DialogVisibilityDidChangeNotification;
import songscribe.ui.action.Actions;
import songscribe.ui.action.UIAction.AppMenuAction;

class MacNativeMenuControllerTest extends UnitTest {

    // --- Row 1: constructor subscribes to MessageCenter ---

    @Test
    void testConstructorSubscribesToMessageCenter() {
        try (var messageCenterMock = mockStatic(MessageCenter.class)) {
            new MacNativeMenuController(List.of());
            messageCenterMock.verify(() -> MessageCenter.subscribe(any(MacNativeMenuController.class)));
        }
    }

    // --- Rows 2 & 3: dialogVisibilityDidChange ---

    @Nested
    class WhenDialogVisibilityChanges {

        private MockedStatic<MessageCenter> messageCenterMock;

        @BeforeEach
        void setUp() {
            messageCenterMock = mockStatic(MessageCenter.class);
        }

        @AfterEach
        void tearDown() {
            messageCenterMock.close();
        }

        @Test
        void testDialogVisibleDisablesAllManagedItems() {
            var item = mock(NSMenuItem.class);
            var controller = new MacNativeMenuController(List.of(item));

            controller.dialogVisibilityDidChange(new DialogVisibilityDidChangeNotification(true));

            verify(item).setEnabled(false);
        }

        @Test
        void testDialogHiddenEnablesAllManagedItems() {
            var item = mock(NSMenuItem.class);
            var controller = new MacNativeMenuController(List.of(item));

            controller.dialogVisibilityDidChange(new DialogVisibilityDidChangeNotification(false));

            verify(item).setEnabled(true);
        }

        @Test
        void testAllManagedItemsReceiveSetEnabled() {
            var itemA = mock(NSMenuItem.class);
            var itemB = mock(NSMenuItem.class);
            var controller = new MacNativeMenuController(List.of(itemA, itemB));

            controller.dialogVisibilityDidChange(new DialogVisibilityDidChangeNotification(true));

            verify(itemA).setEnabled(false);
            verify(itemB).setEnabled(false);
        }
    }

    // --- Rows 4–7: discoverNativeItems branches, exercised via the public constructor ---

    @Nested
    class DiscoverNativeItems {

        private MockedStatic<NSApplication> nsAppMock;
        private MockedStatic<Actions> actionsMock;
        private MockedStatic<MessageCenter> messageCenterMock;

        private NSApplication mockApp;
        private NSMenu mockMainMenu;
        private NSMenuItem mockAppMenuItem;
        private NSMenu mockAppMenu;

        @BeforeEach
        void setUp() {
            nsAppMock = mockStatic(NSApplication.class);
            actionsMock = mockStatic(Actions.class);
            messageCenterMock = mockStatic(MessageCenter.class);

            mockApp = mock(NSApplication.class);
            mockMainMenu = mock(NSMenu.class);
            mockAppMenuItem = mock(NSMenuItem.class);
            mockAppMenu = mock(NSMenu.class);

            nsAppMock.when(NSApplication::sharedApplication).thenReturn(mockApp);
            when(mockApp.mainMenu()).thenReturn(mockMainMenu);
            when(mockMainMenu.itemAtIndex(any(NSInteger.class))).thenReturn(mockAppMenuItem);
            when(mockAppMenuItem.hasSubmenu()).thenReturn(true);
            when(mockAppMenuItem.submenu()).thenReturn(mockAppMenu);
            when(mockAppMenu.numberOfItems()).thenReturn(new NSInteger(0));
        }

        @AfterEach
        void tearDown() {
            messageCenterMock.close();
            actionsMock.close();
            nsAppMock.close();
        }

        // Row 4: no-submenu returns empty list
        @Test
        void testNoSubmenuReturnsEmptyManagedItems() {
            when(mockAppMenuItem.hasSubmenu()).thenReturn(false);
            actionsMock.when(Actions::getAppMenuActions).thenReturn(List.of());

            var controller = new MacNativeMenuController();

            // setAutoenablesItems is never reached because discoverNativeItems returns
            // early when there is no submenu. A subsequent notification call has no
            // items to iterate, so no setEnabled is invoked on the app menu.
            controller.dialogVisibilityDidChange(new DialogVisibilityDidChangeNotification(true));
            verify(mockAppMenu, never()).setAutoenablesItems(false);
            verify(mockAppMenu, never()).itemAtIndex(any(NSInteger.class));
        }

        // Row 5: prefix matching — matched item is in result, unmatched does not appear
        @Test
        void testMatchingItemByPrefixIsManaged() {
            AppMenuAction matchingAction = () -> "About";
            AppMenuAction unmatchedAction = () -> "Settings";
            actionsMock.when(Actions::getAppMenuActions).thenReturn(List.of(matchingAction, unmatchedAction));

            var aboutItem = mock(NSMenuItem.class);
            when(aboutItem.title()).thenReturn("About SongScribe");

            when(mockAppMenu.numberOfItems()).thenReturn(new NSInteger(1));
            when(mockAppMenu.itemAtIndex(any(NSInteger.class))).thenReturn(aboutItem);

            var controller = new MacNativeMenuController();

            // Post visible=true: the managed "About" item must be disabled
            controller.dialogVisibilityDidChange(new DialogVisibilityDidChangeNotification(true));
            verify(aboutItem).setEnabled(false);
        }

        // Row 6: exception in NSApplication.sharedApplication() returns empty list (no propagation)
        @Test
        void testExceptionInNativeCallDoesNotPropagate() {
            nsAppMock.when(NSApplication::sharedApplication).thenThrow(new RuntimeException("no native runtime"));
            actionsMock.when(Actions::getAppMenuActions).thenReturn(List.of());

            // Construction must not throw — the broad catch swallows the exception.
            // Discovery aborts early, so the managed list is empty and a subsequent
            // notification call must not invoke setEnabled on anything.
            var controller = new MacNativeMenuController();
            controller.dialogVisibilityDidChange(new DialogVisibilityDidChangeNotification(true));
            verify(mockAppMenu, never()).itemAtIndex(any(NSInteger.class));
        }

        // Row 7: setAutoenablesItems(false) is called on the app menu before iterating
        @Test
        void testSetAutoenablesItemsCalledBeforeIteration() {
            actionsMock.when(Actions::getAppMenuActions).thenReturn(List.of());

            new MacNativeMenuController();

            verify(mockAppMenu).setAutoenablesItems(false);
        }
    }
}
