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

import static songscribe.util.UIUtils.setupDesktopHandlers;

import java.io.File;

import javax.swing.*;

import org.jetbrains.annotations.NotNull;

import com.formdev.flatlaf.util.SystemInfo;

import songscribe.Version;
import songscribe.ui.action.Actions;
import songscribe.ui.action.DialogOpenAction;
import songscribe.ui.action.ExportABCAction;
import songscribe.ui.action.ExportImageAction;
import songscribe.ui.action.ExportMidiAction;
import songscribe.ui.action.ExportPDFAction;
import songscribe.ui.action.ExportSVGAction;
import songscribe.ui.action.LaunchAction;
import songscribe.ui.action.NewAction;
import songscribe.ui.action.OpenAction;
import songscribe.ui.action.PDFTutorialOpenAction;
import songscribe.ui.action.SaveAction;
import songscribe.ui.action.SaveAsAction;
import songscribe.ui.action.TipAction;
import songscribe.ui.component.MainFrame;
import songscribe.ui.dialog.KeyMapDialog;
import songscribe.ui.dialog.LineWidthChangeDialog;
import songscribe.ui.dialog.ReportBugDialog;
import songscribe.ui.dialog.TutorialDialog;
import songscribe.ui.dialog.WhatsNewDialog;
import songscribe.ui.message.MessageCenter;
import songscribe.ui.playback.PlayMenu;

public class MenuController {

    // Actions
    public static final String ABOUT_ACTION_NAME = "About";

    // We need to keep a reference to the instance to prevent it from being garbage collected
    @SuppressWarnings({ "FieldCanBeLocal", "unused" })
    private static MenuController instance = null;

    public static void init() {
        instance = new MenuController();
    }

    public MenuController() {
        initMenus();
        MessageCenter.subscribe(this);
    }

    private static void initMenus() {
        var menuBar = new JMenuBar();

        menuBar.add(initFileMenu());
        menuBar.add(initEditMenu());
        menuBar.add(InsertMenu.getInstance());
        menuBar.add(new NotesMenu());
        menuBar.add(initModeMenu());
        menuBar.add(new PlayMenu());
        menuBar.add(initCompositionMenu());
        //        menuBar.add(launchMenu);
        //        menuBar.add(initHelpMenu());
        //        var launchMenu = initLaunchMenu();

        if (DebugState.isDebugEnabled()) {
            menuBar.add(new DebugMenu());
        }

        var mainFrame = MainFrame.getInstance();

        if (SystemInfo.isMacOS) {
            // Desktop.getDesktop().setDefaultMenuBar(menuBar) is broken in macOS.
            // We have to use setJMenuBar() in conjunction with:
            // System.setProperty("apple.laf.useScreenMenuBar", "true");
            mainFrame.setJMenuBar(menuBar);
        }

        // Set up the standard OS menu item handlers
        setupDesktopHandlers(mainFrame, true);
    }

    private static JMenu initFileMenu() {
        var menu = new JMenu("File");
        menu.add(new NewAction());
        menu.add(new OpenAction());

        menu.addSeparator();

        menu.add(new SaveAction());
        menu.add(new SaveAsAction());

        menu.addSeparator();

        menu.add(new ExportMidiAction());
        menu.add(new ExportImageAction());
        menu.add(new ExportPDFAction());
        menu.add(new ExportSVGAction());
        menu.add(new ExportABCAction());

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

    private static JMenu initEditMenu() {
        var menu = new JMenu("Edit");
        var score = MainFrame.getInstance().getScore();

        menu.add(Actions.CUT_ACTION);
        menu.add(Actions.COPY_ACTION);
        menu.add(Actions.PASTE_ACTION);
        menu.add(Actions.DELETE_ACTION);
        menu.add(Actions.SELECT_LINE_ACTION);
        menu.add(Actions.DESELECT_ACTION);

        menu.addSeparator();

        var controlMenu = new JMenu("Control");

        for (var action : Actions.CONTROL_ACTION_GROUP.getActions()) {
            controlMenu.add(new JRadioButtonMenuItem(action));
        }

        menu.add(controlMenu);

        if (!SystemInfo.isMacOS) {
            menu.add(Actions.PREFERENCES_ACTION);
        }

        return menu;
    }

    private static JMenu initModeMenu() {
        var menu = new JMenu("Adjustment");

        menu.add(new JRadioButtonMenuItem(Actions.ADJUST_MUSIC_MODE_ACTION));
        menu.add(new JRadioButtonMenuItem(Actions.ADJUST_LYRICS_MODE_ACTION));
        menu.add(new JRadioButtonMenuItem(Actions.ADJUST_VERTICAL_MODE_ACTION));

        return menu;
    }

    private static JMenu initCompositionMenu() {
        var menu = new JMenu("Composition");
        menu.add(Actions.COMPOSITION_SETTINGS_ACTION);
        menu.add(Actions.LYRICS_DIALOG_ACTION);

        menu.add(
            new DialogOpenAction<>("Line Width...", LineWidthChangeDialog.class)
        );

        return menu;
    }

    @NotNull
    private JMenu initHelpMenu() {
        // TODO: Help needs updating for the new app
        var menu = new JMenu("Help");
        menu.add(
            new DialogOpenAction<>("Basic Tutorial", TutorialDialog.class)
        );
        menu.add(new PDFTutorialOpenAction("Extended Tutorial (PDF)"));
        menu.add(new TipAction());
        menu.add(new DialogOpenAction<>("Keymap", KeyMapDialog.class));

        menu.addSeparator();

        addCommonHelpItems(menu);
        return menu;
    }

    protected void addCommonHelpItems(JMenu menu) {
        menu.add(new DialogOpenAction<>("Report a Bug", ReportBugDialog.class));

        if (new File(WhatsNewDialog.WHATS_NEW_FILE).exists()) {
            menu.add(
                new DialogOpenAction<>(
                    "What’s new in " + Version.PUBLIC_VERSION,
                    WhatsNewDialog.class
                )
            );
        }

        if (!SystemInfo.isMacOS) {
            menu.add(Actions.ABOUT_ACTION);
        }
    }

    @NotNull
    private static JMenu initLaunchMenu() {
        var menu = new JMenu("Launch");
        menu.add(new LaunchAction(LaunchAction.App.SONGBOOK));
        menu.add(new LaunchAction(LaunchAction.App.SONGSHOW));
        return menu;
    }
}
