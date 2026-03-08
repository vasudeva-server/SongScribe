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

import songscribe.prefs.Prefs;
import songscribe.Strings;

public class PreferencesDialog extends StandardDialog {

    // Playback
    private final JSlider durationSlider = new JSlider(34, 100);
    private final JCheckBox playInsertingNoteCheck = new JCheckBox(
        Strings.get(Strings.LABEL_PREFS_PLAY_INSERTED_NOTE)
    );

    public PreferencesDialog() {
        super(Strings.get(Strings.DIALOG_PREFERENCES_TITLE));
        var playbackPanel = new JPanel();
        playbackPanel.setLayout(new BoxLayout(playbackPanel, BoxLayout.Y_AXIS));
        playbackPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 0, 15));

        var label = new JLabel(Strings.get(Strings.LABEL_PREFS_PLAYBACK_DURATION));
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
        labels.put(34, new JLabel(Strings.get(Strings.LABEL_PREFS_STACCATO)));
        labels.put(67, new JLabel(Strings.get(Strings.LABEL_PREFS_NORMAL)));
        labels.put(100, new JLabel(Strings.get(Strings.LABEL_PREFS_LEGATO)));
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
        var prefs = Prefs.getInstance();
        durationSlider.setValue(prefs.getInt("playbackNoteDuration"));
        playInsertingNoteCheck.setSelected(prefs.getBoolean("playInsertedNote"));
    }

    @Override
    protected void setData() {
        var prefs = Prefs.getInstance();
        prefs.put("playbackNoteDuration", durationSlider.getValue());
        prefs.put("playInsertedNote", playInsertingNoteCheck.isSelected());
        mainFrame.fireMusicChanged(this);
    }
}
