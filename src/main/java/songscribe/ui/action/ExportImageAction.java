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

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;

import javax.swing.*;

import songscribe.data.MyFileFilter;
import songscribe.ui.Constants;
import songscribe.ui.component.MainFrame;
import songscribe.ui.dialog.PlatformFileDialog;
import songscribe.ui.dialog.ResolutionDialog;
import songscribe.util.FileUtils;
import songscribe.util.GraphicUtils;
import songscribe.util.Utils;

public class ExportImageAction extends UIAction {

    private final PlatformFileDialog fileDialog;
    private ResolutionDialog resolutionDialog = null;

    private final MyFileFilter[] myFileFilters = new MyFileFilter[] {
        new MyFileFilter("JPEG", "jpg"),
        new MyFileFilter("Portable Network Graphics", "png"),
    };

    public ExportImageAction() {
        super("Export as Image...", "export-image");
        var mainFrame = MainFrame.getInstance();
        fileDialog = new PlatformFileDialog(
            mainFrame,
            NAME,
            false,
            myFileFilters,
            Integer.parseInt(
                mainFrame
                    .getProperties()
                    .getProperty(Constants.IMAGE_EXPORT_FILTER_PROP)
            )
        );
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        var mainFrame = MainFrame.getInstance();
        var score = mainFrame.getScore();

        fileDialog.setFile(FileUtils.getSongFileNameForFileChooser(score));

        if (fileDialog.showDialog()) {
            var filter = fileDialog.getFileFilter();
            mainFrame
                .getProperties()
                .setProperty(
                    Constants.IMAGE_EXPORT_FILTER_PROP,
                    Integer.toString(Utils.arrayIndexOf(myFileFilters, filter))
                );
            var saveFile = fileDialog.getFile();
            var extension = filter.getExtension(0);

            if (
                !(saveFile.getName().toLowerCase().endsWith(extension) ||
                    (extension.equals("jpg") &&
                        saveFile.getName().toLowerCase().endsWith(".jpeg")))
            ) {
                saveFile = new File(
                    saveFile.getAbsolutePath() + '.' + extension
                );
            }

            if (saveFile.exists()) {
                var response = JOptionPane.showConfirmDialog(
                    mainFrame,
                    "The file “" +
                    saveFile.getName() +
                    "” already exists. Do you want to overwrite it?",
                    mainFrame.appName,
                    JOptionPane.YES_NO_OPTION
                );

                if (response == JOptionPane.NO_OPTION) {
                    return;
                }
            }

            if (resolutionDialog == null) {
                resolutionDialog = new ResolutionDialog(mainFrame);
            }

            resolutionDialog.setVisible(true);

            if (!resolutionDialog.isApproved()) {
                return;
            }

            var scale =
                (double) resolutionDialog.getResolution() /
                (double) GraphicUtils.getDpi();
            var composition = score.getComposition();
            var underLyrics = composition.getUnderLyrics();
            var transletedLyrics = composition.getTranslatedLyrics();
            var songTitle = composition.getTitle();

            if (resolutionDialog.isWithoutLyrics()) {
                composition.setUnderLyrics("");
                composition.setTranslatedLyrics("");
            }

            if (resolutionDialog.isWithoutTitle()) {
                composition.setTitle("");
            }

            try {
                var sheetImageForExport = score.createImageForExport(
                    Color.white,
                    scale,
                    resolutionDialog.getBorder()
                );
                var successful = GraphicUtils.writeImage(
                    sheetImageForExport,
                    extension,
                    saveFile
                );

                if (!successful) {
                    mainFrame.showErrorMessage(
                        "Could not export the image file."
                    );
                } else {
                    FileUtils.openExportFile(mainFrame, saveFile);
                }
            } catch (IOException e1) {
                mainFrame.showErrorMessage(MainFrame.COULD_NOT_SAVE_MESSAGE);
            } catch (OutOfMemoryError e1) {
                mainFrame.showErrorMessage(
                    "There is not enough memory for this resolution."
                );
            } finally {
                if (resolutionDialog.isWithoutLyrics()) {
                    composition.setUnderLyrics(underLyrics);
                    composition.setTranslatedLyrics(transletedLyrics);
                }
                if (resolutionDialog.isWithoutTitle()) {
                    composition.setTitle(songTitle);
                }
                score.repaint();
            }
        }
    }
}
