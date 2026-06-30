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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import songscribe.dom.Beam;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.ElementType;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;
import songscribe.layout.LineEndingSupport;

/**
 * Reader robustness cases: lenient handling of dangling/stray markers, unknown
 * tokens, malformed documents, and {@code characters()} buffer isolation. These
 * exercise the read side with hand-crafted MusicXML the round-trip cannot produce.
 */
class MusicXmlReaderLenienceTest extends MusicXmlRoundTripSupport {

    // Element indices in the supersede-begin fixture: a stale beam begin (note 0)
    // followed by a valid two-note beam (notes 1-2). The surviving beam must
    // anchor at the second begin, not the orphaned first one.
    private static final int SECOND_BEGIN_INDEX = 1;
    private static final int BEAM_END_INDEX = 2;

    /**
     * Wraps {@code measureBody} in a minimal score-partwise document with a
     * new-system {@code <print>} (so the reader starts a line) and standard
     * attributes. Used by the reader edge-case and error-path tests below.
     */
    private static String scoreWithMeasureBody(String measureBody) {
        return
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<score-partwise version=\"4.0\">\n" +
            "  <part-list>\n" +
            "    <score-part id=\"P1\"><part-name></part-name></score-part>\n" +
            "  </part-list>\n" +
            "  <part id=\"P1\">\n" +
            "    <measure number=\"1\">\n" +
            "      <print new-system=\"yes\"/>\n" +
            "      <attributes>\n" +
            "        <divisions>480</divisions>\n" +
            "        <key><fifths>0</fifths></key>\n" +
            "        <time print-object=\"no\"><senza-misura/></time>\n" +
            "        <clef><sign>G</sign><line>2</line></clef>\n" +
            "      </attributes>\n" +
            measureBody +
            "    </measure>\n" +
            "  </part>\n" +
            "</score-partwise>\n";
    }

    /**
     * Task 2 — Dangling {@code <slide type="start">} reader handling: when no matching
     * {@code type="stop"} arrives (e.g. a truncated file), the reader drops the pending
     * start at part-end, the note has no glissando, and parsing completes without error.
     */
    @Test
    void testDanglingSlideStartProducesNoGlissando() throws Exception {
        // Hand-crafted MusicXML: one note with a slide start, no matching stop.
        var xml =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<score-partwise version=\"4.0\">\n" +
            "  <part-list>\n" +
            "    <score-part id=\"P1\"><part-name></part-name></score-part>\n" +
            "  </part-list>\n" +
            "  <part id=\"P1\">\n" +
            "    <measure number=\"1\">\n" +
            "      <print new-system=\"yes\"/>\n" +
            "      <attributes>\n" +
            "        <divisions>480</divisions>\n" +
            "        <key><fifths>0</fifths></key>\n" +
            "        <time print-object=\"no\"><senza-misura/></time>\n" +
            "        <clef><sign>G</sign><line>2</line></clef>\n" +
            "      </attributes>\n" +
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>480</duration>\n" +
            "        <type>quarter</type>\n" +
            "        <notations><slide type=\"start\" line-type=\"solid\"/></notations>\n" +
            "      </note>\n" +
            "      <barline location=\"right\"><bar-style>light-heavy</bar-style></barline>\n" +
            "    </measure>\n" +
            "  </part>\n" +
            "</score-partwise>\n";

        var song = parse(xml);

        assertThat(song.lineCount()).as("must have one line").isEqualTo(1);
        // The note plus the closing terminal barline (valid-music last line).
        assertThat(song.getLine(0).getElements()).as("note plus closing terminal").hasSize(2);
        assertThat(song.getLine(0).getElement(0).hasGlissando())
            .as("dangling slide start must leave the note without a glissando")
            .isFalse();
    }

    /**
     * Task 3 — {@code characters()} isolation: the reader accumulates text
     * unconditionally and clears the buffer at every {@code startElement}, so text
     * from one leaf element does not bleed into the next.
     * <p>
     * Scenario: {@code <step>C</step><alter>99</alter><octave>4</octave>} in a
     * compact, whitespace-free encoding. Without the clear-on-{@code startElement}
     * reset, the "99" from {@code <alter>} would remain in the buffer when
     * {@code <octave>} fires {@code characters("4")}, accumulating "994"; then
     * {@code parseInt("994")} would yield 994 instead of 4, and the staff position
     * would be wrong (or the parse would throw). With the reset, {@code <octave>}
     * starts with an empty buffer, so octave == 4 and staffPosition == C4.
     */
    @Test
    void testCharactersAccumulationDoesNotBleedAcrossLeafElements() throws Exception {
        // Compact encoding: no whitespace between adjacent pitch child elements,
        // so any buffer-bleed would concatenate directly (no trim salvation).
        var xml =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<score-partwise version=\"4.0\">\n" +
            "  <part-list>\n" +
            "    <score-part id=\"P1\"><part-name></part-name></score-part>\n" +
            "  </part-list>\n" +
            "  <part id=\"P1\">\n" +
            "    <measure number=\"1\">\n" +
            "      <print new-system=\"yes\"/>\n" +
            "      <attributes>\n" +
            "        <divisions>480</divisions>\n" +
            "        <key><fifths>0</fifths></key>\n" +
            "        <time print-object=\"no\"><senza-misura/></time>\n" +
            "        <clef><sign>G</sign><line>2</line></clef>\n" +
            "      </attributes>\n" +
            "      <note>" +
                "<pitch><step>C</step><alter>99</alter><octave>4</octave></pitch>" +
                "<duration>480</duration>" +
                "<type>quarter</type>" +
            "</note>\n" +
            "      <barline location=\"right\"><bar-style>light-heavy</bar-style></barline>\n" +
            "    </measure>\n" +
            "  </part>\n" +
            "</score-partwise>\n";

        var song = parse(xml);

        assertThat(song.lineCount()).as("must have one line").isEqualTo(1);
        // The note plus the closing terminal barline (valid-music last line).
        assertThat(song.getLine(0).getElements()).as("note plus closing terminal").hasSize(2);

        var note = song.getLine(0).getElement(0);
        assertThat(note.getType())
            .as("note type must be CROTCHET")
            .isEqualTo(ElementType.CROTCHET);
        assertThat(note.getStaffPosition())
            .as("staff position must be C4 (%d) — not contaminated by <alter>99</alter> text", C4_STAFF_POSITION)
            .isEqualTo(C4_STAFF_POSITION);
    }

    // -- Reader tolerance: stray/unknown tokens are ignored, not fatal --

    @Test
    void testStraySlideStopIsIgnored() throws Exception {
        var xml = scoreWithMeasureBody(
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>480</duration>\n" +
            "        <type>quarter</type>\n" +
            "        <notations><slide type=\"stop\" line-type=\"solid\"/></notations>\n" +
            "      </note>\n" +
            "      <barline location=\"right\"><bar-style>light-heavy</bar-style></barline>\n"
        );

        var song = parse(xml);
        // The note plus the closing terminal barline (valid-music last line).
        assertThat(song.getLine(0).getElements()).as("note plus closing terminal").hasSize(2);
        assertThat(song.getLine(0).getElement(0).hasGlissando())
            .as("a slide stop with no pending start must leave the note without a glissando")
            .isFalse();
    }

    @Test
    void testUnknownAccidentalTokenIsIgnored() throws Exception {
        var xml = scoreWithMeasureBody(
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>480</duration>\n" +
            "        <type>quarter</type>\n" +
            "        <accidental>bogus</accidental>\n" +
            "      </note>\n" +
            "      <barline location=\"right\"><bar-style>none</bar-style></barline>\n"
        );

        var song = parse(xml);
        assertThat(song.getLine(0).getElement(0).getAccidental())
            .as("an unrecognised <accidental> token must leave the note with no accidental")
            .isNull();
    }

    @Test
    void testUnknownDynamicSymbolIsIgnored() throws Exception {
        var xml = scoreWithMeasureBody(
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>480</duration>\n" +
            "        <type>quarter</type>\n" +
            "        <notations><dynamics><xyz/></dynamics></notations>\n" +
            "      </note>\n" +
            "      <barline location=\"right\"><bar-style>none</bar-style></barline>\n"
        );

        var song = parse(xml);
        assertThat(song.getLine(0).getElement(0).findAttachment(DynamicAttachment.class))
            .as("an unrecognised <dynamics> symbol must produce no DynamicAttachment")
            .isNull();
    }

    // -- Reader error paths: malformed documents must throw, not silently corrupt --

    @Test
    void testNoteMissingTypeThrows() {
        var xml = scoreWithMeasureBody(
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>480</duration>\n" +
            "      </note>\n" +
            "      <barline location=\"right\"><bar-style>none</bar-style></barline>\n"
        );

        assertThatThrownBy(() -> parse(xml))
            .isInstanceOf(SAXException.class)
            .hasMessageContaining("type");
    }

    @Test
    void testUnrecognisedTypeTokenThrows() {
        var xml = scoreWithMeasureBody(
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>480</duration>\n" +
            "        <type>bogus</type>\n" +
            "      </note>\n" +
            "      <barline location=\"right\"><bar-style>none</bar-style></barline>\n"
        );

        assertThatThrownBy(() -> parse(xml))
            .isInstanceOf(SAXException.class)
            .hasMessageContaining("Unrecognised");
    }

    @Test
    void testMalformedOctaveThrows() {
        var xml = scoreWithMeasureBody(
            "      <note>\n" +
            "        <pitch><step>B</step><octave>xyz</octave></pitch>\n" +
            "        <duration>480</duration>\n" +
            "        <type>quarter</type>\n" +
            "      </note>\n" +
            "      <barline location=\"right\"><bar-style>none</bar-style></barline>\n"
        );

        assertThatThrownBy(() -> parse(xml))
            .isInstanceOf(SAXException.class)
            .hasMessageContaining("octave");
    }

    @Test
    void testMalformedRelativeXThrows() {
        var xml = scoreWithMeasureBody(
            "      <note relative-x=\"abc\">\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>480</duration>\n" +
            "        <type>quarter</type>\n" +
            "      </note>\n" +
            "      <barline location=\"right\"><bar-style>none</bar-style></barline>\n"
        );

        assertThatThrownBy(() -> parse(xml))
            .isInstanceOf(SAXException.class)
            .hasMessageContaining("relative-x");
    }

    @Test
    void testNoteBeforeAnyLineThrows() {
        // A <note> in a measure that never opened a line (no <print new-system>):
        // currentLine is null when </note> assembles the note.
        var xml =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<score-partwise version=\"4.0\">\n" +
            "  <part-list>\n" +
            "    <score-part id=\"P1\"><part-name></part-name></score-part>\n" +
            "  </part-list>\n" +
            "  <part id=\"P1\">\n" +
            "    <measure number=\"1\">\n" +
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>480</duration>\n" +
            "        <type>quarter</type>\n" +
            "      </note>\n" +
            "    </measure>\n" +
            "  </part>\n" +
            "</score-partwise>\n";

        assertThatThrownBy(() -> parse(xml))
            .isInstanceOf(SAXException.class)
            .hasMessageContaining("before any line");
    }

    // -------------------------------------------------------------------------
    // Phase 7c: lenient range-span reads the round-trip cannot produce.
    //
    // Two symmetric failure modes per span type:
    //   * Dangling start — a begin/start whose matching end/stop never arrives
    //     (e.g. truncated input). The pending run is dropped at part-end flush;
    //     a range needs both endpoints, so nothing is built.
    //   * Orphan stop/end — a stop/end arriving with no pending anchor (e.g.
    //     malformed or foreign XML). It is ignored, building nothing.
    // Every case must leave parsing complete with no partial span created.
    // -------------------------------------------------------------------------

    // -- Dangling starts (matching stop/end never arrives) --

    @Test
    void testDanglingBeamBeginProducesNoBeam() throws Exception {
        var xml = scoreWithMeasureBody(
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>240</duration>\n" +
            "        <type>eighth</type>\n" +
            "        <beam number=\"1\">begin</beam>\n" +
            "      </note>\n" +
            "      <barline location=\"right\"><bar-style>light-heavy</bar-style></barline>\n"
        );

        var song = parse(xml);
        assertThat(song.lineCount()).as("parsing completes with one line").isEqualTo(1);
        assertThat(song.getLine(0).findRangeElements(Beam.class))
            .as("a beam begin with no matching end must build no span")
            .isEmpty();
    }

    /**
     * A stale beam begin superseded by a second begin must not absorb the
     * following valid beam. {@code resolveBeam} resets the pending anchor on
     * every begin, so the surviving beam anchors at the <em>second</em> begin
     * (index 1), not the orphaned first (index 0). Without that reset the lone
     * first begin would mis-anchor the span one note too early — a corruption a
     * single-note dangling fixture cannot surface, since it never reaches a
     * build path.
     */
    @Test
    void testDanglingBeamBeginDoesNotAbsorbLaterBeam() throws Exception {
        var xml = scoreWithMeasureBody(
            // Note 0: a begin with no matching end — superseded by the next begin.
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>240</duration>\n" +
            "        <type>eighth</type>\n" +
            "        <beam number=\"1\">begin</beam>\n" +
            "      </note>\n" +
            // Notes 1-2: a valid two-note beam.
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>240</duration>\n" +
            "        <type>eighth</type>\n" +
            "        <beam number=\"1\">begin</beam>\n" +
            "      </note>\n" +
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>240</duration>\n" +
            "        <type>eighth</type>\n" +
            "        <beam number=\"1\">end</beam>\n" +
            "      </note>\n" +
            "      <barline location=\"right\"><bar-style>light-heavy</bar-style></barline>\n"
        );

        var song = parse(xml);
        var beams = song.getLine(0).findRangeElements(Beam.class);
        assertThat(beams)
            .as("the stale begin must not produce a second beam")
            .hasSize(1);
        assertRangeElementEquals(beams.getFirst(), SECOND_BEGIN_INDEX, BEAM_END_INDEX,
            "beam after a superseded begin");
    }

    @Test
    void testDanglingTiedStartProducesNoTie() throws Exception {
        var xml = scoreWithMeasureBody(
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>480</duration>\n" +
            "        <type>quarter</type>\n" +
            "        <notations><tied type=\"start\"/></notations>\n" +
            "      </note>\n" +
            "      <barline location=\"right\"><bar-style>light-heavy</bar-style></barline>\n"
        );

        var song = parse(xml);
        assertThat(song.lineCount()).as("parsing completes with one line").isEqualTo(1);
        assertThat(song.getLine(0).findTies())
            .as("a tied start with no matching stop must build no span")
            .isEmpty();
    }

    @Test
    void testDanglingTupletStartProducesNoTuplet() throws Exception {
        var xml = scoreWithMeasureBody(
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>480</duration>\n" +
            "        <type>quarter</type>\n" +
            "        <time-modification><actual-notes>3</actual-notes><normal-notes>2</normal-notes></time-modification>\n" +
            "        <notations><tuplet type=\"start\" number=\"1\"/></notations>\n" +
            "      </note>\n" +
            "      <barline location=\"right\"><bar-style>light-heavy</bar-style></barline>\n"
        );

        var song = parse(xml);
        assertThat(song.lineCount()).as("parsing completes with one line").isEqualTo(1);
        assertThat(song.getLine(0).findRangeElements(Tuplet.class))
            .as("a tuplet start with no matching stop must build no span")
            .isEmpty();
    }

    @Test
    void testDanglingWavyLineStartProducesNoTrill() throws Exception {
        var xml = scoreWithMeasureBody(
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>480</duration>\n" +
            "        <type>quarter</type>\n" +
            "        <notations><ornaments><trill-mark/><wavy-line type=\"start\" number=\"1\"/></ornaments></notations>\n" +
            "      </note>\n" +
            "      <barline location=\"right\"><bar-style>light-heavy</bar-style></barline>\n"
        );

        var song = parse(xml);
        assertThat(song.lineCount()).as("parsing completes with one line").isEqualTo(1);
        assertThat(song.getLine(0).findRangeElements(Trill.class))
            .as("a wavy-line start with no matching stop must build no span")
            .isEmpty();
    }

    @Test
    void testUnpairedWedgeStartProducesNoHairpin() throws Exception {
        var xml = scoreWithMeasureBody(
            "      <direction><direction-type>" +
            "<wedge type=\"crescendo\" number=\"1\"/>" +
            "</direction-type></direction>\n" +
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>480</duration>\n" +
            "        <type>quarter</type>\n" +
            "      </note>\n" +
            "      <barline location=\"right\"><bar-style>light-heavy</bar-style></barline>\n"
        );

        var song = parse(xml);
        var line = song.getLine(0);
        assertThat(song.lineCount()).as("parsing completes with one line").isEqualTo(1);
        assertThat(line.getCrescendos())
            .as("a wedge start with no matching stop must build no hairpin")
            .isEmpty();
        assertThat(line.getDiminuendos())
            .as("a wedge start with no matching stop must build no hairpin")
            .isEmpty();
    }

    @Test
    void testPartialEndingMissingSecondStopProducesNoEnding() throws Exception {
        // A number="1" type="start" anchor with no number="2" type="stop" close —
        // finalizeOrDropPendingEnding drops it at part-end flush.
        var xml = scoreWithMeasureBody(
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>480</duration>\n" +
            "        <type>quarter</type>\n" +
            "      </note>\n" +
            "      <barline location=\"right\"><bar-style>light-heavy</bar-style>" +
            "<ending number=\"1\" type=\"start\"/></barline>\n"
        );

        var song = parse(xml);
        assertThat(song.lineCount()).as("parsing completes with one line").isEqualTo(1);
        assertThat(LineEndingSupport.findEndings(song.getLine(0)))
            .as("an ending start with no number=2 stop must build no span")
            .isEmpty();
    }

    // -- Orphan stops/ends (no pending anchor) --

    @Test
    void testOrphanBeamEndIsIgnored() throws Exception {
        var xml = scoreWithMeasureBody(
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>240</duration>\n" +
            "        <type>eighth</type>\n" +
            "        <beam number=\"1\">end</beam>\n" +
            "      </note>\n" +
            "      <barline location=\"right\"><bar-style>light-heavy</bar-style></barline>\n"
        );

        var song = parse(xml);
        assertThat(song.lineCount()).as("parsing completes with one line").isEqualTo(1);
        assertThat(song.getLine(0).findRangeElements(Beam.class))
            .as("a beam end with no pending begin must build no span")
            .isEmpty();
    }

    @Test
    void testOrphanTiedStopIsIgnored() throws Exception {
        var xml = scoreWithMeasureBody(
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>480</duration>\n" +
            "        <type>quarter</type>\n" +
            "        <notations><tied type=\"stop\"/></notations>\n" +
            "      </note>\n" +
            "      <barline location=\"right\"><bar-style>light-heavy</bar-style></barline>\n"
        );

        var song = parse(xml);
        assertThat(song.lineCount()).as("parsing completes with one line").isEqualTo(1);
        assertThat(song.getLine(0).findTies())
            .as("a tied stop with no pending start must build no span")
            .isEmpty();
    }

    @Test
    void testOrphanTupletStopIsIgnored() throws Exception {
        var xml = scoreWithMeasureBody(
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>480</duration>\n" +
            "        <type>quarter</type>\n" +
            "        <notations><tuplet type=\"stop\" number=\"1\"/></notations>\n" +
            "      </note>\n" +
            "      <barline location=\"right\"><bar-style>light-heavy</bar-style></barline>\n"
        );

        var song = parse(xml);
        assertThat(song.lineCount()).as("parsing completes with one line").isEqualTo(1);
        assertThat(song.getLine(0).findRangeElements(Tuplet.class))
            .as("a tuplet stop with no pending start must build no span")
            .isEmpty();
    }

    @Test
    void testOrphanWavyLineStopIsIgnored() throws Exception {
        var xml = scoreWithMeasureBody(
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>480</duration>\n" +
            "        <type>quarter</type>\n" +
            "        <notations><ornaments><wavy-line type=\"stop\" number=\"1\"/></ornaments></notations>\n" +
            "      </note>\n" +
            "      <barline location=\"right\"><bar-style>light-heavy</bar-style></barline>\n"
        );

        var song = parse(xml);
        assertThat(song.lineCount()).as("parsing completes with one line").isEqualTo(1);
        assertThat(song.getLine(0).findRangeElements(Trill.class))
            .as("a wavy-line stop with no pending start must build no span")
            .isEmpty();
    }

    @Test
    void testOrphanWedgeStopIsIgnored() throws Exception {
        var xml = scoreWithMeasureBody(
            "      <direction><direction-type>" +
            "<wedge type=\"stop\" number=\"1\"/>" +
            "</direction-type></direction>\n" +
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>480</duration>\n" +
            "        <type>quarter</type>\n" +
            "      </note>\n" +
            "      <barline location=\"right\"><bar-style>light-heavy</bar-style></barline>\n"
        );

        var song = parse(xml);
        var line = song.getLine(0);
        assertThat(song.lineCount()).as("parsing completes with one line").isEqualTo(1);
        assertThat(line.getCrescendos())
            .as("a wedge stop with no open hairpin must build no span")
            .isEmpty();
        assertThat(line.getDiminuendos())
            .as("a wedge stop with no open hairpin must build no span")
            .isEmpty();
    }

    @Test
    void testOrphanEndingStopIsIgnored() throws Exception {
        // A number="2" type="stop" with no matching number="1" start — ignored.
        var xml = scoreWithMeasureBody(
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>480</duration>\n" +
            "        <type>quarter</type>\n" +
            "      </note>\n" +
            "      <barline location=\"right\"><bar-style>light-heavy</bar-style>" +
            "<ending number=\"2\" type=\"stop\"/></barline>\n"
        );

        var song = parse(xml);
        assertThat(song.lineCount()).as("parsing completes with one line").isEqualTo(1);
        assertThat(LineEndingSupport.findEndings(song.getLine(0)))
            .as("an ending stop with no matching start must build no span")
            .isEmpty();
    }
}
