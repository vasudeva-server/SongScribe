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

package songscribe.ui.component;

import java.awt.*;

import javax.swing.*;

import songscribe.Version;
import songscribe.util.GraphicUtils;
import songscribe.util.Utils;

public class SplashWindow extends JWindow {

    // Save the time when the window was created
    public final long startTime = System.currentTimeMillis();

    public SplashWindow() {
        super((Frame) null);
        init();
    }

    private void init() {
        var content = getContentPane();
        content.setLayout(new BorderLayout());
        content.setBackground(Color.WHITE);

        // Get the main splash image
        var image = GraphicUtils.readImageResource("/images/splash.jpg");

        if (image == null) {
            return;
        }

        var splashLabel = new JLabel(new ImageIcon(image));
        content.add(splashLabel, BorderLayout.CENTER);

        // Add a panel for the version number and copyright
        var infoPanel = new JPanel(new BorderLayout());

        // Give the panel a little padding
        infoPanel.setBorder(BorderFactory.createEmptyBorder(13, 13, 13, 13));
        content.add(infoPanel, BorderLayout.SOUTH);

        // Add a label with the version number
        infoPanel.add(
            createLabel("SongScribe Writer " + Version.PUBLIC_VERSION),
            BorderLayout.WEST
        );

        // Add a label with the copyright
        infoPanel.add(
            createLabel(
                "©" +
                Utils.getCurrentYear() +
                " Sri Chinmoy Centres International"
            ),
            BorderLayout.EAST
        );

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
        Utils.sleep(10);
    }

    private static JLabel createLabel(String text) {
        var label = new JLabel(text);
        label.putClientProperty("FlatLaf.styleClass", "h3.regular");
        label.setForeground(Color.BLACK);
        return label;
    }
}
