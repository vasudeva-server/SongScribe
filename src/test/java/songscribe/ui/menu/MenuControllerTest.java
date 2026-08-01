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

package songscribe.ui.menu;

import module java.desktop;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.formdev.flatlaf.util.SystemInfo;

import songscribe.UnitTest;
import songscribe.message.notification.RecentDocumentsDidChangeNotification;
import songscribe.prefs.RecentDocumentsManager;
import songscribe.ui.action.Actions;
import songscribe.ui.action.MockEnvHelper;
import songscribe.ui.component.MainFrame;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class MenuControllerTest extends UnitTest {

    // -------------------------------------------------------------------------
    // buildLabels — unique filenames
    // -------------------------------------------------------------------------

    @Test
    void testBuildLabelsUniqueFilenames() {
        var paths = List.of(
            Path.of("/tmp/docs/alpha.mssw"),
            Path.of("/tmp/docs/beta.mssw"),
            Path.of("/tmp/docs/gamma.mssw")
        );

        var labels = MenuController.buildLabels(paths);

        assertThat(labels).containsExactly("alpha.mssw", "beta.mssw", "gamma.mssw");
    }

    // -------------------------------------------------------------------------
    // buildLabels — duplicate filenames (depth-1 parent disambiguates)
    // -------------------------------------------------------------------------

    @Test
    void testBuildLabelsDuplicateFilenames() {
        // Two paths share the same filename but live in different parent dirs.
        // At depth=1, the parent directory names differ, so that suffix
        // is used as the disambiguator.
        var paths = List.of(
            Path.of("/tmp/project-a/song.mssw"),
            Path.of("/tmp/project-b/song.mssw")
        );

        var labels = MenuController.buildLabels(paths);

        assertThat(labels).containsExactly(
            "song.mssw — project-a",
            "song.mssw — project-b"
        );
    }

    // -------------------------------------------------------------------------
    // buildLabels — two-level disambiguation
    // -------------------------------------------------------------------------

    @Test
    void testBuildLabelsTwoLevelDisambiguation() {
        // Both paths share filename and the same depth-1 parent name ("work"),
        // so depth-1 is not unique. Depth-2 produces unique suffixes "client-a/work"
        // vs "client-b/work".
        var paths = List.of(
            Path.of("/tmp/client-a/work/song.mssw"),
            Path.of("/tmp/client-b/work/song.mssw")
        );

        var labels = MenuController.buildLabels(paths);

        assertThat(labels).containsExactly(
            "song.mssw — client-a/work",
            "song.mssw — client-b/work"
        );
    }

    // -------------------------------------------------------------------------
    // buildLabels — fallback to full path when no depth resolves uniqueness
    // -------------------------------------------------------------------------

    @Test
    void testBuildLabelsFallbackToFullPath() {
        // Both paths are identical (same filename, same parent at every depth).
        // No depth level can distinguish them, so the fallback uses the full
        // path string. Since the paths are under the user's home directory,
        // the full path gets ~ substitution.
        var home = System.getProperty("user.home");
        var path = Path.of(home, "shared-folder", "song.mssw");
        var paths = List.of(path, path);

        var labels = MenuController.buildLabels(paths);

        // Both entries fall back to the tilde-substituted full path.
        assertThat(labels).containsExactly(
            "~/shared-folder/song.mssw",
            "~/shared-folder/song.mssw"
        );
    }

    // -------------------------------------------------------------------------
    // tildeSubstitute — path under home directory
    // -------------------------------------------------------------------------

    @Test
    void testTildeSubstituteUnderHome() {
        var home = Path.of("/home/testuser");
        var result = MenuController.tildeSubstitute("/home/testuser/docs/song.mssw", home);
        assertThat(result).isEqualTo("~/docs/song.mssw");
    }

    // -------------------------------------------------------------------------
    // tildeSubstitute — path outside home directory
    // -------------------------------------------------------------------------

    @Test
    void testTildeSubstituteOutsideHome() {
        var home = Path.of("/home/testuser");
        var result = MenuController.tildeSubstitute("/tmp/other/song.mssw", home);
        assertThat(result).isEqualTo("/tmp/other/song.mssw");
    }

    // -------------------------------------------------------------------------
    // tildeSubstitute — path exactly equal to home directory
    // -------------------------------------------------------------------------

    @Test
    void testTildeSubstituteExactlyHome() {
        var home = Path.of("/home/testuser");
        var result = MenuController.tildeSubstitute("/home/testuser", home);
        assertThat(result).isEqualTo("~");
    }

    // =========================================================================
    // Tests that require a constructed MenuController
    // =========================================================================

    /**
     * Shared fixture for tests that need a live {@link MenuController} instance.
     *
     * <p>Sets up a mocked {@link MainFrame} environment and a mocked
     * {@link RecentDocumentsManager} (returning an empty list by default, so
     * construction succeeds cleanly). Each test can re-stub
     * {@code RecentDocumentsManager.getRecents()} to a different return value
     * before exercising the code under test.
     */
    @Nested
    class WithController {

        private MockedStatic<RecentDocumentsManager> recentManagerMock;
        private MainFrame mockFrame;
        private MenuController controller;

        @BeforeEach
        void setUp() {
            // MenuController uses the injected frame directly, not MainFrame.getInstance(),
            // so no static mock of MainFrame is needed.
            var env = MockEnvHelper.setupMockEnv();
            mockFrame = env.frame();

            // Initialize Actions with the mock frame so constants are non-null when
            // MenuController's initFileMenu() accesses Actions.PRINT_ACTION etc.
            Actions.initialize(mockFrame);

            // Default: empty recents so construction initialises cleanly.
            recentManagerMock = mockStatic(RecentDocumentsManager.class);
            recentManagerMock.when(RecentDocumentsManager::getRecents).thenReturn(List.of());

            controller = new MenuController(mockFrame);
        }

        @AfterEach
        void tearDown() {
            recentManagerMock.close();
            Actions.resetForTest();
        }

        // -------------------------------------------------------------------------
        // rebuildOpenRecentMenu — empty recents list (row 12)
        // -------------------------------------------------------------------------

        @Test
        void testRebuildOpenRecentMenuEmptyShowsSingleDisabledItem() {
            // Recents already empty from setUp; rebuild to confirm current state.
            recentManagerMock.when(RecentDocumentsManager::getRecents).thenReturn(List.of());

            controller.rebuildOpenRecentMenu();

            var components = controller.openRecentMenu.getMenuComponents();
            assertThat(components).hasSize(1);
            assertThat(components[0]).isInstanceOf(JMenuItem.class);
            assertThat(components[0].isEnabled()).isFalse();
        }

        // -------------------------------------------------------------------------
        // rebuildOpenRecentMenu — non-empty recents list (row 13)
        // -------------------------------------------------------------------------

        @Test
        void testRebuildOpenRecentMenuNonEmptyShowsOneItemPerPathPlusSeparatorPlusClear() {
            var path1 = Path.of("/tmp/docs/alpha.mssw");
            var path2 = Path.of("/tmp/docs/beta.mssw");
            recentManagerMock.when(RecentDocumentsManager::getRecents).thenReturn(
                List.of(path1, path2)
            );

            controller.rebuildOpenRecentMenu();

            var components = controller.openRecentMenu.getMenuComponents();
            // 2 path items + 1 separator + 1 Clear action = 4 components
            final var expectedCount = 4;
            assertThat(components).hasSize(expectedCount);
            // First two components are action items (one per path)
            assertThat(components[0]).isInstanceOf(JMenuItem.class);
            assertThat(components[1]).isInstanceOf(JMenuItem.class);
            // Third component is a separator (JSeparator)
            assertThat(components[2]).isInstanceOf(JSeparator.class);
            // Fourth component is the Clear Recents action item
            assertThat(components[3]).isInstanceOf(JMenuItem.class);
        }

        // -------------------------------------------------------------------------
        // recentDocumentsDidChange — handler rebuilds the open-recent menu (row 14)
        // -------------------------------------------------------------------------

        @Test
        void testRecentDocumentsDidChangeRebuildsMenu() {
            // Initially the menu has one disabled "No recent documents" item.
            assertThat(controller.openRecentMenu.getMenuComponentCount()).isEqualTo(1);

            // Now stub a non-empty list and fire the notification handler directly.
            var path = Path.of("/tmp/music/song.mssw");
            recentManagerMock.when(RecentDocumentsManager::getRecents).thenReturn(List.of(path));

            controller.recentDocumentsDidChange(new RecentDocumentsDidChangeNotification());

            // The handler must have called rebuildOpenRecentMenu(): 1 item + separator + clear = 3.
            final var expectedCount = 3;
            assertThat(controller.openRecentMenu.getMenuComponentCount()).isEqualTo(expectedCount);
        }

        // -------------------------------------------------------------------------
        // initFileMenu — Quit action present iff non-macOS (rows 15 & 16)
        // -------------------------------------------------------------------------

        @Test
        void testInitFileMenuContainsQuitActionOnlyOnNonMacOS() {
            var menu = controller.initFileMenu();

            var hasQuit = menuContainsAction(menu, Actions.QUIT_ACTION);
            assertThat(hasQuit).isEqualTo(!SystemInfo.isMacOS);
        }

        // -------------------------------------------------------------------------
        // initEditMenu — Preferences action present iff non-macOS (rows 17 & 18)
        // -------------------------------------------------------------------------

        @Test
        void testInitEditMenuContainsPreferencesActionOnlyOnNonMacOS() {
            var menu = controller.initEditMenu();

            var hasPrefs = menuContainsAction(menu, Actions.PREFERENCES_ACTION);
            assertThat(hasPrefs).isEqualTo(!SystemInfo.isMacOS);
        }

        // -------------------------------------------------------------------------
        // initMenus — setJMenuBar called on mainFrame iff macOS (row 19)
        // -------------------------------------------------------------------------

        @Test
        void testInitMenusSetsJMenuBarOnlyOnMacOS() {
            // initMenus() was already called inside the constructor; verify its side-effect.
            if (SystemInfo.isMacOS) {
                verify(mockFrame, times(1)).setJMenuBar(any(JMenuBar.class));
            } else {
                verify(mockFrame, never()).setJMenuBar(any(JMenuBar.class));
            }
        }

        // -----------------------------------------------------------------------
        // Helper
        // -----------------------------------------------------------------------

        /**
         * Returns true if {@code menu} contains a direct child item whose action
         * is {@code action}.
         */
        private static boolean menuContainsAction(JMenu menu, Action action) {
            return Arrays.stream(menu.getMenuComponents())
                .anyMatch(c -> c instanceof JMenuItem item && item.getAction() == action);
        }
    }
}
