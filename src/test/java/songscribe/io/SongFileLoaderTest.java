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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.Key;
import songscribe.font.DocumentFonts;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.buildSong;
import static songscribe.dom.SongFactory.notesAroundKeyChange;
import static songscribe.dom.StaffElementFactory.crotchet;

/**
 * {@link SongFileLoader#load}: a document of either format that holds a key change restating the
 * key already in effect is refused as a stranded key change on its line, whichever reader read it.
 */
class SongFileLoaderTest extends UnitTest {

    private static final Key DOCUMENT_KEY = Key.NO_ACCIDENTALS;

    /** Each document's one line holds the stranded key change. */
    private static final int STRANDED_LINE_NUMBER = 1;

    /**
     * A file that holds a stranded key change.
     *
     * @param description the format, as the test's display name
     * @param fileName    the file's name, whose extension picks the reader
     * @param content     the file's text
     */
    private record StrandedFileCase(String description, String fileName, String content) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("strandedFileCases")
    void testAFileHoldingAStrandedKeyChangeIsRefusedNamingItsLine(StrandedFileCase testCase, @TempDir Path directory)
        throws IOException {
        var file = directory.resolve(testCase.fileName());
        Files.writeString(file, testCase.content());

        assertThat(SongFileLoader.load(file.toFile()))
            .isInstanceOfSatisfying(SongLoadResult.StrandedKeyChange.class, refused ->
                assertThat(refused.cause().lineNumber()).isEqualTo(STRANDED_LINE_NUMBER));
    }

    static Stream<StrandedFileCase> strandedFileCases() {
        return Stream.of(
            new StrandedFileCase("a legacy document", "stranded.mssw", LEGACY_DOCUMENT),
            new StrandedFileCase("a MusicXML document", "stranded.musicxml", musicXmlDocument()));
    }

    /** One line in the document key holding a key change that restates it. */
    private static final String LEGACY_DOCUMENT = """
        <?xml version="1.0" encoding="UTF-8"?>
        <composition version="2.10">
          <keys>0</keys>
          <keytype>FLATS</keytype>
          <songtitle>Untitled</songtitle>
          <composer>Sri Chinmoy</composer>
          <lyricist>Sri Chinmoy</lyricist>
          <lyricssource>LYRICIST</lyricssource>
          <linewidth>94.5</linewidth>
          <dynamicLayout>true</dynamicLayout>
          <lines>
            <line>
              <lyricsypos>5.0</lyricsypos>
              <notes>
                <note type="CROTCHET">
                  <staffposition>0</staffposition>
                </note>
                <note type="SINGLE_BARLINE">
                  <staffposition>0</staffposition>
                </note>
                <note type="KEY_CHANGE">
                  <staffposition>0</staffposition>
                </note>
                <note type="CROTCHET">
                  <staffposition>0</staffposition>
                </note>
                <note type="FINAL_DOUBLE_BARLINE">
                  <staffposition>0</staffposition>
                </note>
              </notes>
            </line>
          </lines>
          <view/>
        </composition>
        """;

    private static String musicXmlDocument() {
        var song = buildSong(line -> {
            line.setKey(DOCUMENT_KEY);
            notesAroundKeyChange(line, DOCUMENT_KEY);
            line.addElement(crotchet());
        });
        var text = new StringWriter();
        var writer = new PrintWriter(text);

        SongFileWriter.write(song, DocumentFonts.defaultFonts(), writer);
        writer.flush();

        return text.toString();
    }
}
