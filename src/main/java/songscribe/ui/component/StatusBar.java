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
import java.awt.event.*;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.*;

import net.engio.mbassy.listener.Handler;

import songscribe.ui.Constants;
import songscribe.ui.action.Actions;
import songscribe.ui.message.ControlChangedMessage;
import songscribe.ui.message.MessageCenter;
import songscribe.ui.message.ModeChangedMessage;

public class StatusBar extends JPanel {

    private static final long MEM_PROGRESS_REFRESH_RATE = 1000;
    private static final int HEIGHT = 26;

    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final JLabel modeLabel = new JLabel();
    private final JLabel controlLabel = new JLabel();
    private final JLabel pitchLabel = new JLabel();

    public StatusBar() {
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setBorder(
            BorderFactory.createMatteBorder(
                1,
                0,
                0,
                0,
                UIManager.getColor("ToolBar.separatorColor")
            )
        );
        var panelSize = new Dimension(120, HEIGHT);

        var pitchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pitchPanel.add(pitchLabel);
        pitchPanel.setPreferredSize(panelSize);
        pitchPanel.setMaximumSize(panelSize);
        pitchPanel.setToolTipText("Present pitch");
        add(pitchPanel);

        add(createSeparator());
        add(Box.createGlue());
        add(createSeparator());

        var modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        modePanel.add(modeLabel);
        modePanel.setPreferredSize(panelSize);
        modePanel.setMaximumSize(panelSize);
        modePanel.setToolTipText("Editing mode");
        modePanel.addMouseListener(
            new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        Actions.MODE_ACTION_GROUP.selectNext();
                    }
                }
            }
        );
        add(modePanel);

        add(createSeparator());

        var controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controlPanel.add(controlLabel);
        controlPanel.setPreferredSize(panelSize);
        controlPanel.setMaximumSize(panelSize);
        controlPanel.setToolTipText("Control");
        controlPanel.addMouseListener(
            new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        Actions.CONTROL_ACTION_GROUP.selectNext();
                    }
                }
            }
        );
        add(controlPanel);

        initMemoryPanel();
        MessageCenter.subscribe(this);
    }

    private void initMemoryPanel() {
        if (
            MainFrame.getInstance()
                .getProperties()
                .getProperty(Constants.SHOW_MEM_USAGE)
                .equals(Constants.TRUE_VALUE)
        ) {
            var progress = new JProgressBar();
            progress.setMaximumSize(new Dimension(50, HEIGHT));
            progress.setStringPainted(true);

            add(createSeparator());
            add(Box.createHorizontalStrut(4));
            add(progress);

            var trashButton = new JButton("GC");
            var size = new Dimension(20, HEIGHT);
            trashButton.setPreferredSize(size);
            trashButton.setMaximumSize(size);
            trashButton.setToolTipText("Run garbage collector");
            trashButton.addActionListener(_ -> memoryBean.gc());
            add(trashButton);

            new Timer(true).schedule(
                new MemProgressTask(progress),
                0,
                MEM_PROGRESS_REFRESH_RATE
            );
        }
    }

    private static JSeparator createSeparator() {
        var separator = new JSeparator(SwingConstants.VERTICAL);
        separator.setMaximumSize(new Dimension(2, HEIGHT));
        return separator;
    }

    public void setModeLabel(String mode) {
        modeLabel.setText(mode);
    }

    public void setControlLabel(String control) {
        controlLabel.setText(control);
    }

    public void setPitchString(String pitch) {
        pitchLabel.setText(pitch);
    }

    private final class MemProgressTask extends TimerTask {

        private static final int MEGABYTE = 1048576;
        private final JProgressBar memProgress;

        private MemProgressTask(JProgressBar memProgress) {
            this.memProgress = memProgress;
        }

        @Override
        public void run() {
            var usage = memoryBean.getHeapMemoryUsage();
            memProgress.setMaximum((int) usage.getCommitted());
            memProgress.setValue((int) usage.getUsed());
            var maxMem = (int) (usage.getCommitted() / MEGABYTE);
            var usedMem = (int) (usage.getUsed() / MEGABYTE);
            memProgress.setString(usedMem + "M of " + maxMem + 'M');
            memProgress.setToolTipText(
                "Total heap size: " + maxMem + "M   Used: " + usedMem + 'M'
            );
        }
    }

    @Handler
    public void modeDidChange(ModeChangedMessage message) {
        setModeLabel(MainFrame.getInstance().getScore().getMode().name());
    }

    @Handler
    public void controlDidChange(ControlChangedMessage message) {
        setControlLabel(message.getControl().getDescription());
    }
}
