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
package songscribe.converter;

import java.io.File;
import java.io.PrintWriter;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.SongScribe;
import songscribe.io.SongFileLoader;
import songscribe.ui.action.ExportABCAction;

public class AbcConverter {

    private static final Logger LOG = LoggerFactory.getLogger(AbcConverter.class);

    @FileArgument
    public @Nullable File file = null;

    public static void main(String[] args) {
        SongScribe.configureLogging();
        var reader = new ArgumentReader<>(args, AbcConverter.class);
        var converter = reader.getObj();

        if (converter == null) {
            LOG.error("Failed to parse arguments");
            return;
        }

        converter.convert(new PrintWriter(System.out));
    }

    public void convert(PrintWriter writer) {
        if (file == null) {
            LOG.error("No file specified");
            return;
        }

        try {
            var song = SongFileLoader.load(file).songOrThrow();
            ExportABCAction.writeABC(song, writer);
            writer.close();
            LOG.info("Converted {} to ABC", file.getName());
        } catch (Exception e) {
            LOG.error("Could not convert {}", file.getName(), e);
        }
    }
}
