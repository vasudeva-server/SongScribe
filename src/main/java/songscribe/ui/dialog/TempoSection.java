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

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;

import songscribe.Strings;
import songscribe.dom.Duration;
import songscribe.dom.Tempo;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.ui.component.DurationListCellRenderer;
import songscribe.ui.component.InputUtils;

/**
 * A reusable panel containing the standard tempo controls:
 * note-type combo, BPM spinner, description combo, and
 * "show only description" checkbox.
 *
 * <p>Callers supply the set of note types, the checkbox label text,
 * and the resource file names from which description values are loaded.
 */
class TempoSection extends JPanel {

    private final JComboBox<Duration> tempoTypeCombo;
    private final SpinnerModel tempoSpinnerModel = new SpinnerNumberModel(120, 40, 220, 1);
    private final OtherValueComboBox tempoDescriptionCombo;
    private final JCheckBox showOnlyDescriptionCheckBox;

    /**
     * @param types         the note types to show in the type combo
     * @param checkboxLabel the label for the "show only description" checkbox
     * @param fileNames     resource file names from which to load description values
     */
    TempoSection(Duration[] types, String checkboxLabel, String... fileNames) {
        tempoTypeCombo = DurationListCellRenderer.createCombo(types);

        showOnlyDescriptionCheckBox = new JCheckBox(checkboxLabel);

        tempoDescriptionCombo = new OtherValueComboBox(
            new OtherValuePrompt(
                Strings.get(Strings.DIALOG_TEMPO_TITLE),
                Strings.get(Strings.LABEL_TEMPO_OTHER_PROMPT)
            ),
            OtherValueComboBox.EmptyChoice.OFFERED,
            fileNames
        );

        var spinner = new JSpinner(tempoSpinnerModel);
        InputUtils.addNumericFilter(spinner);

        setLayout(new GridBagLayout());
        setAlignmentX(Component.LEFT_ALIGNMENT);

        var gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 0, FlatLafProps.getInt(FlatLafKey.DIALOG_COMPONENT_HORIZONTAL_GAP));
        gbc.anchor = GridBagConstraints.WEST;
        add(tempoTypeCombo, gbc);

        gbc.gridx += 1;
        add(new JLabel("="), gbc);

        gbc.gridx += 1;
        add(spinner, gbc);

        gbc.gridx += 1;
        add(tempoDescriptionCombo, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 4;
        gbc.insets = new Insets(FlatLafProps.getInt(FlatLafKey.DIALOG_COMPONENT_VERTICAL_EXTRA_GAP), 0, 0, 0);
        add(showOnlyDescriptionCheckBox, gbc);

        // Prevent the panel from growing beyond its preferred size
        setMaximumSize(getPreferredSize());
    }

    /** Populates all controls from the given tempo. */
    void setTempo(Tempo tempo) {
        tempoTypeCombo.setSelectedItem(tempo.getTempoType());
        tempoSpinnerModel.setValue(tempo.getVisibleTempo());
        tempoDescriptionCombo.setSelectedItem(tempo.getTempoDescription());
        showOnlyDescriptionCheckBox.setSelected(!tempo.shouldShowTempo());
    }

    Duration getTempoType() {
        var item = tempoTypeCombo.getSelectedItem();

        if (item == null) {
            throw new IllegalStateException("No tempo type selected");
        }

        return (Duration) item;
    }

    int getVisibleTempo() {
        return (Integer) tempoSpinnerModel.getValue();
    }

    /**
     * @return the description as chosen, empty when the user chose {@code (none)}
     */
    String getTempoDescription() {
        return tempoDescriptionCombo.getValue();
    }

    boolean isShowOnlyDescription() {
        return showOnlyDescriptionCheckBox.isSelected();
    }

}
