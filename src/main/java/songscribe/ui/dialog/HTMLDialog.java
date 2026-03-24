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

import songscribe.Strings;
import songscribe.ui.OptionDialogs;
import songscribe.util.Utils;

public class HTMLDialog extends StandardDialog {

    private final JEditorPane editorPane;

    public HTMLDialog(String title) {
        super(title, false, DialogCategory.INFORMATIONAL);
        buttonPanel = new JPanel();
        buttonPanel.add(okButton);
        contentPanel.add(BorderLayout.SOUTH, buttonPanel);

        editorPane = new JEditorPane();
        editorPane.setEditable(false);
        editorPane.setBackground(contentPanel.getBackground());

        // Put the editor pane in a scroll pane.
        var editorScrollPane = new JScrollPane(editorPane);
        editorScrollPane.setPreferredSize(new Dimension(500, 500));
        editorScrollPane.setMinimumSize(new Dimension(100, 100));
        contentPanel.add(BorderLayout.CENTER, editorScrollPane);
    }

    public HTMLDialog(String dialogTitle, String htmlPage) {
        this(dialogTitle);
        setPage(htmlPage);
    }

    protected void setPage(String htmlPage) {
        try {
            editorPane.setPage(
                "file:" + Utils.getResourcePath("help/" + htmlPage)
            );
        } catch (IOException e) {
            OptionDialogs.showErrorMessage(
                getMainFrame(),
                Strings.get(Strings.ALERT_TITLE_HELP_ERROR),
                Strings.get(Strings.ERROR_HELP_OPEN)
            );
        }
    }

    @Override
    protected boolean getData() { return true; }

    @Override
    protected void setData() {}
}
