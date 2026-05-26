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

package songscribe.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import songscribe.UnitTest;
import songscribe.dom.Song;
import songscribe.font.DocumentFonts;

class SongLoadResultTest extends UnitTest {

    // row 51: Success branch returns the same song instance
    @Test
    void testSongOrThrowOnSuccessReturnsSong() throws Exception {
        var song = mock(Song.class);
        var fonts = mock(DocumentFonts.class);
        var result = new SongLoadResult.Success(song, fonts);
        assertThat(result.songOrThrow()).isSameAs(song);
    }

    // row 52: IoError branch throws the wrapped IOException
    @Test
    void testSongOrThrowOnIoErrorThrowsIOException() {
        var cause = new IOException("disk error");
        var result = new SongLoadResult.IoError(new File("test.mssw"), cause);
        assertThatThrownBy(result::songOrThrow)
            .isInstanceOf(IOException.class)
            .isSameAs(cause);
    }

    // row 53: ParseError branch throws the wrapped SAXException
    @Test
    void testSongOrThrowOnParseErrorThrowsSAXException() {
        var cause = new SAXException("bad xml");
        var result = new SongLoadResult.ParseError(new File("test.mssw"), cause);
        assertThatThrownBy(result::songOrThrow)
            .isInstanceOf(SAXException.class)
            .isSameAs(cause);
    }

    // row 54: NewerVersion branch throws the wrapped NewerVersionException
    @Test
    void testSongOrThrowOnNewerVersionThrowsNewerVersionException() {
        var cause = new SongIO.NewerVersionException();
        var result = new SongLoadResult.NewerVersion(new File("test.mssw"), cause);
        assertThatThrownBy(result::songOrThrow)
            .isInstanceOf(SongIO.NewerVersionException.class)
            .isSameAs(cause);
    }

    // row 55: LineWidthTooLarge branch throws IOException with both inch values in message
    @Test
    void testSongOrThrowOnLineWidthTooLargeThrowsIOExceptionWithBothInchValues() {
        double actualInches = 10.5;
        double maxInches = 8.5;
        var result = new SongLoadResult.LineWidthTooLarge(new File("test.mssw"), actualInches, maxInches);
        assertThatThrownBy(result::songOrThrow)
            .isInstanceOf(IOException.class)
            .hasMessageContaining(String.valueOf(actualInches))
            .hasMessageContaining(String.valueOf(maxInches));
    }
}
