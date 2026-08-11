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
package songscribe.io.musicxml;

import java.io.PrintWriter;
import java.time.Clock;

import songscribe.dom.Song;
import songscribe.font.DocumentFontsHolder;
import songscribe.layout.LineLayoutProvider;

/**
 * Serializes a {@link Song} as MusicXML.
 *
 * <p>Both entry points require a {@link LineLayoutProvider}. Deciding what to do when the
 * caller has no layout — fall back to a headless one — belongs to {@link songscribe.io.SongFileWriter},
 * the save path's entry point, and is made there once; an overload here that defaulted it
 * would put the same decision in a second class.
 */
public final class MusicXmlWriter {

    private MusicXmlWriter() {}

    /**
     * Writes {@code song} to {@code pw} as MusicXML, taking its line geometry from
     * {@code layoutProvider}. Uses the system-default {@link Clock}.
     *
     * @param song           the song to serialize
     * @param fonts          the document fonts to emit under {@code <defaults>}/{@code <credit>}
     * @param layoutProvider supplies each line's layout, the source of the emitted glissando
     *                       coordinates
     * @param pw             the writer to emit the MusicXML document to
     */
    public static void writeSong(
        Song song, DocumentFontsHolder fonts, LineLayoutProvider layoutProvider, PrintWriter pw) {
        writeSong(song, fonts, layoutProvider, pw, Clock.systemDefaultZone());
    }

    /**
     * Writes {@code song} to {@code pw} as MusicXML, taking its line geometry from
     * {@code layoutProvider}. The {@code clock} is injectable so the write-forward
     * {@code <rights>} year and {@code <encoding-date>} are deterministic under test.
     *
     * @param song           the song to serialize
     * @param fonts          the document fonts to emit under {@code <defaults>}/{@code <credit>}
     * @param layoutProvider supplies each line's layout, the source of the emitted glissando
     *                       coordinates
     * @param pw             the writer to emit the MusicXML document to
     * @param clock          the clock supplying the current date for write-forward fields
     */
    public static void writeSong(
        Song song,
        DocumentFontsHolder fonts,
        LineLayoutProvider layoutProvider,
        PrintWriter pw,
        Clock clock) {
        MusicXmlSerializer.marshal(ScorePartwiseBuilder.build(song, fonts, layoutProvider, clock), pw);
    }
}
