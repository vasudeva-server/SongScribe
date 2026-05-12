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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.formdev.flatlaf.util.SystemInfo;

import org.jspecify.annotations.Nullable;

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

    public static File ensureExtension(File file, String... extensions) {
        var name = file.getName().toLowerCase();

        for (var ext : extensions) {
            if (name.endsWith(toDotExt(ext))) {
                return file;
            }
        }

        return new File(file.getAbsolutePath() + toDotExt(extensions[0]));
    }

    private static String toDotExt(String ext) {
        return ext.startsWith(".") ? ext : '.' + ext;
    }

    public static File getDocumentsDirectory() {
        var directory = new File(System.getProperty("user.home"), "Documents");

        if (!SystemInfo.isWindows) {
            return directory;
        }
        return new File(System.getenv("USERPROFILE"), "Documents");
    }

    public static void copyFile(File in, File out) {
        try {
            Files.copy(in.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // Ignore
        }
    }

    public static void zipFile(
        ZipOutputStream zos,
        File file,
        @Nullable String requestName,
        byte[] buf
    ) throws IOException, FileNotFoundException {
        var fileName = (requestName == null) ? file.getName() : requestName;
        zos.putNextEntry(new ZipEntry(fileName));

        try (var inputStream = new FileInputStream(file)) {
            var bytesRead = inputStream.read(buf);

            while (bytesRead != -1) {
                zos.write(buf, 0, bytesRead);
                bytesRead = inputStream.read(buf);
            }
        }
    }
}
