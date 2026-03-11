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

package songscribe.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.swing.*;

import com.formdev.flatlaf.util.SystemInfo;

import org.jetbrains.annotations.Nullable;

import songscribe.Strings;
import songscribe.data.MyDesktop;
import songscribe.ui.Dialogs;
import songscribe.ui.Constants;
import songscribe.ui.component.IMainFrame;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.Score;
import songscribe.ui.dialog.PlatformFileDialog;

public final class FileUtils {

    private FileUtils() {}

    // Returns the extension without the dot
    public static String getExtension(String filePath) {
        var path = Paths.get(filePath);
        var fileName = path.getFileName().toString();
        var lastDot = fileName.lastIndexOf('.');

        if (lastDot == -1) {
            return "";
        }

        return fileName.substring(lastDot + 1);
    }

    public static String getPathWithoutExtension(File file) throws IOException {
        return getPathWithoutExtension(file.getCanonicalPath());
    }

    public static String getPathWithoutExtension(String path) {
        var lastDot = path.lastIndexOf('.');

        if (lastDot == -1) {
            return path;
        }

        return path.substring(0, lastDot);
    }

    public static String getFilename(String filePath) {
        var path = Paths.get(filePath);
        return path.getFileName().toString();
    }

    public static String getDirectory(String filePath) {
        var path = Paths.get(filePath);
        var parent = path.getParent();
        return (parent != null) ? parent.toString() : "";
    }

    public static String getSongFileNameForFileChooser(Score score) {
        var composition = score.getComposition();
        var title = composition.getTitle();
        var numberStr = composition.getNumber();
        var sb = new StringBuilder(title.length() + 10);

        try {
            var number = Integer.parseInt(numberStr);
            sb.append(String.format("%03d", number));
        } catch (NumberFormatException nfe) {
            sb.append(numberStr);
        }

        if (!numberStr.isEmpty()) {
            sb.append(' ');
        }

        sb.append(StringUtils.stripDiacritics(title));
        return sb.toString();
    }

    public static File ensureExtension(File file, String... extensions) {
        var name = file.getName().toLowerCase();

        for (var ext : extensions) {
            var dotExt = ext.startsWith(".") ? ext : "." + ext;

            if (name.endsWith(dotExt)) {
                return file;
            }
        }

        var dotExt = extensions[0].startsWith(".") ? extensions[0] : "." + extensions[0];
        return new File(file.getAbsolutePath() + dotExt);
    }

    @Nullable
    public static File showExportDialog(
        PlatformFileDialog fileDialog,
        String... extensions
    ) {
        var mainFrame = MainFrame.getInstance();
        fileDialog.setFile(getSongFileNameForFileChooser(mainFrame.getScore()));

        if (!fileDialog.showDialog()) {
            return null;
        }

        var saveFile = ensureExtension(fileDialog.getFile(), extensions);

        if (!Dialogs.confirmFileOverwrite(mainFrame, mainFrame.appName, saveFile)) {
            return null;
        }

        return saveFile;
    }

    public static File getDocumentsDirectory() {
        var directory = new File(System.getProperty("user.home"), "Documents");

        if (!SystemInfo.isWindows) {
            return directory;
        }
        return new File(System.getenv("USERPROFILE"), "Documents");
    }

    public static File getSongScribeDirectory() {
        return new File(getDocumentsDirectory(), Constants.PACKAGE_NAME);
    }

    public static void readComboValuesFromFile(
        JComboBox<? super String> combo,
        String file
    ) {
        try {
            var inputStream =
                FileUtils.class.getResourceAsStream("/conf/" + file);

            if (inputStream == null) {
                throw new FileNotFoundException("File not found: " + file);
            }

            try (
                var reader = new BufferedReader(
                    new InputStreamReader(inputStream)
                )
            ) {
                var line = reader.readLine();

                while (line != null) {
                    combo.addItem(line);
                    line = reader.readLine();
                }
            }
        } catch (IOException e) {
            Dialogs.showErrorMessage(
                null,
                Strings.get(Strings.DIALOG_TITLE_FILE_ERROR),
                Strings.get(Strings.ERROR_FILE_REINSTALL)
            );
        }
    }

    public static void copyFile(File in, File out) {
        try (
            var inputStream = new FileInputStream(in);
            var outputStream = new FileOutputStream(out)
        ) {
            var buf = new byte[1024];
            var bytesRead = inputStream.read(buf);

            while (bytesRead != -1) {
                outputStream.write(buf, 0, bytesRead);
                bytesRead = inputStream.read(buf);
            }
        } catch (IOException e) {
            // Ignore
        }
    }

    public static void openExportFile(IMainFrame mainFrame, File file) {
        if (MyDesktop.isDesktopSupported()) {
            var desktop = MyDesktop.getDesktop();

            if (desktop == null) {
                return;
            }

            var answer = Dialogs.showConfirmDialog(
                null,
                Strings.get(Strings.DIALOG_TITLE_FILE_ERROR),
                Strings.get(Strings.CONFIRM_FILE_OPEN),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
            );

            if (answer == JOptionPane.YES_OPTION) {
                try {
                    desktop.open(file);
                } catch (Exception e) {
                    Dialogs.showErrorMessage(
                        null,
                        Strings.get(Strings.DIALOG_TITLE_FILE_ERROR),
                        Strings.get(Strings.ERROR_FILE_OPEN)
                    );
                }
            }
        }
    }

    public static void zipFile(
        ZipOutputStream zos,
        File file,
        String requestName,
        byte[] buf
    ) throws IOException, FileNotFoundException {
        var fileName = (requestName == null) ? file.getName() : requestName;
        zos.putNextEntry(new ZipEntry(fileName));

        try (var inputStream = new FileInputStream(file)) {
            var bytesRead = inputStream.read(buf);

            while (bytesRead > 0) {
                zos.write(buf, 0, bytesRead);
                bytesRead = inputStream.read(buf);
            }
        }
    }
}
