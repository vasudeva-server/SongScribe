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

import javax.swing.*;

import songscribe.music.Note;
import songscribe.music.Tempo;
import songscribe.util.FileUtils;

public class TempoChangeDialog extends StandardDialog {

    private Note selectedNote = null;
    private final JLabel indexOfSelectedNoteLabel = new JLabel();
    private final JComboBox<?> tempoTypeCombo;
    private final SpinnerModel tempoSpinner = new SpinnerNumberModel(
        120,
        40,
        220,
        1
    );
    private final JComboBox<String> tempoDescriptionCombo = new JComboBox<>();
    private final JCheckBox showOnlyDescriptionCheckBox = new JCheckBox(
        "Show only the tempo description"
    );
    private final JButton removeButton;

    public TempoChangeDialog() {
        super("Tempo change");
        //----------------------center------------------------
        var center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        var large = new Dimension(0, 15);

        var infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        infoPanel.add(new JLabel("Index of selected note:"));
        infoPanel.add(indexOfSelectedNoteLabel);
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
        tempoPanel.add(new JLabel("Description:"));
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
        removeButton = new JButton("Remove");
        removeButton.addActionListener(_ -> {
            var score = mainFrame.getScore();
            selectedNote.setTempoChange(null);
            setVisible(false);
            score.repaint();
            score.getComposition().setModified(true);
        });
        south.add(okButton);
        south.add(applyButton);
        south.add(removeButton);
        south.add(cancelButton);
        contentPanel.add(BorderLayout.SOUTH, south);
    }

    @Override
    protected void getData() {
        var score = mainFrame.getScore();
        selectedNote = score.getSingleSelectedNote();

        // This should never happen, but make Java happy
        if (selectedNote == null) {
            return;
        }

        var tempoChange = selectedNote.getTempoChange();
        var addingTempoChange = tempoChange == null;

        if (addingTempoChange) {
            tempoChange = new Tempo(144, Tempo.Type.CROTCHET, "Slower", true);
        }

        indexOfSelectedNoteLabel.setText(
            (
                score.getComposition().indexOfLine(selectedNote.getLine()) + 1
            ) +
            ". line " +
            (selectedNote.getLine().getNoteIndex(selectedNote) + 1) +
            ". note"
        );

        tempoTypeCombo.setSelectedIndex(tempoChange.getTempoType().ordinal());
        tempoSpinner.setValue(tempoChange.getVisibleTempo());
        tempoDescriptionCombo.setSelectedItem(
            tempoChange.getTempoDescription()
        );
        showOnlyDescriptionCheckBox.setSelected(!tempoChange.shouldShowTempo());
        removeButton.setEnabled(!addingTempoChange);

        if (addingTempoChange) {
            okButton.setText("Add");
            applyButton.setText("Apply addition");
        } else {
            okButton.setText("Modify");
            applyButton.setText("Apply modification");
        }
    }

    @Override
    protected void setData() {
        selectedNote.setTempoChange(
            new Tempo(
                ((Integer) tempoSpinner.getValue()),
                (Tempo.Type) tempoTypeCombo.getSelectedItem(),
                (String) tempoDescriptionCombo.getSelectedItem(),
                !showOnlyDescriptionCheckBox.isSelected()
            )
        );
        mainFrame.getScore().getComposition().setModified(true);
    }
}
