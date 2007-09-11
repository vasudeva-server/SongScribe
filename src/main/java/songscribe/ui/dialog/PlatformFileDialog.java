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
import java.io.File;

import javax.swing.*;
import javax.swing.plaf.basic.*;

import com.formdev.flatlaf.util.SystemInfo;
import com.jtechlabs.ui.widget.directorychooser.DirectoryChooserDefaults;
import com.jtechlabs.ui.widget.directorychooser.JDirectoryChooser;

import songscribe.data.MyFileFilter;
import songscribe.ui.component.MainFrame;

public class PlatformFileDialog {

    private JFileChooser chooser = null;
    private FileDialog dialog = null;
    private File selectedDirectory = null;
    private final MainFrame mainFrame;
    private final boolean isOpenDialog;
    private final boolean directoriesOnly;
    private MyFileFilter[] filters = null;
    private int initialFilterIndex = 0;

    public PlatformFileDialog(
        MainFrame mainFrame,
        String title,
        boolean isOpenDialog,
        MyFileFilter filter
    ) {
        this(mainFrame, title, isOpenDialog, false);
        setFileFiler(filter);
    }

    public PlatformFileDialog(
        MainFrame mainFrame,
        String title,
        boolean isOpenDialog,
        MyFileFilter filter,
        boolean directoriesOnly
    ) {
        this(mainFrame, title, isOpenDialog, directoriesOnly);
        setFileFiler(filter);
    }

    public PlatformFileDialog(
        MainFrame mainFrame,
        String title,
        boolean isOpenDialog,
        MyFileFilter[] filters,
        int initialFilterIndex
    ) {
        this(mainFrame, title, isOpenDialog, false);
        this.filters = filters;
        this.initialFilterIndex = initialFilterIndex;

        if (!SystemInfo.isMacOS) {
            for (var maf : filters) {
                chooser.addChoosableFileFilter(maf);
            }

            chooser.setAcceptAllFileFilterUsed(false);
            chooser.setFileFilter(filters[initialFilterIndex]);
        }
    }

    private PlatformFileDialog(
        MainFrame mainFrame,
        String title,
        boolean isOpenDialog,
        boolean directoriesOnly
    ) {
        this.mainFrame = mainFrame;
        this.isOpenDialog = isOpenDialog;
        this.directoriesOnly = directoriesOnly;

        if (SystemInfo.isMacOS) {
            dialog = new FileDialog(
                mainFrame,
                title,
                isOpenDialog ? FileDialog.LOAD : FileDialog.SAVE
            );
        } else {
            if (!directoriesOnly) {
                chooser = new JFileChooser();
                chooser.setDialogTitle(title);
            } else {
                DirectoryChooserDefaults.putOption(
                    DirectoryChooserDefaults.PROP_DIALOG_TEXT,
                    title
                );
                DirectoryChooserDefaults.putOption(
                    DirectoryChooserDefaults.PROP_ACCESS,
                    JDirectoryChooser.ACCESS_NEW |
                    JDirectoryChooser.ACCESS_RENAME
                );
            }
        }
    }

    public void setFileFiler(MyFileFilter maf) {
        if (SystemInfo.isMacOS) {
            dialog.setFilenameFilter(maf);
        } else {
            if (!directoriesOnly) {
                chooser.setFileFilter(maf);
            }
        }
    }

    public MyFileFilter getFileFilter() {
        if (SystemInfo.isMacOS) {
            return (MyFileFilter) dialog.getFilenameFilter();
        }

        return (MyFileFilter) chooser.getFileFilter();
    }

    public boolean showDialog() {
        if (SystemInfo.isMacOS) {
            if (filters != null) {
                var answer = JOptionPane.showOptionDialog(
                    mainFrame,
                    "Select the file type",
                    mainFrame.appName,
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    filters,
                    filters[initialFilterIndex]
                );

                if (answer == JOptionPane.CLOSED_OPTION) {
                    return false;
                }

                setFileFiler(filters[answer]);
            }

            dialog.setDirectory(
                mainFrame.getRecentFileDirectory().getAbsolutePath()
            );

            if (directoriesOnly) {
                System.setProperty(
                    "apple.awt.fileDialogForDirectories",
                    "true"
                );
            }

            dialog.setVisible(true);

            if (directoriesOnly) {
                System.setProperty(
                    "apple.awt.fileDialogForDirectories",
                    "false"
                );
            }

            return dialog.getFile() != null;
        }

        if (!directoriesOnly) {
            chooser.setCurrentDirectory(mainFrame.getRecentFileDirectory());
            var result = isOpenDialog
                ? chooser.showOpenDialog(mainFrame)
                : chooser.showSaveDialog(mainFrame);
            return result == JFileChooser.APPROVE_OPTION;
        }

        DirectoryChooserDefaults.putOption(
            DirectoryChooserDefaults.PROP_INITIAL_DIRECTORY,
            mainFrame.getRecentFileDirectory()
        );
        selectedDirectory = JDirectoryChooser.showDialog(mainFrame);
        return selectedDirectory != null;
    }

    public File getFile() {
        File file;

        if (SystemInfo.isMacOS) {
            file = new File(dialog.getDirectory(), dialog.getFile());
        } else {
            file = !directoriesOnly
                ? chooser.getSelectedFile()
                : selectedDirectory;
        }

        mainFrame.setRecentFileDirectory(
            !directoriesOnly ? file.getParentFile() : file
        );

        return file;
    }

    public void setFile(String file) {
        if (SystemInfo.isMacOS) {
            dialog.setFile(file);
        } else {
            if (!directoriesOnly) {
                if (chooser.getUI() instanceof BasicFileChooserUI) {
                    ((BasicFileChooserUI) chooser.getUI()).setFileName(file);
                }
            }
        }
    }

    public File[] getFiles() {
        if (SystemInfo.isMacOS) {
            return new File[] { getFile() };
        }
        if (directoriesOnly) {
            return new File[] { getFile() };
        }

        var files = chooser.getSelectedFiles();
        mainFrame.setRecentFileDirectory(files[0].getParentFile());
        return files;
    }

    public void setMultiSelectionEnabled(boolean enabled) {
        if (!SystemInfo.isMacOS && !directoriesOnly) {
            chooser.setMultiSelectionEnabled(enabled);
        }
    }
}
