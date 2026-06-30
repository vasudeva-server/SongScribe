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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;

import org.jspecify.annotations.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.formdev.flatlaf.util.SystemInfo;
import com.uber.nullaway.annotations.Initializer;
import net.engio.mbassy.listener.Handler;

import songscribe.error.RuntimeError;
import songscribe.Strings;
import songscribe.message.MessageCenter;
import songscribe.message.notification.RecentDocumentsDidChangeNotification;
import songscribe.prefs.RecentDocumentsManager;
import songscribe.ui.action.Actions;
import songscribe.ui.action.ClearRecentsAction;
import songscribe.ui.action.CloseWindowAction;
import songscribe.ui.action.ExportABCAction;
import songscribe.ui.action.ExportImageAction;
import songscribe.ui.action.ExportMidiAction;
import songscribe.ui.action.ExportPDFAction;
import songscribe.ui.action.ExportSVGAction;
import songscribe.ui.action.InsertLineAction;
import songscribe.ui.action.NewAction;
import songscribe.ui.action.OpenAction;
import songscribe.ui.action.OpenRecentAction;
import songscribe.ui.action.SaveAction;
import songscribe.ui.action.SaveAsAction;
import songscribe.ui.component.MainFrame;
import songscribe.ui.platform.mac.MacNativeMenuController;

public class MenuController {

    private static final Logger LOG = LoggerFactory.getLogger(MenuController.class);

    // We need to keep a reference to the instance to prevent it from being garbage collected
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private static @Nullable MenuController instance = null;

    /** Strong reference prevents GC — MBassador uses weak subscriber references. */
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private static @Nullable MacNativeMenuController nativeMenuController = null;

    private final MainFrame mainFrame;
    JMenu openRecentMenu;

    public static void init(MainFrame mainFrame) {
        instance = new MenuController(mainFrame);
    }

    public MenuController(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        var scoreView = mainFrame.requireScoreView();
        initMenus();
        MessageCenter.subscribe(this);
    }

    void initMenus() {
        var menuBar = new JMenuBar();

        menuBar.add(initFileMenu());
        menuBar.add(initEditMenu());
        menuBar.add(new NotationMenu(mainFrame));
        menuBar.add(initSongMenu());
        //        menuBar.add(initModeMenu());

        if (SystemInfo.isMacOS) {
            // Desktop.getDesktop().setDefaultMenuBar(menuBar) is broken in macOS.
            // We have to use setJMenuBar() in conjunction with
            // apple.laf.useScreenMenuBar (set in SongScribe.main).
            mainFrame.setJMenuBar(menuBar);
        }

        if (SystemInfo.isMacOS) {
            try {
                nativeMenuController = new MacNativeMenuController();
            } catch (Throwable e) {
                if (GraphicsEnvironment.isHeadless()) {
                    LOG.debug("MacNativeMenuController not available: {}", e.getMessage());
                } else {
                    throw RuntimeError.exit("MacNativeMenuController not available", e);
                }
            }
        }
    }

    @Initializer
    JMenu initFileMenu() {
        var menu = new JMenu(Strings.get(Strings.MENU_FILE));
        menu.add(NewAction.createAction(mainFrame));
        menu.add(OpenAction.createAction(mainFrame));
        openRecentMenu = new JMenu(Strings.get(Strings.MENU_FILE_OPEN_RECENT));
        rebuildOpenRecentMenu();
        menu.add(openRecentMenu);
        menu.add(CloseWindowAction.createAction(mainFrame));

        menu.addSeparator();

        menu.add(SaveAction.createAction(mainFrame));
        menu.add(SaveAsAction.createAction(mainFrame));

        menu.addSeparator();

        menu.add(ExportMidiAction.createAction(mainFrame));
        menu.add(ExportImageAction.createAction(mainFrame));
        menu.add(ExportPDFAction.createAction(mainFrame));
        menu.add(ExportSVGAction.createAction(mainFrame));
        menu.add(ExportABCAction.createAction(mainFrame));

        menu.addSeparator();

        menu.add(Actions.PRINT_ACTION);

        // Even though the quit action is not added on macOS, we need to
        // instantiate the action so it is registered in the global action map.
        if (!SystemInfo.isMacOS) {
            menu.addSeparator();
            menu.add(Actions.QUIT_ACTION);
        }

        return menu;
    }

    void rebuildOpenRecentMenu() {
        openRecentMenu.removeAll();
        var recents = RecentDocumentsManager.getRecents();

        if (recents.isEmpty()) {
            var noRecentItem = new JMenuItem(Strings.get(Strings.MENU_FILE_NO_RECENT_DOCUMENTS));
            noRecentItem.setEnabled(false);
            openRecentMenu.add(noRecentItem);
        } else {
            var labels = buildLabels(recents);

            for (var i = 0; i < recents.size(); i++) {
                openRecentMenu.add(new OpenRecentAction(mainFrame, labels.get(i), recents.get(i)));
            }

            openRecentMenu.addSeparator();
            openRecentMenu.add(ClearRecentsAction.createAction(mainFrame));
        }
    }

    @Handler
    public void recentDocumentsDidChange(RecentDocumentsDidChangeNotification message) {
        rebuildOpenRecentMenu();
    }

    static List<String> buildLabels(List<? extends Path> paths) {
        var nameToIndices = new LinkedHashMap<String, List<Integer>>();

        for (var i = 0; i < paths.size(); i++) {
            var filename = paths.get(i).getFileName().toString();
            nameToIndices.computeIfAbsent(filename, k -> new ArrayList<>()).add(i);
        }

        var labels = new String[paths.size()];

        for (var entry : nameToIndices.entrySet()) {
            var filename = entry.getKey();
            var indices = entry.getValue();

            if (indices.size() == 1) {
                labels[indices.getFirst()] = filename;
            } else {
                disambiguate(paths, indices, filename, labels);
            }
        }

        return List.of(labels);
    }

    static void disambiguate(
        List<? extends Path> paths,
        List<Integer> indices,
        String filename,
        String[] labels
    ) {
        var homePath = Path.of(System.getProperty("user.home"));
        var maxDepth = paths.stream()
            .mapToInt(p -> p.getNameCount() - 1)
            .max()
            .orElse(0);

        for (var depth = 1; depth <= maxDepth; depth++) {
            var suffixMap = new HashMap<Integer, String>();
            var seen = new HashSet<String>();
            var allUnique = true;

            for (var idx : indices) {
                // Paths from RecentDocumentsManager are always absolute, so parent is always non-null
                var parent = paths.get(idx).getParent();

                if (parent == null) {
                    continue;
                }

                var nameCount = parent.getNameCount();
                var start = Math.max(0, nameCount - depth);
                var suffix = parent.subpath(start, nameCount).toString();
                var suffixStr = tildeSubstitute(suffix, homePath);
                suffixMap.put(idx, suffixStr);

                if (!seen.add(suffixStr)) {
                    allUnique = false;
                }
            }

            if (allUnique) {
                for (var idx : indices) {
                    labels[idx] = filename + " — " + suffixMap.get(idx);
                }

                return;
            }
        }

        // Fallback: full absolute path with ~ substitution
        for (var idx : indices) {
            labels[idx] = tildeSubstitute(paths.get(idx).toString(), homePath);
        }
    }

    static String tildeSubstitute(String pathStr, Path homePath) {
        var path = Path.of(pathStr);

        if (path.startsWith(homePath)) {
            var relative = homePath.relativize(path);
            var relStr = relative.toString();
            return relStr.isEmpty() ? "~" : "~/" + relStr;
        }

        return pathStr;
    }

    static JMenu initEditMenu() {
        var menu = new JMenu(Strings.get(Strings.MENU_EDIT));

        menu.add(Actions.CUT_ACTION);
        menu.add(Actions.COPY_ACTION);
        menu.add(Actions.PASTE_ACTION);
        menu.add(Actions.DELETE_ACTION);
        menu.add(Actions.SELECT_LINE_ACTION);
        menu.add(Actions.DESELECT_ACTION);

        menu.addSeparator();

        var controlMenu = new JMenu(Strings.get(Strings.MENU_EDIT_CONTROL));

        for (var action : Actions.CONTROL_ACTION_GROUP.getActions()) {
            controlMenu.add(new JRadioButtonMenuItem(action));
        }

        menu.add(controlMenu);

        if (!SystemInfo.isMacOS) {
            menu.add(Actions.PREFERENCES_ACTION);
        }

        return menu;
    }

//    private static JMenu initModeMenu() {
//        var menu = new JMenu(Strings.get(Strings.MENU_ADJUSTMENT));
//
//        menu.add(new JRadioButtonMenuItem(Actions.ADJUST_MUSIC_MODE_ACTION));
//        menu.add(new JRadioButtonMenuItem(Actions.ADJUST_VERTICAL_MODE_ACTION));
//
//        return menu;
//    }

    private JMenu initSongMenu() {
        var menu = new JMenu(Strings.get(Strings.MENU_SONG));

        var lineMenu = new JMenu(Strings.get(Strings.MENU_SONG_LINE));
        lineMenu.add(InsertLineAction.createAddLineAction(mainFrame));
        lineMenu.add(InsertLineAction.createInsertLineBeforeAction(mainFrame));
        lineMenu.add(InsertLineAction.createInsertLineAfterAction(mainFrame));
        menu.add(lineMenu);
        menu.addSeparator();

        menu.add(Actions.SONG_SETTINGS_ACTION);
        return menu;
    }

}
