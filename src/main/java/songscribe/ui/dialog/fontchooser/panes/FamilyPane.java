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

package songscribe.ui.dialog.fontchooser.panes;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionListener;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.icons.FlatSearchIcon;

import songscribe.ui.component.MyJTextField;
import songscribe.ui.dialog.fontchooser.model.FamilyListModel;

public class FamilyPane extends JPanel {

    private final JList<String> familyList = new JList<>();

    private final SearchListener searchListener;

    public FamilyPane() {
        var familyListModel = new FamilyListModel();
        searchListener = new SearchListener(familyListModel, this);

        initializeList(familyListModel);
        setMinimumSize(new Dimension(80, 100));
        setPreferredSize(new Dimension(240, 160));

        setLayout(new GridBagLayout());
        addSearchField();
        addScrollPane();
    }

    private void initializeList(ListModel<String> familyListModel) {
        familyList.setModel(familyListModel);
        familyList
            .getSelectionModel()
            .setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    }

    private void addSearchField() {
        var gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new Insets(0, 0, 5, 0);
        gridBagConstraints.weightx = 1.0;

        var searchField = new MyJTextField();
        searchField.setMargin(new Insets(4, 4, 4, 4));
        searchField.putClientProperty(
            FlatClientProperties.PLACEHOLDER_TEXT,
            "Search"
        );
        searchField.putClientProperty(
            FlatClientProperties.TEXT_FIELD_LEADING_ICON,
            new FlatSearchIcon()
        );
        searchField.requestFocus();
        searchField.addKeyListener(searchListener);
        add(searchField, gridBagConstraints);
    }

    private void addScrollPane() {
        var gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.fill = GridBagConstraints.BOTH;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        add(new JScrollPane(familyList), gridBagConstraints);
    }

    public void setSelectedFamily(String family) {
        familyList.setSelectedValue(family, false);
        SwingUtilities.invokeLater(() -> {
            var index = familyList.getSelectedIndex();

            if (index >= 0) {
                familyList.ensureIndexIsVisible(index);
            }
        });
    }

    public void addListSelectionListener(ListSelectionListener listener) {
        familyList.addListSelectionListener(listener);
    }

    public void removeListSelectionListener(ListSelectionListener listener) {
        familyList.removeListSelectionListener(listener);
    }

    public String getSelectedFamily() {
        return familyList.getSelectedValue();
    }
}
