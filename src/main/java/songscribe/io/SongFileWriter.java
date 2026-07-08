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

/**
 * Writes a {@link Song} to disk without requiring a {@code ScoreView}.
 * <p>
 * Symmetric with {@link SongLoader}: this owns the write mechanics for the
 * save path so the {@code PrintWriter} error-checking guard is unit-testable
 * independent of {@code MainFrame}.
 */
public final class SongFileWriter {

    private SongFileWriter() {}

    /**
     * Writes {@code song} through {@code pw} and reports whether the write
     * succeeded. Does not close {@code pw} — the caller owns its lifecycle.
     *
     * @param song the song to write
     * @param fonts the document fonts to write alongside the song
     * @param pw the writer to write through
     * @return true on success, false if {@code pw} recorded an error
     */
    public static boolean write(Song song, DocumentFontsHolder fonts, PrintWriter pw) {
        MusicXmlWriter.writeSong(song, fonts, pw);
        pw.flush();
        return !pw.checkError();
    }

    /**
     * Writes {@code song} to {@code file}, opening and closing the underlying
     * {@code PrintWriter}.
     *
     * @param song the song to write
     * @param fonts the document fonts to write alongside the song
     * @param file the file to write to
     * @return true on success, false if the write recorded an error
     * @throws IOException if the file cannot be opened for writing
     */
    public static boolean write(Song song, DocumentFontsHolder fonts, File file) throws IOException {
        var pw = new PrintWriter(file, StandardCharsets.UTF_8);

        try {
            return write(song, fonts, pw);
        } finally {
            pw.close();
        }
    }
}
