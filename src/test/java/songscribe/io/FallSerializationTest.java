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

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;

/**
 * Back-compatibility coverage for the fall serialization cut-over: the legacy
 * {@code <glissando>SLIDE_OUT</glissando>} loads as a fall (not a glissando, not dropped), a fall
 * round-trips through the new {@code <fall/>} tag, and connecting glissandos (named or legacy
 * integer) still load as connecting.
 */
@SuppressWarnings("OverlyBroadThrowsClause")
class FallSerializationTest extends UnitTest {

    // The connections fixture carries five CONNECTED glissandos and one legacy SLIDE_OUT.
    private static final int FIXTURE_FALL_COUNT = 1;
    private static final int FIXTURE_CONNECTING_COUNT = 5;

    private static String noteXml(String innerXml) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <composition version="1.1">
              <keys>0</keys>
              <keytype>SHARPS</keytype>
              <tempo>
                <visibletempo>120</visibletempo>
                <tempotype>CROTCHET</tempotype>
                <tempodescription></tempodescription>
                <showtempo>true</showtempo>
              </tempo>
              <songtitle>Test</songtitle>
              <lines>
                <line>
                  <keyCount>0</keyCount>
                  <keyType>SHARPS</keyType>
                  <tempoChangeYPos>-28</tempoChangeYPos>
                  <notes>
                    <note type="CROTCHET">
                      <staffposition>0</staffposition>
                      %s
                    </note>
                  </notes>
                </line>
              </lines>
              <view/>
            </composition>
            """.formatted(innerXml);
    }

    private static StaffElement firstElement(String innerXml) throws Exception {
        return parseXml(noteXml(innerXml)).getLine(0).getElement(0);
    }

    private static String writeNote(StaffElement note) {
        var sw = new StringWriter();
        StaffElementIO.writeElement(note, new PrintWriter(sw), detachedLine(), 0);
        return sw.toString();
    }

    @Test
    void testLegacySlideOutGlissandoLoadsAsFall() throws Exception {
        var note = firstElement("<glissando>SLIDE_OUT</glissando>");

        assertThat(note.hasFall()).as("legacy SLIDE_OUT loads as a fall").isTrue();
        assertThat(note.hasGlissando()).as("a fall is not a connecting glissando").isFalse();
    }

    @Test
    void testFallTagLoadsAsFall() throws Exception {
        var note = firstElement("<fall/>");

        assertThat(note.hasFall()).as("<fall/> loads as a fall").isTrue();
    }

    @Test
    void testConnectedGlissandoLoadsAsConnectingGlissando() throws Exception {
        var note = firstElement("<glissando>CONNECTED</glissando>");

        assertThat(note.hasGlissando()).as("CONNECTED loads as connecting").isTrue();
        assertThat(note.hasFall()).as("CONNECTED is not a fall").isFalse();
    }

    @Test
    void testLegacyNumericGlissandoLoadsAsConnectingGlissando() throws Exception {
        var note = firstElement("<glissando>5</glissando>");

        assertThat(note.hasGlissando()).as("legacy integer loads as connecting").isTrue();
        assertThat(note.hasFall()).as("legacy integer is not a fall").isFalse();
    }

    @Test
    void testFallIsEmittedAsFallTag() {
        var note = ElementType.CROTCHET.newInstance();
        note.setFall();

        var output = writeNote(note);

        assertThat(output).as("fall emits the <fall> tag").contains("<fall");
        assertThat(output).as("fall does not emit a glissando tag").doesNotContain("<glissando");
    }

    @Test
    void testConnectionsFixtureSlideOutLoadsAsSingleFall() throws Exception {
        var line = loadFixture("connections").getLine(0);

        var fallCount = 0;
        var connectingCount = 0;

        for (var i = 0; i < line.elementCount(); i++) {
            var element = line.getElement(i);

            if (element.hasFall()) {
                fallCount++;
            }

            if (element.hasGlissando()) {
                connectingCount++;
            }
        }

        assertThat(fallCount)
            .as("the fixture's legacy SLIDE_OUT loads as exactly one fall")
            .isEqualTo(FIXTURE_FALL_COUNT);
        assertThat(connectingCount)
            .as("the fixture's CONNECTED glissandos still load as connecting")
            .isEqualTo(FIXTURE_CONNECTING_COUNT);
    }
}
