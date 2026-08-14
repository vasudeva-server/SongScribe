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


import songscribe.Strings;
import songscribe.message.notification.KeySignatureDidChangeNotification;
import songscribe.dom.KeyType;
import songscribe.ui.component.MainFrame;

public class KeySignatureChangeDialog extends StandardDialog {

    final JLabel indexOfSelectedElementLabel = new JLabel();
    final JComboBox<KeyType> keysCombo;
    final SpinnerModel keysSpinner = new SpinnerNumberModel(4, 0, 7, 1);

    public KeySignatureChangeDialog(MainFrame mainFrame) {
        super(mainFrame, Strings.get(Strings.DIALOG_KEY_SIGNATURE_CHANGE_TITLE));
        var center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        var large = new Dimension(0, 15);

        var infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        infoPanel.add(new JLabel(Strings.get(Strings.LABEL_KEYSIG_SELECTED_LINE)));
        infoPanel.add(indexOfSelectedElementLabel);
        infoPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(infoPanel);
        center.add(Box.createRigidArea(large));

        var keysPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        keysCombo = new JComboBox<>(
            new KeyType[]{KeyType.FLATS, KeyType.SHARPS}
        );
        keysPanel.add(keysCombo);

        var spinner = new JSpinner(keysSpinner);
        keysPanel.add(spinner);

        keysPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(keysPanel);
        contentPanel.add(center);

        var south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 13, 0));
        south.add(okButton);
        south.add(cancelButton);
        contentPanel.add(BorderLayout.SOUTH, south);
    }

    @Override
    protected boolean getData() {
        var score = getMainFrame().requireScoreView();
        var line = score.getSong().getLine(score.getSelectedLine());
        indexOfSelectedElementLabel.setText(
            Integer.toString(score.getSong().indexOfLine(line) + 1)
        );
        keysCombo.setSelectedItem(line.getKeyType());
        keysSpinner.setValue(line.getKeyAccidentalCount());
        return true;
    }

    @Override
    protected boolean commitOnOk() {
        var keyType = (KeyType) keysCombo.getSelectedItem();

        if (keyType == null) {
            return true;
        }

        var score = getMainFrame().requireScoreView();
        score.getSong().postWithModification(
            Strings.get(Strings.ACTION_EDIT_OP_CHANGE_KEY),
            new KeySignatureDidChangeNotification(
                score.getSelectedLine(),
                keyType,
                (Integer) keysSpinner.getValue()
            ));
        return true;
    }
}
