/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.io.musicxml;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Song;

class MusicXmlWriterSchemaTest extends UnitTest {

    @Test
    void testEmptyDefaultSongIsSchemaValid() throws Exception {
        var validator = new MusicXmlSchemaValidator();
        var song = new Song();
        var sw = new StringWriter();
        var pw = new PrintWriter(sw);
        MusicXmlWriter.writeSong(song, pw);
        pw.flush();
        var xml = sw.toString();

        assertThatCode(() -> validator.validate(xml))
            .as("MusicXmlWriter output for new Song() must be schema-valid")
            .doesNotThrowAnyException();
    }
}
