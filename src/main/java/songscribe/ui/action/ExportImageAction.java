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

import java.io.IOException;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.util.ExtensionFileFilter;
import songscribe.export.ExportOptions;
import songscribe.ui.OptionDialogs;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.ui.dialog.PlatformFileDialog;
import songscribe.ui.dialog.ResolutionDialog;
import songscribe.export.ExportUtils;
import songscribe.util.FileUtils;
import songscribe.util.GraphicUtils;
import songscribe.util.Utils;

public final class ExportImageAction extends UIAction {

    private final PlatformFileDialog fileDialog;
    private @Nullable ResolutionDialog resolutionDialog = null;

    private final ExtensionFileFilter[] myFileFilters = new ExtensionFileFilter[] {
        new ExtensionFileFilter(Strings.get(Strings.FILTER_JPEG), "jpg"),
        new ExtensionFileFilter(Strings.get(Strings.FILTER_PNG), "png"),
    };

    public static ExportImageAction createAction() {
        return new ExportImageAction();
    }

    private ExportImageAction() {
        super(Strings.get(Strings.ACTION_EXPORT_IMAGE), "export-image");
        setFlags(Flag.OPENS_DIALOG);
        fileDialog = new PlatformFileDialog(
            getMainFrame(),
            NAME,
            false,
            myFileFilters,
            Prefs.getInstance().getInt(PrefsKey.IMAGE_EXPORT_FILTER)
        );
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        var score = requireScore();

        fileDialog.setFile(score.getSuggestedFileName());

        if (fileDialog.showDialog()) {
            var filter = fileDialog.getFileFilter();

            if (filter == null) {
                return;
            }

            Prefs.getInstance().put(
                PrefsKey.IMAGE_EXPORT_FILTER,
                Utils.arrayIndexOf(myFileFilters, filter)
            );
            var saveFile = fileDialog.getFile();
            var extension = filter.getExtension(0);

            if (extension.equals("jpg")) {
                saveFile = FileUtils.ensureExtension(saveFile, "jpg", "jpeg");
            } else {
                saveFile = FileUtils.ensureExtension(saveFile, extension);
            }

            if (resolutionDialog == null) {
                resolutionDialog = new ResolutionDialog();
            }

            resolutionDialog.setVisible(true);

            if (!resolutionDialog.isApproved()) {
                return;
            }

            var scale =
                (double) resolutionDialog.getResolution() /
                (double) GraphicUtils.getDpi();
            var options = new ExportOptions(
                !resolutionDialog.isWithoutLyrics(),
                !resolutionDialog.isWithoutTitle(),
                true
            );

            try {
                var sheetImageForExport = score.createImageForExport(
                    Color.white,
                    scale,
                    resolutionDialog.getBorder(),
                    options
                );
                var successful = GraphicUtils.writeImage(
                    sheetImageForExport,
                    extension,
                    saveFile
                );

                if (!successful) {
                    OptionDialogs.showErrorMessage(
                        null,
                        Strings.ALERT_TITLE_EXPORT_ERROR,
                        Strings.ERROR_IMAGE_EXPORT
                    );
                } else {
                    ExportUtils.openExportedFile(saveFile);
                }
            } catch (IOException e1) {
                OptionDialogs.showErrorMessage(
                    null,
                    Strings.ALERT_TITLE_EXPORT_ERROR,
                    Strings.ERROR_FILE_SAVE
                );
            } catch (OutOfMemoryError e1) {
                OptionDialogs.showErrorMessage(
                    null,
                    Strings.ALERT_TITLE_EXPORT_ERROR,
                    Strings.ERROR_IMAGE_MEMORY
                );
            }
        }
    }
}
