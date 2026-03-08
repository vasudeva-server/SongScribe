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

import java.awt.*;

import songscribe.Strings;
import songscribe.data.PageLayoutData;
public class ExportPDFDialog extends StandardDialog {

    private final PaperSizeStep paperSizePanel;
    private final PageLayoutData paperSizePageLayoutDataPrivate;
    private PageLayoutData paperSizePageLayoutData = null;

    public ExportPDFDialog() {
        super(Strings.get(Strings.DIALOG_EXPORT_PDF_TITLE));
        paperSizePageLayoutDataPrivate = new PageLayoutData();
        paperSizePageLayoutDataPrivate.mainFrame = mainFrame;
        paperSizePanel = new PaperSizeStep(paperSizePageLayoutDataPrivate);
        paperSizePanel.setMirroredCheckInvisible();
        contentPanel.add(BorderLayout.CENTER, paperSizePanel);
        buttonPanel.remove(applyButton);
        contentPanel.add(BorderLayout.SOUTH, buttonPanel);
    }

    @Override
    protected void getData() {
        paperSizePanel.start();
    }

    @Override
    protected void setData() {
        paperSizePanel.end();
        paperSizePageLayoutData = paperSizePageLayoutDataPrivate;
    }

    public PageLayoutData getPaperSizeData() {
        return paperSizePageLayoutData;
    }
}
