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

import songscribe.Strings;
import songscribe.ui.component.MyJTextArea;

public class PreviewPane extends JScrollPane {

    private final JTextArea previewText = new MyJTextArea();

    public PreviewPane() {
        previewText.setText(Strings.get(Strings.LABEL_FONT_PREVIEW_TEXT));
        previewText.setMargin(new Insets(5, 5, 5, 5));
        setPreferredSize(new Dimension(200, 80));
        setViewportView(previewText);
    }

    public void setPreviewFont(Font font) {
        previewText.setFont(font);
    }
}
