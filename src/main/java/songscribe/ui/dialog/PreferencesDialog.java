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
package songscribe.ui.dialog;

import java.awt.*;
import java.util.Dictionary;
import java.util.Hashtable;

import javax.swing.*;

import songscribe.ui.Constants;
import songscribe.ui.component.MainFrame;

public class PreferencesDialog extends StandardDialog {

    // Playback
    private final JSlider durationSlider = new JSlider(34, 100);
    private final JCheckBox playInsertingNoteCheck = new JCheckBox(
        "Play the note being inserted"
    );

    public PreferencesDialog(MainFrame mainFrame) {
        super("Preferences");
        var playbackPanel = new JPanel();
        playbackPanel.setLayout(new BoxLayout(playbackPanel, BoxLayout.Y_AXIS));
        playbackPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 0, 15));

        var label = new JLabel("Playback note duration:");
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        playbackPanel.add(label);

        playbackPanel.add(Box.createVerticalStrut(10));

        durationSlider.setMajorTickSpacing(33);
        durationSlider.setMinorTickSpacing(11);
        durationSlider.setSnapToTicks(true);
        durationSlider.setPaintLabels(true);
        durationSlider.setPaintTicks(true);
        //noinspection UseOfObsoleteCollectionType
        Dictionary<Integer, JLabel> labels = new Hashtable<>();
        labels.put(34, new JLabel("Staccato"));
        labels.put(67, new JLabel("Normal"));
        labels.put(100, new JLabel("Legato"));
        durationSlider.setLabelTable(labels);
        durationSlider.setAlignmentX(Component.LEFT_ALIGNMENT);
        playbackPanel.add(durationSlider);

        playbackPanel.add(Box.createVerticalStrut(20));

        playbackPanel.add(playInsertingNoteCheck);

        contentPanel.add(BorderLayout.CENTER, playbackPanel);

        buttonPanel.remove(applyButton);
        contentPanel.add(BorderLayout.SOUTH, buttonPanel);
    }

    @Override
    protected void getData() {
        var props = mainFrame.getProperties();
        durationSlider.setValue(
            Integer.parseInt(
                props.getProperty(Constants.PLAYBACK_NOTE_DURATION_PROP)
            )
        );
        playInsertingNoteCheck.setSelected(
            props
                .getProperty(Constants.PLAY_INSERTING_NOTE)
                .equals(Constants.TRUE_VALUE)
        );
    }

    @Override
    protected void setData() {
        var props = mainFrame.getProperties();
        props.setProperty(
            Constants.PLAYBACK_NOTE_DURATION_PROP,
            Integer.toString(durationSlider.getValue())
        );
        props.setProperty(
            Constants.PLAY_INSERTING_NOTE,
            playInsertingNoteCheck.isSelected()
                ? Constants.TRUE_VALUE
                : Constants.FALSE_VALUE
        );
        mainFrame.fireMusicChanged(this);
    }
}
