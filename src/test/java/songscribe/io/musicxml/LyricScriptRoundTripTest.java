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

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.LineRange;
import songscribe.dom.ChangeHeading;
import songscribe.dom.Song;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.io.XmlFixtures.closeTag;
import static songscribe.io.XmlFixtures.element;
import static songscribe.io.XmlFixtures.emptyTag;
import static songscribe.io.XmlFixtures.openTag;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.middleCEighth;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.parseResult;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.scoreWithMeasureBody;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.writeToString;

/**
 * Which script a {@code <lyric number>} names: a number that names a script loads as that script
 * and is written back with the same number, and a number that names none is dropped, with the
 * load's report holding one entry for the line that lost it.
 *
 * <p>The numbers are written out rather than read from {@code LyricNumbers}: documents holding
 * them are already on disk, so a test that asked the production code which number means which
 * script would agree with a transposed pair.
 */
class LyricScriptRoundTripTest extends UnitTest {

    private static final String SYLLABLE = "la";

    private static final int FIRST_LINE_INDEX = 0;

    private static final int SECOND_LINE_INDEX = 1;

    /** A lyric number that names no script: scripts hold the numbers 1 through their count. */
    private static final int UNKNOWN_LYRIC_NUMBER = Song.Script.values().length + 1;

    /** One lyric number and the script it names. */
    private record NumberedScript(int number, Song.Script script) {

        @Override
        public String toString() {
            return number + " is " + script;
        }
    }

    static Stream<NumberedScript> numberedScripts() {
        return Stream.of(
            new NumberedScript(1, Song.Script.LATIN),
            new NumberedScript(2, Song.Script.BENGALI)
        );
    }

    @Test
    void testEveryScriptHasANumberedCase() {
        var covered = numberedScripts().map(NumberedScript::script).toArray(Song.Script[]::new);

        assertThat(covered).containsExactlyInAnyOrder(Song.Script.values());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("numberedScripts")
    void testALyricNumberLoadsAsItsScriptAndWritesBackWithTheSameNumber(NumberedScript numbered)
        throws Exception {
        var song = parseResult(scoreWithLyricNumbered(numbered.number())).song();

        var lyric = song.getLine(FIRST_LINE_INDEX).getElement(0).getLyricForScript(numbered.script());
        assertThat(lyric).as("the lyric loads as the script its number names").isNotNull();
        assertThat(lyric.text()).isEqualTo(SYLLABLE);

        assertThat(writeToString(song))
            .as("the lyric is written back under its number")
            .containsPattern("<lyric[^>]*\\bnumber=\"" + numbered.number() + '"');
    }

    @Test
    void testALyricWhoseNumberNamesNoScriptIsDroppedAndReportedOnce() throws Exception {
        var result = parseResult(scoreWithLyricNumbered(UNKNOWN_LYRIC_NUMBER));

        assertThat(result.song().getLine(FIRST_LINE_INDEX).getElement(0).getLyrics())
            .as("the song loads without the lyric")
            .isEmpty();

        assertThat(result.report().changes()).singleElement().satisfies(change -> {
            assertThat(change.heading()).isEqualTo(ChangeHeading.LYRICS_REMOVED);
            assertThat(change.description()).isEqualTo(Strings.get(Strings.REPORT_LYRICS_DETAIL));
            assertThat(change.where()).isEqualTo(new LineRange(FIRST_LINE_INDEX, FIRST_LINE_INDEX));
        });
    }

    @Test
    void testEachLineThatLostLyricsGetsOneEntryInLineOrder() throws Exception {
        var unknownLyric = lyricNumbered(UNKNOWN_LYRIC_NUMBER);
        var newSystem = emptyTag(
            MusicXmlRoundTripSupport.PRINT,
            MusicXmlRoundTripSupport.ATTR_NEW_SYSTEM,
            MusicXmlRoundTripSupport.YES);

        var result = parseResult(scoreWithMeasureBody(
            middleCEighth(unknownLyric) + middleCEighth(unknownLyric)
                + newSystem
                + middleCEighth(unknownLyric)));

        assertThat(result.report().changes())
            .extracting(change -> change.where())
            .containsExactly(
                new LineRange(FIRST_LINE_INDEX, FIRST_LINE_INDEX),
                new LineRange(SECOND_LINE_INDEX, SECOND_LINE_INDEX));
    }

    private static String lyricNumbered(int number) {
        return openTag(MusicXmlRoundTripSupport.LYRIC, MusicXmlRoundTripSupport.ATTR_NUMBER, Integer.toString(number))
            + element(MusicXmlRoundTripSupport.SYLLABIC, "single")
            + element(MusicXmlRoundTripSupport.LYRIC_TEXT, SYLLABLE)
            + closeTag(MusicXmlRoundTripSupport.LYRIC);
    }

    private static String scoreWithLyricNumbered(int number) {
        return scoreWithMeasureBody(middleCEighth(lyricNumbered(number)));
    }
}
