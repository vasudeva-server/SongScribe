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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.formdev.flatlaf.util.SystemFileChooser;

import songscribe.data.MyFileFilter;
import songscribe.ui.component.MainFrame;

public class PlatformFileDialog {

    private SystemFileChooser chooser;
    private final MainFrame mainFrame;
    private final boolean isOpenDialog;
    private final boolean directoriesOnly;
    private MyFileFilter[] originalFilters = null;
    private int initialFilterIndex = 0;
    private SystemFileChooser.FileNameExtensionFilter[] convertedFilters = null;

    private static SystemFileChooser.FileNameExtensionFilter convertFilter(MyFileFilter maf) {
        List<String> extensions = maf.getExtensions();

        // Extract description without extension list (remove " (ext1, ext2)")
        String description = maf.getDescription();
        int parenIndex = description.lastIndexOf('(');
        if (parenIndex > 0) {
            description = description.substring(0, parenIndex).trim();
        }

        return new SystemFileChooser.FileNameExtensionFilter(
            description,
            extensions.toArray(new String[0])
        );
    }

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
        this.originalFilters = filters;
        this.initialFilterIndex = initialFilterIndex;

        convertedFilters = new SystemFileChooser.FileNameExtensionFilter[filters.length];
        for (int i = 0; i < filters.length; i++) {
            convertedFilters[i] = convertFilter(filters[i]);
            chooser.addChoosableFileFilter(convertedFilters[i]);
        }

        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(convertedFilters[initialFilterIndex]);
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

        chooser = new SystemFileChooser();
        chooser.setDialogTitle(title);
        chooser.setDialogType(isOpenDialog
            ? SystemFileChooser.OPEN_DIALOG
            : SystemFileChooser.SAVE_DIALOG);

        if (directoriesOnly) {
            chooser.setFileSelectionMode(SystemFileChooser.DIRECTORIES_ONLY);
        }

        chooser.setCurrentDirectory(mainFrame.getRecentFileDirectory());
    }

    public void setFileFiler(MyFileFilter maf) {
        var filter = convertFilter(maf);
        chooser.setFileFilter(filter);
        chooser.setAcceptAllFileFilterUsed(false);
        this.originalFilters = new MyFileFilter[] { maf };
        this.convertedFilters = new SystemFileChooser.FileNameExtensionFilter[] { filter };
    }

    public MyFileFilter getFileFilter() {
        // First, try to infer format from the filename extension
        // This is more reliable than the filter dropdown on macOS native dialogs
        var selectedFile = chooser.getSelectedFile();
        if (selectedFile != null && originalFilters != null) {
            String fileName = selectedFile.getName().toLowerCase();
            for (MyFileFilter filter : originalFilters) {
                for (String ext : filter.getExtensions()) {
                    if (fileName.endsWith("." + ext.toLowerCase())) {
                        return filter;
                    }
                }
            }
        }

        // Fall back to filter dropdown selection
        var selectedFilter = chooser.getFileFilter();
        if (convertedFilters != null && selectedFilter != null) {
            String selectedDescription = selectedFilter.getDescription();
            for (int i = 0; i < convertedFilters.length; i++) {
                if (convertedFilters[i].getDescription().equals(selectedDescription)) {
                    return originalFilters[i];
                }
            }
        }

        return originalFilters != null && originalFilters.length > 0
            ? originalFilters[0]
            : null;
    }

    public boolean showDialog() {
        int result = isOpenDialog
            ? chooser.showOpenDialog(mainFrame)
            : chooser.showSaveDialog(mainFrame);

        return result == SystemFileChooser.APPROVE_OPTION;
    }

    public File getFile() {
        File file = chooser.getSelectedFile();

        mainFrame.setRecentFileDirectory(
            !directoriesOnly ? file.getParentFile() : file
        );

        return file;
    }

    public void setFile(String file) {
        chooser.setSelectedFile(new File(file));
    }

    public File[] getFiles() {
        var files = chooser.getSelectedFiles();
        if (files.length > 0) {
            mainFrame.setRecentFileDirectory(files[0].getParentFile());
        }
        return files;
    }

    public void setMultiSelectionEnabled(boolean enabled) {
        chooser.setMultiSelectionEnabled(enabled);
    }
}
