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

import module java.desktop;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Objects;

import songscribe.Strings;
import songscribe.prefs.Prefs;
import songscribe.ui.Appearance;
import songscribe.ui.AppearanceManager;

public class PreferencesDialog extends StandardDialog {

    // General tab
    private final JSlider durationSlider = new JSlider(34, 100);
    private final JCheckBox playInsertingNoteCheck = new JCheckBox(
        Strings.get(Strings.LABEL_PREFS_PLAY_INSERTED_NOTE)
    );

    // Appearance tab
    private final JRadioButton systemRadio = new JRadioButton(
        Strings.get(Strings.LABEL_PREFS_APPEARANCE_SYSTEM)
    );
    private final JRadioButton lightRadio = new JRadioButton(
        Strings.get(Strings.LABEL_PREFS_APPEARANCE_LIGHT)
    );
    private final JRadioButton darkRadio = new JRadioButton(
        Strings.get(Strings.LABEL_PREFS_APPEARANCE_DARK)
    );

    public PreferencesDialog() {
        super(Strings.get(Strings.DIALOG_PREFERENCES_TITLE));

        var group = new ButtonGroup();
        group.add(systemRadio);
        group.add(lightRadio);
        group.add(darkRadio);

        var tabbedPane = createTabbedPane();
        tabbedPane.addTab(
            Strings.get(Strings.LABEL_PREFS_TAB_GENERAL),
            createGeneralTab()
        );
        tabbedPane.addTab(
            Strings.get(Strings.LABEL_PREFS_TAB_APPEARANCE),
            createAppearanceTab()
        );

        contentPanel.add(BorderLayout.CENTER, tabbedPane);

        buttonPanel.remove(applyButton);
        contentPanel.add(BorderLayout.SOUTH, buttonPanel);
    }

    private JPanel createGeneralTab() {
        var panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 0, 20));

        var label = new JLabel(Strings.get(Strings.LABEL_PREFS_PLAYBACK_DURATION));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(label);

        panel.add(Box.createVerticalStrut(10));

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
        panel.add(durationSlider);

        panel.add(Box.createVerticalStrut(20));

        panel.add(playInsertingNoteCheck);

        return panel;
    }

    private JPanel createAppearanceTab() {
        var panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 0, 20));

        var gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;

        var description = new JLabel(
            Strings.get(Strings.LABEL_PREFS_APPEARANCE_DESCRIPTION)
        );
        description.setBorder(BorderFactory.createEmptyBorder(0, 7, 0, 7));
        panel.add(description, gbc);

        gbc.gridy = 1;
        gbc.insets = new Insets(15, 0, 0, 0);

        var radioPanel = new JPanel();
        radioPanel.setLayout(new BoxLayout(radioPanel, BoxLayout.Y_AXIS));
        radioPanel.add(systemRadio);
        radioPanel.add(Box.createVerticalStrut(5));
        radioPanel.add(lightRadio);
        radioPanel.add(Box.createVerticalStrut(5));
        radioPanel.add(darkRadio);
        panel.add(radioPanel, gbc);

        return panel;
    }

    @Override
    protected boolean getData() {
        var prefs = Prefs.getInstance();
        durationSlider.setValue(prefs.getInt("playbackNoteDuration"));
        playInsertingNoteCheck.setSelected(prefs.getBoolean("playInsertedNote"));

        (switch (AppearanceManager.getPreference()) {
            case LIGHT -> lightRadio;
            case DARK -> darkRadio;
            case SYSTEM -> systemRadio;
        }).setSelected(true);
        return true;
    }

    @Override
    protected void setData() {
        var prefs = Prefs.getInstance();
        prefs.put("playbackNoteDuration", durationSlider.getValue());
        prefs.put("playInsertedNote", playInsertingNoteCheck.isSelected());
        Objects.requireNonNull(getScore()).syncPlaybackPrefs();

        Appearance newAppearance;

        if (darkRadio.isSelected()) {
            newAppearance = Appearance.DARK;
        } else if (lightRadio.isSelected()) {
            newAppearance = Appearance.LIGHT;
        } else {
            newAppearance = Appearance.SYSTEM;
        }

        AppearanceManager.switchTheme(newAppearance);
    }
}
