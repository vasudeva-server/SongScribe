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

import songscribe.util.UIUtils;
import songscribe.model.Duration;
import songscribe.model.Tempo;
import songscribe.ui.FlatLafKeys;
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
    private final JComboBox<String> tempoDescriptionCombo = new JComboBox<>();
    private final JCheckBox showOnlyDescriptionCheckBox;

    /**
     * @param types         the note types to show in the type combo
     * @param checkboxLabel the label for the "show only description" checkbox
     * @param fileNames     resource file names from which to load description values
     */
    TempoSection(Duration[] types, String checkboxLabel, String... fileNames) {
        tempoTypeCombo = DurationListCellRenderer.createCombo(types);

        showOnlyDescriptionCheckBox = new JCheckBox(checkboxLabel);

        tempoDescriptionCombo.setEditable(true);

        for (var fileName : fileNames) {
            UIUtils.readComboValuesFromFile(tempoDescriptionCombo, fileName);
        }

        var spinner = new JSpinner(tempoSpinnerModel);
        InputUtils.addNumericFilter(spinner);

        setLayout(new GridBagLayout());
        setAlignmentX(Component.LEFT_ALIGNMENT);

        var gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 0, FlatLafProps.<Integer>get(FlatLafKeys.DIALOG_COMPONENT_HORIZONTAL_GAP));
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
        gbc.insets = new Insets(FlatLafProps.<Integer>get(FlatLafKeys.DIALOG_COMPONENT_VERTICAL_EXTRA_GAP), 0, 0, 0);
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

    String getTempoDescription() {
        var item = tempoDescriptionCombo.getSelectedItem();
        return item != null ? (String) item : "";
    }

    boolean isShowOnlyDescription() {
        return showOnlyDescriptionCheckBox.isSelected();
    }

}
