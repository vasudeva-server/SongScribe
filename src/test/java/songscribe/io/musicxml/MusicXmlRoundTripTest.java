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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import songscribe.UnitTest;
import songscribe.dom.Song;

class MusicXmlRoundTripTest extends UnitTest {

    // -- helpers --

    private static String writeToString(Song song) throws Exception {
        var sw = new StringWriter();
        var pw = new PrintWriter(sw);
        MusicXmlWriter.writeSong(song, pw);
        pw.flush();
        return sw.toString();
    }

    private static Song parse(String xml) throws Exception {
        return MusicXmlReader.read(new InputSource(new StringReader(xml)));
    }

    public static Song roundTrip(Song song) throws Exception {
        return parse(writeToString(song));
    }

    private static void assertPopulatedSubsetEquals(Song expected, Song actual) {
        // Later phases extend this helper with additional fields.
        assertThat(actual.getDefaultKeyAccidentalCount())
            .as("default key accidental count")
            .isEqualTo(expected.getDefaultKeyAccidentalCount());
        assertThat(actual.getDefaultKeyType())
            .as("default key type")
            .isEqualTo(expected.getDefaultKeyType());
    }

    // -- tests --

    @Test
    void testEmptySongRoundTripsLosslessly() throws Exception {
        var song = new Song();
        var song2 = roundTrip(song);
        assertPopulatedSubsetEquals(song, song2);
        assertThat(song2.lineCount()).isEqualTo(0);
    }

    @Test
    void testEmptySongWriterOutputIsSchemaValid() throws Exception {
        var song = new Song();
        var xml = writeToString(song);
        var validator = new MusicXmlSchemaValidator();
        assertThatCode(() -> validator.validate(xml))
            .as("MusicXmlWriter output for new Song() must be schema-valid")
            .doesNotThrowAnyException();
    }
}
