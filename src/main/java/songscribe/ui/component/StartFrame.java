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

import songscribe.ui.Constants;
import songscribe.util.GraphicUtils;

public final class StartFrame {

    private StartFrame() {}

    public static void startFrame(String[] args) {
        var frame = new JFrame(Constants.PACKAGE_NAME);
        var panel = new JPanel();
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        var label = new JLabel("Which application do you want to start?");
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(label);
        panel.add(Box.createVerticalStrut(10));
        var bg = new ButtonGroup();
        GraphicUtils.setMediaTracker(new MediaTracker(frame));
        var sw = new JRadioButton("Song Writer", null, true);
        bg.add(sw);
        panel.add(sw);
        panel.add(Box.createVerticalStrut(5));
        var ss = new JRadioButton("Song Show", null);
        bg.add(ss);
        panel.add(ss);
        panel.add(Box.createVerticalStrut(5));
        var sb = new JRadioButton("Song Book", null);
        bg.add(sb);
        panel.add(sb);
        frame.getContentPane().add(panel);
        var start = new JButton("Start");
        start.addActionListener(_ -> {
            frame.setVisible(false);

            if (sw.isSelected()) {
                MainFrame.main(args);
            }
        });
        var south = new JPanel();
        south.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        south.add(start);
        frame.getContentPane().add(BorderLayout.SOUTH, south);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
