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

import songscribe.music.KeyType;

public class KeySignatureChangeDialog extends StandardDialog {

    private final JLabel indexOfSelectedElementLabel = new JLabel();
    private final JComboBox<KeyType> keysCombo;
    private final SpinnerModel keysSpinner = new SpinnerNumberModel(4, 0, 7, 1);

    public KeySignatureChangeDialog() {
        super("Key Signature Change");
        var center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        var large = new Dimension(0, 15);

        var infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        infoPanel.add(new JLabel("Index of selected line:"));
        infoPanel.add(indexOfSelectedElementLabel);
        infoPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(infoPanel);
        center.add(Box.createRigidArea(large));

        var keysPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        keysCombo = new JComboBox<>(
            new KeyType[]{KeyType.FLATS, KeyType.SHARPS}
        );
        keysCombo.setRenderer(new CompositionSettingsDialog.KeyCellRenderer());
        keysPanel.add(keysCombo);

        var spinner = new JSpinner(keysSpinner);
        keysPanel.add(spinner);

        keysPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(keysPanel);
        contentPanel.add(center);

        var south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 13, 0));
        south.add(okButton);
        south.add(applyButton);
        south.add(cancelButton);
        contentPanel.add(BorderLayout.SOUTH, south);
    }

    @Override
    protected void getData() {
        var score = mainFrame.getScore();
        var line = score.getComposition().getLine(score.getSelectedLine());
        indexOfSelectedElementLabel.setText(
            Integer.toString(score.getComposition().indexOfLine(line) + 1)
        );
        keysCombo.setSelectedItem(line.getKeyType());
        keysSpinner.setValue(line.getKeyAccidentalCount());
    }

    @Override
    protected void setData() {
        var score = mainFrame.getScore();
        var line = score.getComposition().getLine(score.getSelectedLine());
        line.setKeyType((KeyType) keysCombo.getSelectedItem());
        line.setKeyAccidentalCount((Integer) keysSpinner.getValue());
    }
}
