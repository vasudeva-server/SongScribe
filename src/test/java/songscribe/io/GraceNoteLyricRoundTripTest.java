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

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Lyric;

/**
 * A syllable on a paired grace note survives the legacy {@code .mssw} load path. Since the
 * grace-host melisma became automatic, loading also normalizes the pair: the syllable becomes a
 * melisma START on the grace and the host receives the text-less STOP terminator, so these cases
 * double as coverage that the repair reaches the legacy reader (via
 * {@code MigrationPipeline}'s post-assembly stage) and not only the MusicXML one.
 */
class GraceNoteLyricRoundTripTest extends UnitTest {

    /**
     * Minimal XML with a paired grace+host pair (grace has CONNECTED glissando)
     * followed by a barline. No lyric yet — added programmatically before round-trip.
     */
    @SuppressWarnings("SameReturnValue")
    private static String graceHostXml() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <composition version="2.6">
              <keys>0</keys>
              <rightinfostarty>0.0</rightinfostarty>
              <linewidth>200.0</linewidth>
              <lines>
                <line>
                  <lyricsypos>5.0</lyricsypos>
                  <notes>
                    <note type="GRACE_QUAVER">
                      <staffposition>0</staffposition>
                      <glissando>CONNECTED</glissando>
                    </note>
                    <note type="CROTCHET">
                      <staffposition>0</staffposition>
                    </note>
                    <note type="SINGLE_BARLINE">
                      <staffposition>0</staffposition>
                    </note>
                  </notes>
                </line>
              </lines>
              <view/>
            </composition>
            """;
    }

    @Test
    void testGraceNoteLyricRoundTrips() throws Exception {
        var song = parseXml(graceHostXml());
        var line = song.getLine(0);

        line.getElement(0).lyrics.add(
            new Lyric(1, "la", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false)
        );

        var reloaded = legacyRoundTrip(song);
        var reloadedLine = reloaded.getLine(0);

        assertThat(reloadedLine.getElement(0).lyrics)
            .as("lyric on grace note must survive the round-trip, as an automatic melisma START")
            .containsExactly(new Lyric(1, "la", Lyric.Extend.START, Lyric.Syllabic.SINGLE, false));

        assertThat(reloadedLine.getElement(1).lyrics)
            .as("host of paired grace carries only the text-less melisma terminator")
            .containsExactly(new Lyric(1, "", Lyric.Extend.STOP, null, false));
    }

    @Test
    void testGraceNoteLyricPreservedOnDirectLoad() throws Exception {
        var xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <composition version="2.6">
              <keys>0</keys>
              <rightinfostarty>0.0</rightinfostarty>
              <linewidth>200.0</linewidth>
              <lines>
                <line>
                  <lyricsypos>5.0</lyricsypos>
                  <notes>
                    <note type="GRACE_QUAVER">
                      <staffposition>0</staffposition>
                      <glissando>CONNECTED</glissando>
                      <lyric number="1">
                        <syllabic>single</syllabic>
                        <text>la</text>
                      </lyric>
                    </note>
                    <note type="CROTCHET">
                      <staffposition>0</staffposition>
                    </note>
                    <note type="SINGLE_BARLINE">
                      <staffposition>0</staffposition>
                    </note>
                  </notes>
                </line>
              </lines>
              <view/>
            </composition>
            """;

        var song = parseXml(xml);
        var line = song.getLine(0);

        assertThat(line.getElement(0).lyrics)
            .as("lyric on grace note must load from XML, repaired into a melisma START")
            .containsExactly(new Lyric(1, "la", Lyric.Extend.START, Lyric.Syllabic.SINGLE, false));

        assertThat(line.getElement(1).lyrics)
            .as("the load-path repair gives the host the text-less melisma terminator")
            .containsExactly(new Lyric(1, "", Lyric.Extend.STOP, null, false));
    }
}
