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
package songscribe.uiconverter;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Properties;
import java.util.zip.ZipOutputStream;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiSystem;
import javax.swing.*;

import org.jetbrains.annotations.Nullable;

import songscribe.io.CompositionIO;
import songscribe.ui.Constants;
import songscribe.ui.component.MyBorder;
import songscribe.ui.dialog.ProcessDialog;
import songscribe.ui.playback.PlaybackController;
import songscribe.util.FileUtils;
import songscribe.util.GraphicUtils;

public class ConvertAction extends AbstractAction {

    public static final int[] IMAGE_WIDTH = new int[] { /*640, */2240 };
    public static final int[] LEFT_RIGHT_MARGIN = new int[] { /*13, */39 };
    public static final String[] IMAGE_NAME_POSIX = new String[] {
        /*"-s", */"-l",
    };
    private static final boolean CREATE_ZIP = false;
    private final UIConverter uiConverter;
    private final JTextField songsDirectory;

    public ConvertAction(UIConverter uiConverter, JTextField songsDirectory) {
        this.uiConverter = uiConverter;
        this.songsDirectory = songsDirectory;
        putValue(NAME, "Convert");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (songsDirectory.getText().isEmpty()) {
            uiConverter.showErrorMessage("You need to select a folder first.");
            return;
        }

        var songDirectoryFile = new File(songsDirectory.getText());

        if (!songDirectoryFile.exists()) {
            uiConverter.showErrorMessage("The selected folder does not exist.");
            return;
        }

        var songFiles = songDirectoryFile.listFiles(
            (dir, name) -> uiConverter.isLegalFileName(name)
        );

        if ((songFiles == null) || (songFiles.length == 0)) {
            uiConverter.showErrorMessage("No files in this folder to convert.");
            return;
        }

        var pd = new ProcessDialog(
            uiConverter,
            "Converting...",
            songFiles.length * (3 + (IMAGE_WIDTH.length * 2))
        );
        pd.packAndPos();
        new ConvertThread(songDirectoryFile, songFiles, pd).start();
        pd.setVisible(true);
    }

    private final class ConvertThread extends Thread {

        private static final String IMAGETYPE = "PNG";
        private final File songDirectory;
        private final File[] songFiles;
        private final ProcessDialog processDialog;

        private ConvertThread(
            File songDirectory,
            File[] songFiles,
            ProcessDialog processDialog
        ) {
            this.songDirectory = songDirectory;
            this.songFiles = songFiles;
            this.processDialog = processDialog;
        }

        @Override
        public void run() {
            var myBorders = new MyBorder[LEFT_RIGHT_MARGIN.length];

            for (var i = 0; i < LEFT_RIGHT_MARGIN.length; i++) {
                myBorders[i] = new MyBorder();
                myBorders[i].setLeft(LEFT_RIGHT_MARGIN[i]);
                myBorders[i].setRight(LEFT_RIGHT_MARGIN[i]);
            }

            var props = new Properties(uiConverter.getProperties());
            props.setProperty(
                Constants.WITH_REPEAT_PROP,
                Constants.FALSE_VALUE
            );
            props.setProperty(Constants.INSTRUMENT_PROP, Integer.toString(0));
            props.setProperty(
                Constants.TEMPO_CHANGE_PROP,
                Integer.toString(100)
            );

            File zipFile = null;

            if (CREATE_ZIP) {
                zipFile = new File(
                    songDirectory.getParentFile(),
                    songDirectory.getName() + ".zip"
                );
            }

            try (
                var fileOutputStream = CREATE_ZIP
                    ? new FileOutputStream(zipFile)
                    : null;
                var zos = CREATE_ZIP
                    ? new ZipOutputStream(fileOutputStream)
                    : null
            ) {
                byte[] buf = null;

                if (CREATE_ZIP) {
                    buf = new byte[1024];
                }

                for (var songFile : songFiles) {
                    // load file
                    var score = uiConverter.getScore();
                    score.setComposition(null);
                    score.openFile(uiConverter, songFile, false);
                    var composition = score.getComposition();

                    // ensure we have the latest format by writing the mssw file again
                    var tempMsswSong = File.createTempFile(
                        "mssw_uiconvert",
                        ".mssw"
                    );
                    var tempMsswSongPrintWriter = new PrintWriter(
                        new FileWriter(tempMsswSong)
                    );
                    CompositionIO.writeComposition(
                        composition,
                        tempMsswSongPrintWriter
                    );
                    tempMsswSongPrintWriter.close();

                    if (zos != null) {
                        FileUtils.zipFile(
                            zos,
                            tempMsswSong,
                            songFile.getName(),
                            buf
                        );
                    }

                    tempMsswSong.delete();
                    processDialog.nextValue();

                    // produce images
                    composition.setUnderLyrics("");
                    composition.setTranslatedLyrics("");
                    composition.setTitle("");
                    composition.setAttribution("");

                    var fileName = songFile.getName();
                    var dotPos = fileName.lastIndexOf('.');

                    if (dotPos > 0) {
                        fileName = fileName.substring(0, dotPos);
                    }

                    for (var i = 0; i < IMAGE_WIDTH.length; i++) {
                        var scale =
                            (double) (IMAGE_WIDTH[i] -
                                (2 * LEFT_RIGHT_MARGIN[i])) /
                            score.getSheetWidth();
                        var image = score.createImageForExport(
                            Color.WHITE,
                            scale,
                            myBorders[i]
                        );
                        @Nullable
                        var imageFile = new File(
                            songDirectory,
                            fileName +
                            IMAGE_NAME_POSIX[i] +
                            '.' +
                            IMAGETYPE.toLowerCase()
                        );
                        try {
                            GraphicUtils.writeImage(
                                image,
                                IMAGETYPE,
                                imageFile
                            );
                        } catch (Exception e) {
                            imageFile = null;
                            uiConverter.showErrorMessage(
                                "Could not convert image for " + songFile.getName()
                            );
                        } finally {
                            processDialog.nextValue();
                        }

                        if ((imageFile != null) && (zos != null)) {
                            FileUtils.zipFile(zos, imageFile, null, buf);
                        }

                        processDialog.nextValue();
                    }

                    // produce MIDI
                    score.musicDidChange(props);
                    @Nullable
                    var midiFile = new File(
                        songDirectory,
                        fileName + ".mid"
                    );

                    try {
                        var sequence = PlaybackController.buildSequence(score.getComposition());
                        MidiSystem.write(sequence, 1, midiFile);
                    } catch (IOException | InvalidMidiDataException e) {
                        midiFile = null;
                        uiConverter.showErrorMessage(
                            "Could not convert MIDI for " + songFile.getName()
                        );
                    } finally {
                        processDialog.nextValue();
                    }

                    if ((midiFile != null) && (zos != null)) {
                        FileUtils.zipFile(zos, midiFile, null, buf);
                    }

                    processDialog.nextValue();
                }

                JOptionPane.showMessageDialog(
                    processDialog,
                    "Conversion complete!"
                );
                //Utilities.openWebPage(uiConverter, uiConverter.getProperties().getProperty
                // (Constants
                // .BOOK_UPLOAD_URL));
            } catch (IOException e) {
                uiConverter.showErrorMessage("Error while producing ZIP file.");
            } finally {
                processDialog.setVisible(false);
            }
        }
    }
}
