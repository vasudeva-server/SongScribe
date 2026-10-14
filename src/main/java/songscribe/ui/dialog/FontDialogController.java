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

import java.awt.Font;

import songscribe.ui.component.MainFrame;

/**
 * The controller behind {@link FontDialog}. Commits to itself rather than to any document;
 * {@link FontDialog#showDialog} reads {@link #getSelectedFont()} once the dialog closes.
 *
 * <p>Constructed per gesture and discarded with the dialog.
 */
final class FontDialogController extends DialogController<FontChoice, Font> {

    private Font selectedFont;

    FontDialogController(Font initialFont) {
        selectedFont = initialFont;
    }

    @Override
    protected FontChoice read() {
        return new FontChoice(selectedFont);
    }

    @Override
    protected void commit(Font font) {
        selectedFont = font;
    }

    /**
     * @return the font this controller was constructed with, unless OK was pressed
     */
    Font getSelectedFont() {
        return selectedFont;
    }
}
