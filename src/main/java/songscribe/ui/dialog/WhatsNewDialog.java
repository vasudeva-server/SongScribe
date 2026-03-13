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
import songscribe.Version;
import songscribe.util.Utils;

public class WhatsNewDialog extends StandardDialog {

    public static final String WHATS_NEW_FILE = Utils.getResourcePath(
        "help/release-notes-" + Version.PUBLIC_VERSION + ".html"
    );
    private boolean noReleaseNotes = false;

    public WhatsNewDialog() {
        super(Strings.get(Strings.DIALOG_WHATS_NEW_TITLE));
        try {
            contentPanel.add(
                BorderLayout.CENTER,
                createTextPane("file:" + WHATS_NEW_FILE)
            );
        } catch (IOException e) {
            noReleaseNotes = true;
        }

        var southPanel = new JPanel();
        southPanel.add(okButton);
        contentPanel.add(BorderLayout.SOUTH, southPanel);
    }

    private static Component createTextPane(String url) throws IOException {
        var textPane = new JTextPane();
        textPane.setPage(url);
        textPane.setEditable(false);
        var textScroll = new JScrollPane(textPane);
        textScroll.setPreferredSize(new Dimension(500, 300));
        return textScroll;
    }

    @Override
    protected void getData() throws DoNotShowException {
        if (noReleaseNotes) {
            throw new DoNotShowException();
        }
    }

    @Override
    protected void setData() {}
}
