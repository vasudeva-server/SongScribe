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

import java.io.IOException;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.ui.Dialogs;
import songscribe.util.Utils;

public class HelpDialog
    extends StandardDialog
    implements ListCellRenderer<Object>, ListSelectionListener {

    private final DefaultListModel<ListObject> defaultListModel =
        new DefaultListModel<>();
    private final JEditorPane editorPane = new JEditorPane();

    public HelpDialog(String title) {
        this(title, true);
    }

    public HelpDialog(String title, boolean isModal) {
        super(title, isModal);
        var leftList = new JList<>(defaultListModel);
        leftList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        leftList.setCellRenderer(this);
        leftList.addListSelectionListener(this);
        var splitPane = new JSplitPane();
        splitPane.setLeftComponent(new JScrollPane(leftList));

        editorPane.setEditable(false);
        editorPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        var editorScrollPane = new JScrollPane(editorPane);
        editorScrollPane.setPreferredSize(new Dimension(600, 500));
        editorScrollPane.setMinimumSize(new Dimension(100, 100));
        splitPane.setRightComponent(editorScrollPane);

        contentPanel.add(splitPane);

        buttonPanel.remove(applyButton);
        buttonPanel.remove(cancelButton);
        contentPanel.add(BorderLayout.SOUTH, buttonPanel);
    }

    public void addToList(String name, String html) {
        var listObject = new ListObject(name, html);
        defaultListModel.addElement(listObject);
    }

    @Override
    protected void getData() {}

    @Override
    protected void setData() {}

    @Override
    public Component getListCellRendererComponent(
        JList list,
        Object value,
        int index,
        boolean isSelected,
        boolean cellHasFocus
    ) {
        var lo = (ListObject) value;

        if (lo.component == null) {
            lo.component = new JPanel();
            var label = new JLabel(lo.name);
            label.setFont(new Font(Font.SERIF, Font.PLAIN, 20));
            label.setPreferredSize(new Dimension(200, 30));
            ((JPanel) lo.component).add(label);
        }

        if (isSelected) {
            lo.component.setBackground(list.getSelectionBackground());
            ((JPanel) lo.component).getComponent(0).setForeground(list.getSelectionForeground());
        } else {
            lo.component.setBackground(list.getBackground());
            ((JPanel) lo.component).getComponent(0).setForeground(list.getForeground());
        }

        var border = cellHasFocus
            ? UIManager.getBorder("List.focusCellHighlightBorder")
            : null;
        ((JComponent) lo.component).setBorder(border);

        return lo.component;
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
        var theList = (JList<?>) e.getSource();

        if (theList.isSelectionEmpty()) {
            editorPane.setText(null);
        } else {
            try {
                editorPane.setPage(
                    "file:" +
                    Utils.getResourcePath(
                        "/help/" +
                        ((ListObject) theList.getSelectedValue()).html
                    )
                );
            } catch (IOException e1) {
                Dialogs.showErrorMessage(
                    getMainFrame(),
                    Strings.get(Strings.DIALOG_TITLE_HELP_ERROR),
                    Strings.get(Strings.ERROR_HELP_OPEN)
                );
            }
        }
    }

    private static class ListObject {

        final String name;
        final String html;
        @Nullable Component component = null;

        ListObject(String name, String html) {
            this.name = name;
            this.html = html;
        }
    }
}
