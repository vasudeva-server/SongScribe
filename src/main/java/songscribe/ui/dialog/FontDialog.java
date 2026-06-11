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
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.ui.dialog.fontchooser.FontChooser;
import songscribe.util.UIUtils;

public class FontDialog extends StandardDialog {

    private static final int EXTRA_PREVIEW_HEIGHT = 200;

    // Widened to package-private for testing (FontDialogTest accesses it directly)
    final FontChooser chooser = new FontChooser();
    private Font selectedFont;

    public FontDialog(Font initialFont) {
        super(Strings.get(Strings.DIALOG_FONT_CHOOSER_TITLE));
        selectedFont = initialFont;

        // Padding applied here rather than via getContentPaddingKey() so the button
        // panel isn't indented by contentPanel's border insets, preventing extra side padding.
        chooser.setBorder(UIUtils.spacingBorder(FlatLafKey.DIALOG_STD_BUTTONS_PADDING));
        contentPanel.add(chooser, BorderLayout.CENTER);
    }

    @Override
    protected @Nullable FlatLafKey getContentPaddingKey() {
        return null;
    }

    @Override
    protected Object modifyButtonPanel() {
        return BorderLayout.PAGE_END;
    }

    public static Font showDialog(Component component) {
        var dialog = new FontDialog(component.getFont());
        dialog.setVisible(true);
        return dialog.selectedFont;
    }

    @Override
    protected boolean isResizable() {
        return true;
    }

    @Override
    protected int getExtraWidth() {
        return FlatLafProps.getInt(FlatLafKey.DIALOG_FONT_EXTRA_WIDTH);
    }

    @Override
    protected int getExtraHeight() {
        return EXTRA_PREVIEW_HEIGHT;
    }

    @Override
    protected boolean getData() {
        chooser.setSelectedFont(selectedFont);
        return super.getData();
    }

    @Override
    protected void setData() {
        selectedFont = chooser.getSelectedFont();
        super.setData();
    }

    public Font getSelectedFont() {
        return selectedFont;
    }
}
