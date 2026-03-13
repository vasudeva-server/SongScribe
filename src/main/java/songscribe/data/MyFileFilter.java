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
package songscribe.data;

import module java.desktop;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;
import songscribe.util.FileUtils;

public class MyFileFilter extends FileFilter implements FilenameFilter {

    private final String description;
    private final ArrayList<String> extensions;

    public MyFileFilter(String description, String... extensions) {
        this.extensions = new ArrayList<>(extensions.length);
        this.extensions.addAll(List.of(extensions));
        this.description = description +
        " (" +
        String.join(", ", this.extensions) +
        ')';
    }

    @Override
    public boolean accept(File f) {
        return f.isDirectory() || accept(f.getName());
    }

    @Override
    public boolean accept(File dir, String name) {
        return accept(name);
    }

    private boolean accept(String fileName) {
        var ext = FileUtils.getExtension(fileName).toLowerCase();
        return !ext.isEmpty() && extensions.contains(ext);
    }

    public String getExtension(int index) {
        return extensions.get(index);
    }

    public List<String> getExtensions() {
        return new ArrayList<>(extensions);
    }

    @Override
    public String getDescription() {
        return description;
    }

    public String toString() {
        return description;
    }
}
