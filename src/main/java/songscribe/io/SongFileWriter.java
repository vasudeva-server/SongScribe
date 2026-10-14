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
package songscribe.io;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import songscribe.dom.Song;
import songscribe.font.DocumentFontsHolder;
import songscribe.io.musicxml.MusicXmlWriter;
import songscribe.layout.LineLayoutProvider;

/**
 * Writes a {@link Song} to disk without requiring a {@code ScoreView}.
 */
public final class SongFileWriter {

    private SongFileWriter() {}

    /**
     * Does not close {@code pw} — the caller owns its lifecycle.
     */
    public static boolean write(Song song, DocumentFontsHolder fonts, PrintWriter pw) {
        return write(song, fonts, LineLayoutProvider.headless(song, fonts), pw);
    }

    /**
     * Does not close {@code pw} — the caller owns its lifecycle.
     */
    public static boolean write(
        Song song, DocumentFontsHolder fonts, LineLayoutProvider layoutProvider, PrintWriter pw) {
        MusicXmlWriter.writeSong(song, fonts, layoutProvider, pw);
        pw.flush();
        return !pw.checkError();
    }

    public static boolean write(Song song, DocumentFontsHolder fonts, File file) throws IOException {
        return write(song, fonts, LineLayoutProvider.headless(song, fonts), file);
    }

    public static boolean write(
        Song song, DocumentFontsHolder fonts, LineLayoutProvider layoutProvider, File file)
        throws IOException {

        try (var pw = new PrintWriter(file, StandardCharsets.UTF_8)) {
            return write(song, fonts, layoutProvider, pw);
        }
    }
}
