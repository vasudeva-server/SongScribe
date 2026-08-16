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

import java.awt.BorderLayout;
import java.awt.Font;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.ui.component.MainFrame;
import songscribe.ui.dialog.fontchooser.FontChooser;
import songscribe.util.UIUtils;

public class FontDialog extends StandardDialog<FontChoice, Font> {

    private static final int EXTRA_PREVIEW_HEIGHT = 200;

    private final FontChooser chooser = new FontChooser();

    FontDialog(MainFrame mainFrame, DialogOps<FontChoice, Font> ops) {
        super(mainFrame, Strings.get(Strings.DIALOG_FONT_CHOOSER_TITLE), ops);

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

    public static Font showDialog(MainFrame mainFrame, Font initialFont) {
        var controller = new FontDialogController(mainFrame, initialFont);
        var dialog = new FontDialog(mainFrame, controller.ops());
        dialog.setVisible(true);
        return controller.getSelectedFont();
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
    protected void populate(FontChoice choice) {
        chooser.setSelectedFont(choice.font());
    }

    @Override
    protected Font gather() {
        return chooser.getSelectedFont();
    }
}
