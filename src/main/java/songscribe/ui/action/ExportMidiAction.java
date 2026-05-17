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
import songscribe.ui.dialog.ExportMidiDialog;
import songscribe.ui.dialog.PlatformFileDialog;

public final class ExportMidiAction extends UIAction {

    public static ExportMidiAction createAction() {
        return new ExportMidiAction();
    }

    private ExportMidiAction() {
        super(Strings.get(Strings.ACTION_EXPORT_MIDI), "export-midi");
        setFlags(Flag.OPENS_DIALOG);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        var scoreView = requireScoreView();
        var saveFile = PlatformFileDialog.showSaveDialog(
            getMainFrame(),
            "Export MIDI",
            Strings.get(Strings.FILTER_MIDI),
            scoreView.getSuggestedFileName(),
            "mid"
        );

        if (saveFile == null) {
            return;
        }

        new ExportMidiDialog(saveFile).setVisible(true);
    }
}
