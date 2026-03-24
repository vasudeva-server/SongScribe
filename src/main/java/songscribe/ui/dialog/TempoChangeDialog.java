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

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.music.StaffElement;
import songscribe.music.Tempo;
import songscribe.file.FileUtils;

public class TempoChangeDialog extends StandardDialog {

    private @Nullable StaffElement selectedElement = null;
    private final JLabel indexOfSelectedElementLabel = new JLabel();
    private final JComboBox<?> tempoTypeCombo;
    private final SpinnerModel tempoSpinner = new SpinnerNumberModel(
        120,
        40,
        220,
        1
    );
    private final JComboBox<String> tempoDescriptionCombo = new JComboBox<>();
    private final JCheckBox showOnlyDescriptionCheckBox = new JCheckBox(
        Strings.get(Strings.DIALOG_TEMPO_CHANGE_SHOW_ONLY_DESCRIPTION)
    );
    private final JButton removeButton;

    public TempoChangeDialog() {
        super(Strings.get(Strings.DIALOG_TEMPO_CHANGE_TITLE));
        //----------------------center------------------------
        var center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        var large = new Dimension(0, 15);

        var infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        infoPanel.add(new JLabel(Strings.get(Strings.DIALOG_TEMPO_CHANGE_SELECTED_ELEMENT)));
        infoPanel.add(indexOfSelectedElementLabel);
        infoPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(infoPanel);
        center.add(Box.createRigidArea(large));

        var tempoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        tempoTypeCombo = new JComboBox<>(Tempo.Type.values());
        tempoTypeCombo.setRenderer(
            new CompositionSettingsDialog.NoteCellRenderer()
        );
        tempoPanel.add(tempoTypeCombo);
        tempoPanel.add(new JLabel("="));
        var ts = new JSpinner(tempoSpinner);
        tempoPanel.add(ts);
        tempoPanel.add(new JLabel(Strings.get(Strings.DIALOG_TEMPO_CHANGE_DESCRIPTION)));
        tempoDescriptionCombo.setEditable(true);
        FileUtils.readComboValuesFromFile(
            tempoDescriptionCombo,
            "tempochanges"
        );
        FileUtils.readComboValuesFromFile(tempoDescriptionCombo, "tempos");
        tempoPanel.add(tempoDescriptionCombo);
        tempoPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(tempoPanel);
        center.add(Box.createRigidArea(large));
        center.add(showOnlyDescriptionCheckBox);
        contentPanel.add(center);

        //----------------------south------------------------
        var south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 13, 0));
        removeButton = new JButton(Strings.get(Strings.LABEL_BUTTON_REMOVE));
        removeButton.addActionListener(_ -> {
            var score = requireScore();
            if (selectedElement == null) {
                throw new IllegalStateException("no element selected");
            }

            selectedElement.setTempoChange(null);
            setVisible(false);
            score.repaint();
            score.getComposition().setModified(true);
        });
        south.add(okButton);
        south.add(removeButton);
        south.add(cancelButton);
        contentPanel.add(BorderLayout.SOUTH, south);
    }

    @Override
    protected boolean getData() {
        var score = requireScore();
        var element = score.getSingleSelectedElement();
        selectedElement = element;

        // This should never happen, but make Java happy
        if (element == null) {
            return true;
        }

        var tempoChange = element.getTempoChange();
        var addingTempoChange = tempoChange == null;

        if (addingTempoChange) {
            tempoChange = new Tempo(144, Tempo.Type.CROTCHET, "Slower", true);
        }

        var line = element.getLine();

        indexOfSelectedElementLabel.setText(
            Strings.get(
                Strings.DIALOG_TEMPO_CHANGE_INDEX,
                score.getComposition().indexOfLine(line) + 1,
                line.getElementIndex(element) + 1
            )
        );

        if (tempoChange == null) {
            throw new IllegalStateException("no tempo change");
        }

        var resolvedTempoChange = tempoChange;
        tempoTypeCombo.setSelectedIndex(resolvedTempoChange.getTempoType().ordinal());
        tempoSpinner.setValue(resolvedTempoChange.getVisibleTempo());
        tempoDescriptionCombo.setSelectedItem(
            resolvedTempoChange.getTempoDescription()
        );
        showOnlyDescriptionCheckBox.setSelected(!resolvedTempoChange.shouldShowTempo());
        removeButton.setEnabled(!addingTempoChange);

        if (addingTempoChange) {
            okButton.setText(Strings.get(Strings.LABEL_BUTTON_ADD));
        } else {
            okButton.setText(Strings.get(Strings.LABEL_BUTTON_MODIFY));
        }

        return true;
    }

    @Override
    protected void setData() {
        if (selectedElement == null) {
            throw new IllegalStateException("no element selected");
        }

        selectedElement.setTempoChange(
            new Tempo(
                ((Integer) tempoSpinner.getValue()),
                (Tempo.Type) tempoTypeCombo.getSelectedItem(),
                (String) tempoDescriptionCombo.getSelectedItem(),
                !showOnlyDescriptionCheckBox.isSelected()
            )
        );
        getComposition().setModified(true);
    }
}
