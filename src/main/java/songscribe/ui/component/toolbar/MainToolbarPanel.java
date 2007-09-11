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

package songscribe.ui.component.toolbar;

import java.awt.*;

import javax.swing.*;

import songscribe.ui.component.InsertPopupButton;
import songscribe.ui.component.ModePopupButton;

/**
 * This class creates the main toolbar panel for the application.
 * The editing tools are grouped together in the left side of the toolbar,
 * and the playback controls are grouped together on the right side.
 */
public final class MainToolbarPanel extends JPanel {

    // The minimum space between the main tools and the playback controls
    private static final int TOOLBAR_MARGIN = 40;

    private static final int NO_BORDER = 0;
    private static final int LEFT_BORDER = 1;
    private static final int RIGHT_BORDER = 1 << 1;

    public MainToolbarPanel() {
        setLayout(new BorderLayout());
        setBorder(
            BorderFactory.createMatteBorder(
                1,
                0,
                0,
                0,
                UIManager.getColor("ToolBar.separatorColor")
            )
        );
        var toolsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        toolsPanel.add(createModeToolbarPanel());
        toolsPanel.add(createDurationToolbarPanel());
        toolsPanel.add(createDotRestToolbarPanel());
        toolsPanel.add(createAccidentalToolbarPanel());
        toolsPanel.add(createArticulationToolbarPanel());
        toolsPanel.add(createConnectionToolbarPanel());
        toolsPanel.add(createBarToolbarPanel());
        toolsPanel.add(createInsertToolbarPanel());
        add(toolsPanel, BorderLayout.WEST);

        // Add a minimum margin between the main tools and the playback controls
        add(Box.createHorizontalStrut(TOOLBAR_MARGIN), BorderLayout.CENTER);

        add(createPlaybackToolbarPanel(), BorderLayout.EAST);
    }

    private static JPanel createToolbarPanel(int borders) {
        var panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));

        panel.setBorder(
            BorderFactory.createMatteBorder(
                0,
                ((borders & LEFT_BORDER) != 0) ? 1 : 0,
                0,
                ((borders & RIGHT_BORDER) != 0) ? 1 : 0,
                UIManager.getColor("ToolBar.separatorColor")
            )
        );

        return panel;
    }

    private static JToolBar createToolbar() {
        var toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setRollover(true);
        toolbar.setMargin(new Insets(0, 0, 0, 0));
        return toolbar;
    }

    private static JPanel createModeToolbarPanel() {
        var panel = createToolbarPanel(NO_BORDER);
        var toolbar = createToolbar();
        toolbar.add(new ModePopupButton());
        panel.add(toolbar);
        return panel;
    }

    private static JPanel createDurationToolbarPanel() {
        var panel = createToolbarPanel(RIGHT_BORDER);
        panel.add(new DurationToolbar());
        return panel;
    }

    private static JPanel createDotRestToolbarPanel() {
        var panel = createToolbarPanel(RIGHT_BORDER);
        panel.add(new DotRestToolbar());
        return panel;
    }

    private static JPanel createAccidentalToolbarPanel() {
        var panel = createToolbarPanel(RIGHT_BORDER);
        panel.add(new AccidentalToolbar());
        return panel;
    }

    private static JPanel createBarToolbarPanel() {
        var panel = createToolbarPanel(RIGHT_BORDER);
        panel.add(new BarToolbar());
        return panel;
    }

    private static JPanel createConnectionToolbarPanel() {
        var panel = createToolbarPanel(RIGHT_BORDER);
        panel.add(new ConnectionToolbar());
        return panel;
    }

    private static JPanel createArticulationToolbarPanel() {
        var panel = createToolbarPanel(RIGHT_BORDER);
        panel.add(new ArticulationToolbar());
        return panel;
    }

    private static JPanel createInsertToolbarPanel() {
        var panel = createToolbarPanel(NO_BORDER);
        var toolbar = new Toolbar();
        toolbar.add(new InsertPopupButton());
        panel.add(toolbar);
        return panel;
    }

    private static JPanel createPlaybackToolbarPanel() {
        var panel = createToolbarPanel(LEFT_BORDER);
        panel.add(new PlaybackToolbar());
        return panel;
    }
}
