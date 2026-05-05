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

import com.formdev.flatlaf.util.SystemFileChooser;

import org.jspecify.annotations.Nullable;

import songscribe.file.MyFileFilter;
import songscribe.ui.component.MainFrame;

public class PlatformFileDialog {

    private final SystemFileChooser chooser;
    private final MainFrame mainFrame;
    private final boolean isOpenDialog;
    private MyFileFilter @Nullable [] originalFilters = null;
    private SystemFileChooser.FileNameExtensionFilter @Nullable [] convertedFilters = null;

    private static SystemFileChooser.FileNameExtensionFilter convertFilter(MyFileFilter maf) {
        var extensions = maf.getExtensions();

        // Extract description without extension list (remove " (ext1, ext2)")
        var description = maf.getDescription();
        var parenIndex = description.lastIndexOf('(');
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
        originalFilters = filters;

        convertedFilters = new SystemFileChooser.FileNameExtensionFilter[filters.length];
        for (var i = 0; i < filters.length; i++) {
            convertedFilters[i] = convertFilter(filters[i]);
            chooser.addChoosableFileFilter(convertedFilters[i]);
        }

        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(convertedFilters[Math.clamp(initialFilterIndex, 0, convertedFilters.length - 1)]);
    }

    private PlatformFileDialog(
        MainFrame mainFrame,
        String title,
        boolean isOpenDialog,
        boolean directoriesOnly
    ) {
        this.mainFrame = mainFrame;
        this.isOpenDialog = isOpenDialog;

        chooser = new SystemFileChooser();
        chooser.setDialogTitle(title);
        chooser.setDialogType(isOpenDialog
            ? SystemFileChooser.OPEN_DIALOG
            : SystemFileChooser.SAVE_DIALOG);

        if (directoriesOnly) {
            chooser.setFileSelectionMode(SystemFileChooser.DIRECTORIES_ONLY);
        }

    }

    public void setFileFiler(MyFileFilter maf) {
        var filter = convertFilter(maf);
        chooser.setFileFilter(filter);
        chooser.setAcceptAllFileFilterUsed(false);
        originalFilters = new MyFileFilter[] { maf };
        convertedFilters = new SystemFileChooser.FileNameExtensionFilter[] { filter };
    }

    public @Nullable MyFileFilter getFileFilter() {
        // First, try to infer format from the filename extension
        // This is more reliable than the filter dropdown on macOS native dialogs
        var selectedFile = chooser.getSelectedFile();
        if (selectedFile != null && originalFilters != null) {
            var fileName = selectedFile.getName().toLowerCase();
            for (var filter : originalFilters) {
                for (var ext : filter.getExtensions()) {
                    if (fileName.endsWith("." + ext.toLowerCase())) {
                        return filter;
                    }
                }
            }
        }

        // Fall back to filter dropdown selection
        var selectedFilter = chooser.getFileFilter();
        if (originalFilters != null && convertedFilters != null && selectedFilter != null) {
            var selectedDescription = selectedFilter.getDescription();
            for (var i = 0; i < convertedFilters.length; i++) {
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
        var result = isOpenDialog
            ? chooser.showOpenDialog(mainFrame)
            : chooser.showSaveDialog(mainFrame);

        return result == SystemFileChooser.APPROVE_OPTION;
    }

    public File getFile() {
        return chooser.getSelectedFile();
    }

    public void setFile(String file) {
        chooser.setSelectedFile(new File(file));
    }

    public File[] getFiles() {
        return chooser.getSelectedFiles();
    }

    public void setMultiSelectionEnabled(boolean enabled) {
        chooser.setMultiSelectionEnabled(enabled);
    }
}
