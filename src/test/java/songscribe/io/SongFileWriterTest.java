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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.InputSource;

import songscribe.UnitTest;
import songscribe.dom.Song;
import songscribe.font.DocumentFonts;
import songscribe.io.musicxml.MusicXmlReader;

class SongFileWriterTest extends UnitTest {

    private static final String EXPECTED_ROOT_ELEMENT = "<score-partwise ";

    // A valid write through an in-memory PrintWriter returns true and produces parseable MusicXML.
    @Test
    void testWriteThroughPrintWriterSucceeds() throws Exception {
        var song = new Song();
        var stringWriter = new StringWriter();
        var printWriter = new PrintWriter(stringWriter);

        var succeeded = SongFileWriter.write(song, DocumentFonts.defaultFonts(), printWriter);

        assertThat(succeeded)
            .as("writing a valid song must succeed")
            .isTrue();

        var xml = stringWriter.toString();

        assertThat(xml)
            .as("written output must be parseable SongScribe MusicXML")
            .contains(EXPECTED_ROOT_ELEMENT);

        // Round-trip through the MusicXML reader: the read succeeding at all proves
        // parseability, and matching the line count proves the payload survived.
        var reloaded = MusicXmlReader.read(new InputSource(new StringReader(xml)));
        assertThat(reloaded.song().lineCount())
            .as("the reloaded song must preserve the source song's line count")
            .isEqualTo(song.lineCount());
    }

    // A PrintWriter over a Writer that throws on write/flush records an error via checkError(),
    // and write() must report failure rather than silently succeeding.
    @Test
    void testWriteThroughFailingWriterReturnsFalse() {
        var song = new Song();
        var printWriter = new PrintWriter(new ThrowingWriter());

        var succeeded = SongFileWriter.write(song, DocumentFonts.defaultFonts(), printWriter);

        assertThat(succeeded)
            .as("a PrintWriter that recorded an error must cause write() to report failure")
            .isFalse();
    }

    private static final String MUSICXML_FILE_NAME = "song.musicxml";

    // The File overload opens and closes its own PrintWriter, returns true, and
    // leaves parseable MusicXML on disk that reloads to the same line count.
    @Test
    void testWriteToFileCreatesParseableOutput(@TempDir Path tempDir) throws Exception {
        var song = new Song();
        var file = tempDir.resolve(MUSICXML_FILE_NAME).toFile();

        var succeeded = SongFileWriter.write(song, DocumentFonts.defaultFonts(), file);

        assertThat(succeeded)
            .as("writing a valid song to a fresh file must succeed")
            .isTrue();

        var reloaded = MusicXmlReader.read(file);
        assertThat(reloaded.song().lineCount())
            .as("the file's reloaded song must preserve the source song's line count")
            .isEqualTo(song.lineCount());
    }

    // Opening a directory as a file for writing fails; the File overload must
    // propagate the IOException rather than swallow it.
    @Test
    void testWriteToUnwritableTargetThrowsIOException(@TempDir Path tempDir) {
        var song = new Song();
        var directory = tempDir.toFile();

        assertThatIOException()
            .isThrownBy(() -> SongFileWriter.write(song, DocumentFonts.defaultFonts(), directory));
    }

    /** A {@link Writer} whose {@code write} and {@code flush} always throw {@link IOException}. */
    private static final class ThrowingWriter extends Writer {

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            throw new IOException("simulated disk-full write failure");
        }

        @Override
        public void flush() throws IOException {
            throw new IOException("simulated disk-full flush failure");
        }

        @Override
        public void close() {
            // no-op
        }
    }
}
