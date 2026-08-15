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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.MediaTracker;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.WindowConstants;

import songscribe.Constants;
import songscribe.Strings;
import songscribe.util.GraphicUtils;

public final class StartFrame {

    private StartFrame() {}

    public static void startFrame(String[] args) {
        var frame = new JFrame(Constants.PACKAGE_NAME);
        var panel = new JPanel();
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        var label = new JLabel(Strings.get(Strings.LABEL_START_WHICH_APPLICATION));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(label);
        panel.add(Box.createVerticalStrut(10));
        var bg = new ButtonGroup();
        GraphicUtils.setMediaTracker(new MediaTracker(frame));
        var sw = new JRadioButton(Strings.get(Strings.APP_SONGWRITER), null, true);
        bg.add(sw);
        panel.add(sw);
        panel.add(Box.createVerticalStrut(5));
        var ss = new JRadioButton(Strings.get(Strings.ACTION_LAUNCH_SONG_SHOW), null);
        bg.add(ss);
        panel.add(ss);
        panel.add(Box.createVerticalStrut(5));
        var sb = new JRadioButton(Strings.get(Strings.ACTION_LAUNCH_SONG_BOOK), null);
        bg.add(sb);
        panel.add(sb);
        frame.getContentPane().add(panel);
        var start = new JButton(Strings.get(Strings.LABEL_BUTTON_START));
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
