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
package songscribe.ui.action;

import module java.desktop;


import songscribe.Strings;
import songscribe.file.MyFileFilter;
import songscribe.ui.dialog.PlatformFileDialog;
import songscribe.file.FileUtils;

public class ExportSVGAction extends UIAction {

    private final PlatformFileDialog fileDialog;

    public static ExportSVGAction createAction() {
        return new ExportSVGAction();
    }

    private ExportSVGAction() {
        super(Strings.get(Strings.ACTION_EXPORT_SVG), "export-svg");
        setFlags(Flag.OPENS_DIALOG);
        fileDialog = new PlatformFileDialog(
            getMainFrame(),
            NAME,
            false,
            new MyFileFilter("Portable Document Format", "svg")
        );
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        var score = requireScore();
        var saveFile = FileUtils.showExportDialog(score, fileDialog, "svg");

        if (saveFile == null) {
            return;
        }

        score.createSVG(saveFile);
    }
}
