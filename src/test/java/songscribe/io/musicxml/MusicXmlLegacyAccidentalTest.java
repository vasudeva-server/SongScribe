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

import org.junit.jupiter.api.Test;

import songscribe.dom.StaffElement.Accidental;
import songscribe.io.LegacyAccidentals;

/**
 * Reading a document written before {@code natural-flat} / {@code natural-sharp}
 * were retired: each converts to the accidental that sounds the same, and the
 * load result says so, which is what makes the song open marked modified.
 */
class MusicXmlLegacyAccidentalTest extends MusicXmlRoundTripSupport {

    private static String scoreWithAccidental(String token) {
        return scoreWithMeasureBody(
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>480</duration>\n" +
            "        <type>quarter</type>\n" +
            "        <accidental>" + token + "</accidental>\n" +
            "      </note>\n" +
            "      <barline location=\"right\"><bar-style>light-heavy</bar-style></barline>\n"
        );
    }

    @Test
    void testNaturalFlatLoadsAsFlatAndReportsConversion() throws Exception {
        var result = parseResult(scoreWithAccidental(LegacyAccidentals.ACCIDENTAL_NATURAL_FLAT));

        assertThat(result.song().getLine(0).getElement(0).getAccidental())
            .as("natural-flat must load as a plain flat")
            .isEqualTo(Accidental.FLAT);
        assertThat(result.accidentalsConverted())
            .as("the load must report the conversion so the song opens modified")
            .isTrue();
    }

    @Test
    void testNaturalSharpLoadsAsSharpAndReportsConversion() throws Exception {
        var result = parseResult(scoreWithAccidental(LegacyAccidentals.ACCIDENTAL_NATURAL_SHARP));

        assertThat(result.song().getLine(0).getElement(0).getAccidental())
            .as("natural-sharp must load as a plain sharp")
            .isEqualTo(Accidental.SHARP);
        assertThat(result.accidentalsConverted()).isTrue();
    }

    @Test
    void testDocumentWithNoRetiredAccidentalReportsNoConversion() throws Exception {
        // The negative half of the pair: without it the flag could be hardwired
        // true and every test above would still pass.
        var result = parseResult(scoreWithAccidental(AccidentalMapping.ACCIDENTAL_FLAT));

        assertThat(result.song().getLine(0).getElement(0).getAccidental())
            .as("precondition: the live token still loads as a flat")
            .isEqualTo(Accidental.FLAT);
        assertThat(result.accidentalsConverted())
            .as("a live accidental token must not be reported as a conversion")
            .isFalse();
    }

    @Test
    void testUnknownAccidentalTokenReportsNoConversion() throws Exception {
        assertThat(parseResult(scoreWithAccidental("bogus")).accidentalsConverted())
            .as("an unrecognised token is ignored, not converted")
            .isFalse();
    }
}
