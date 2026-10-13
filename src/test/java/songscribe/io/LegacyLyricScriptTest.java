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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.LineRange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A legacy {@code .mssw} lyric whose number names no script is dropped, the song loads, and the
 * load's report holds one entry for the line that lost it. The rule is the one
 * {@code LyricScriptRoundTripTest} states for MusicXML: both readers report it through
 * {@code ParsedSong.lyricDroppedEntry}.
 */
class LegacyLyricScriptTest extends UnitTest {

    private static final int FIRST_LINE_INDEX = 0;

    /** A lyric number that names no script. */
    private static final int UNKNOWN_LYRIC_NUMBER = 3;

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
                  <lyric number="%d">
                    <syllabic>single</syllabic>
                    <text>la</text>
                  </lyric>
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

    @Test
    void testALyricWhoseNumberNamesNoScriptIsDroppedAndReportedOnce(@TempDir Path directory)
        throws IOException {
        var file = directory.resolve("unknown-lyric-number.mssw");
        Files.writeString(file, LEGACY_DOCUMENT.formatted(UNKNOWN_LYRIC_NUMBER));

        var result = SongLoader.load(file.toFile());

        assertThat(result).isInstanceOf(SongLoadResult.Success.class);
        var success = (SongLoadResult.Success) result;

        assertThat(success.song().getLine(FIRST_LINE_INDEX).getElement(0).getLyrics())
            .as("the song loads without the lyric")
            .isEmpty();

        assertThat(success.report().changes()).singleElement().satisfies(change -> {
            assertThat(change.headingKey()).isEqualTo(Strings.REPORT_LYRICS_REMOVED);
            assertThat(change.description()).isEqualTo(Strings.get(Strings.REPORT_LYRICS_DETAIL));
            assertThat(change.where()).isEqualTo(new LineRange(FIRST_LINE_INDEX, FIRST_LINE_INDEX));
        });
    }
}
