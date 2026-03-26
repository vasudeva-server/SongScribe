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

import module java.desktop;

import songscribe.ui.component.InputUtils;

public class SizePane extends JPanel {

    private final JList<Integer> sizeList = new JList<>();

    private final JSpinner sizeSpinner = new JSpinner();

    private final DefaultListModel<Integer> sizeListModel =
        new DefaultListModel<>();

    public SizePane() {
        setLayout(new GridBagLayout());
        initSizeListModel();
        initSizeList();
        initSizeSpinner();
        addSizeSpinner();
        addSizeScrollPane();
    }

    private void addSizeScrollPane() {
        var sizeScrollPane = new JScrollPane();
        sizeScrollPane.setMinimumSize(new Dimension(70, 100));
        sizeScrollPane.setPreferredSize(new Dimension(80, 100));
        sizeScrollPane.setViewportView(sizeList);
        var gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = GridBagConstraints.VERTICAL;
        gridBagConstraints.anchor = GridBagConstraints.LINE_START;
        gridBagConstraints.weighty = 1.0;
        add(sizeScrollPane, gridBagConstraints);
    }

    private void addSizeSpinner() {
        var gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = GridBagConstraints.VERTICAL;
        gridBagConstraints.anchor = GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new Insets(0, 0, 6, 0);
        add(sizeSpinner, gridBagConstraints);
    }

    private void initSizeSpinner() {
        var spinnerHeight = (int) sizeSpinner.getPreferredSize().getHeight();
        sizeSpinner.setPreferredSize(new Dimension(80, spinnerHeight));
        sizeSpinner.setModel(new SpinnerNumberModel(12, 6, 128, 1));
        setupSpinnerEditor(sizeSpinner);
        sizeSpinner.addChangeListener(event -> {
            var value = (Integer) sizeSpinner.getValue();
            var index =
                ((DefaultListModel<Integer>) sizeList.getModel()).indexOf(
                        value
                    );

            if (index > -1) {
                sizeList.setSelectedValue(value, true);
            } else {
                sizeList.clearSelection();
            }
        });
    }

    private static void setupSpinnerEditor(JSpinner spinner) {
        var editor = (JSpinner.DefaultEditor) spinner.getEditor();
        var textField = editor.getTextField();
        var border = new JScrollPane().getBorder();

        if ((border != null) && !(border instanceof UIResource)) {
            textField.setBorder(border);
        }

        InputUtils.addNumericFilter(spinner);
    }

    private void initSizeList() {
        sizeList.setModel(sizeListModel);
        sizeList
            .getSelectionModel()
            .setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sizeList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                var index =
                    ((DefaultListModel<Integer>) sizeList.getModel()).indexOf(
                            sizeList.getSelectedValue()
                        );

                if (index >= 0) {
                    sizeSpinner.setValue(sizeList.getSelectedValue());
                }
            }
        });

        var renderer = (DefaultListCellRenderer) sizeList.getCellRenderer();
        renderer.setHorizontalAlignment(SwingConstants.RIGHT);
    }

    private void initSizeListModel() {
        var size = 6;
        var step = 1;
        var ceil = 14;

        do {
            sizeListModel.addElement(size);

            if (size == ceil) {
                ceil += ceil;
                step += step;
            }

            size += step;
        } while (size <= 128);
    }

    @Override
    public void updateUI() {
        super.updateUI();

        if (sizeSpinner != null) {
            setupSpinnerEditor(sizeSpinner);
        }
    }

    public void addListSelectionListener(ListSelectionListener listener) {
        sizeList.addListSelectionListener(listener);
    }

    public void removeListSelectionListener(ListSelectionListener listener) {
        sizeList.removeListSelectionListener(listener);
    }

    public void setSelectedSize(int size) {
        if (sizeListModel.contains(size)) {
            sizeList.setSelectedValue(size, true);
        }

        sizeSpinner.setValue(size);
    }

    public int getSelectedSize() {
        if (!sizeList.isSelectionEmpty()) {
            return sizeList.getSelectedValue();
        }

        return (Integer) sizeSpinner.getValue();
    }
}
