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
 * The controller behind {@link FontDialog}. Commits to itself rather than to any document: the
 * chosen font becomes what {@link #getSelectedFont()} answers, for {@link FontDialog#showDialog}
 * to read once the dialog closes. Cancel leaves the font this controller was constructed with.
 *
 * <p>Constructed per gesture, by {@link FontDialog#showDialog}, and discarded with the dialog.
 */
final class FontDialogController extends DialogController<Font, Font> {

    private Font selectedFont;

    FontDialogController(MainFrame mainFrame, Font initialFont) {
        super(mainFrame);
        selectedFont = initialFont;
    }

    @Override
    protected Font read() {
        return selectedFont;
    }

    @Override
    protected void commit(Font font) {
        selectedFont = font;
    }

    /**
     * @return the font OK was pressed on, or the font this controller was constructed with when
     *         it was cancelled or the dialog is still open
     */
    Font getSelectedFont() {
        return selectedFont;
    }
}
