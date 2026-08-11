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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.SOFTWARE_ENCODING;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.SOFTWARE_IDENTIFICATION;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.assertSpanEquals;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.parse;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.scoreWithMeasureBody;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import songscribe.Constants;
import songscribe.UnitTest;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.Beam;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;
import songscribe.dom.TempoChangeAttachment;
import songscribe.dom.Trill;

/**
 * Reader robustness cases: lenient handling of dangling/stray markers, unknown
 * tokens, and malformed documents. These exercise the read side with hand-crafted
 * MusicXML the round-trip cannot produce.
 */
class MusicXmlReaderLenienceTest extends UnitTest {

    // Element indices in the supersede-begin fixture: a stale beam begin (note 0)
    // followed by a valid two-note beam (notes 1-2). The surviving beam must
    // anchor at the second begin, not the orphaned first one.
    private static final int SECOND_BEGIN_INDEX = 1;
    private static final int BEAM_END_INDEX = 2;

    /** A {@code <type>} token no note value maps to, quoted back in the failure detail. */
    private static final String UNRECOGNISED_NOTE_TYPE = "bogus";

    /** A provenance value that is neither ours nor blank — the "foreign" disjunct. */
    private static final String FOREIGN_SOFTWARE = "SomeOtherApp";

    /** A measure body no mapper objects to, so only the provenance tag can fail a fixture. */
    private static final String ONE_VALID_NOTE_BODY =
        """
                  <note>
                    <pitch><step>B</step><octave>4</octave></pitch>
                    <duration>480</duration>
                    <type>quarter</type>
                  </note>
                  <barline location="right"><bar-style>none</bar-style></barline>
            """;

    /** The same, with a {@code <type>} token {@code NoteMapper} rejects. */
    private static final String ONE_UNMAPPABLE_NOTE_BODY =
        ONE_VALID_NOTE_BODY.replace("<type>quarter</type>", "<type>" + UNRECOGNISED_NOTE_TYPE + "</type>");

    // Distinct annotation texts so a "which one survived" mix-up is caught.
    private static final String FIRST_ANNOTATION_TEXT = "dolce";
    private static final String SECOND_ANNOTATION_TEXT = "espressivo";

    /**
     * Wraps a single {@code <miscellaneous-field>} (the given name/value) in an
     * {@code <identification><miscellaneous>} block, followed by a minimal valid
     * one-note part so parsing reaches {@code </score-partwise>}. Used to exercise
     * the head miscellaneous-field error and treat-as-absent paths.
     */
    private static String scoreWithMiscellaneousField(String name, String value) {
        return
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<score-partwise version=\"4.0\">\n" +
            // One <identification>, carrying both the provenance <encoding> and the
            // <miscellaneous> block. Two of them would be schema-invalid, and the reader
            // would keep only the later one — losing <software> and failing the
            // provenance gate rather than exercising the field this fixture is about.
            "  <identification>\n" +
            SOFTWARE_ENCODING +
            "    <miscellaneous>\n" +
            "      <miscellaneous-field name=\"" + name + "\">" + value + "</miscellaneous-field>\n" +
            "    </miscellaneous>\n" +
            "  </identification>\n" +
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
            "      </note>\n" +
            "      <barline location=\"right\"><bar-style>light-heavy</bar-style></barline>\n" +
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
            SOFTWARE_IDENTIFICATION +
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

    // -- Reader tolerance: stray/unknown tokens are ignored, not fatal --

    @Test
    void testStraySlideStopIsIgnored() throws Exception {
        var xml = scoreWithMeasureBody(
            """
                      <note>
                        <pitch><step>B</step><octave>4</octave></pitch>
                        <duration>480</duration>
                        <type>quarter</type>
                        <notations><slide type="stop" line-type="solid"/></notations>
                      </note>
                      <barline location="right"><bar-style>light-heavy</bar-style></barline>
                """
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
            """
                      <note>
                        <pitch><step>B</step><octave>4</octave></pitch>
                        <duration>480</duration>
                        <type>quarter</type>
                        <accidental>bogus</accidental>
                      </note>
                      <barline location="right"><bar-style>none</bar-style></barline>
                """
        );

        var song = parse(xml);
        assertThat(song.getLine(0).getElement(0).getAccidental())
            .as("an unrecognised <accidental> token must leave the note with no accidental")
            .isNull();
    }

    @Test
    void testUnknownDynamicSymbolIsIgnored() throws Exception {
        var xml = scoreWithMeasureBody(
            """
                      <note>
                        <pitch><step>B</step><octave>4</octave></pitch>
                        <duration>480</duration>
                        <type>quarter</type>
                        <notations><dynamics><xyz/></dynamics></notations>
                      </note>
                      <barline location="right"><bar-style>none</bar-style></barline>
                """
        );

        var song = parse(xml);
        assertThat(song.getLine(0).getElement(0).findAttachment(DynamicAttachment.class))
            .as("an unrecognised <dynamics> symbol must produce no DynamicAttachment")
            .isNull();
    }

    /**
     * A {@code <barline>} with no {@code <bar-style>} child at all. MusicXML makes the child
     * optional, so this is a legitimate foreign-file shape the writer never produces (it always
     * emits a style, {@code none} included). The reader treats a missing style exactly like
     * {@code none} — an invisible barline that contributes no element — rather than throwing or
     * inserting a default barline the file never asked for.
     */
    @Test
    void testBarlineWithoutBarStyleIsTreatedAsInvisible() throws Exception {
        var xml = scoreWithMeasureBody(
            """
                      <note>
                        <pitch><step>B</step><octave>4</octave></pitch>
                        <duration>480</duration>
                        <type>quarter</type>
                      </note>
                      <barline location="right"/>
                      <note>
                        <pitch><step>B</step><octave>4</octave></pitch>
                        <duration>480</duration>
                        <type>quarter</type>
                      </note>
                      <barline location="right"><bar-style>light-heavy</bar-style></barline>
                """
        );

        var song = parse(xml);

        assertThat(song.lineCount()).as("parsing completes with one line").isEqualTo(1);
        assertThat(song.getLine(0).getElements())
            .as("a barline with no <bar-style> must insert no element between the two notes")
            .extracting(StaffElement::getType)
            .containsExactly(ElementType.CROTCHET, ElementType.CROTCHET, ElementType.FINAL_DOUBLE_BARLINE);
    }

    // -- Reader error paths: malformed documents must throw, not silently corrupt --

    @Test
    void testNoteMissingTypeThrows() {
        var xml = scoreWithMeasureBody(
            """
                      <note>
                        <pitch><step>B</step><octave>4</octave></pitch>
                        <duration>480</duration>
                      </note>
                      <barline location="right"><bar-style>none</bar-style></barline>
                """
        );

        // The exact detail, not a substring: a missing <type> and an unrecognised one
        // both fail here, and "type" appears in either message.
        var exception = assertThrows(MusicXmlReader.UnsupportedFormatException.class, () -> parse(xml));
        assertThat(exception.detail()).isEqualTo("<note> is missing its <type> element");
    }

    @Test
    void testUnrecognisedTypeTokenThrows() {
        var xml = scoreWithMeasureBody(
            """
                      <note>
                        <pitch><step>B</step><octave>4</octave></pitch>
                        <duration>480</duration>
                        <type>%s</type>
                      </note>
                      <barline location="right"><bar-style>none</bar-style></barline>
                """.formatted(UNRECOGNISED_NOTE_TYPE)
        );

        // detail() quotes the offending token, which is what separates this branch
        // from the missing-<type> one above.
        var exception = assertThrows(MusicXmlReader.UnsupportedFormatException.class, () -> parse(xml));
        assertThat(exception.detail())
            .isEqualTo("Unrecognised <type> token: '" + UNRECOGNISED_NOTE_TYPE + '\'');
    }

    @Test
    void testMalformedOctaveThrows() {
        var xml = scoreWithMeasureBody(
            """
                      <note>
                        <pitch><step>B</step><octave>xyz</octave></pitch>
                        <duration>480</duration>
                        <type>quarter</type>
                      </note>
                      <barline location="right"><bar-style>none</bar-style></barline>
                """
        );

        assertThatThrownBy(() -> parse(xml))
            .isInstanceOf(SAXException.class)
            .hasMessageContaining("octave");
    }

    @Test
    void testMalformedRelativeXThrows() {
        var xml = scoreWithMeasureBody(
            """
                      <note relative-x="abc">
                        <pitch><step>B</step><octave>4</octave></pitch>
                        <duration>480</duration>
                        <type>quarter</type>
                      </note>
                      <barline location="right"><bar-style>none</bar-style></barline>
                """
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
            SOFTWARE_IDENTIFICATION +
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
            """
                      <note>
                        <pitch><step>B</step><octave>4</octave></pitch>
                        <duration>240</duration>
                        <type>eighth</type>
                        <beam number="1">begin</beam>
                      </note>
                      <barline location="right"><bar-style>light-heavy</bar-style></barline>
                """
        );

        var song = parse(xml);
        assertThat(song.lineCount()).as("parsing completes with one line").isEqualTo(1);
        assertThat(song.getLine(0).findSpans(Beam.class))
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
        var beams = song.getLine(0).findSpans(Beam.class);
        assertThat(beams)
            .as("the stale begin must not produce a second beam")
            .hasSize(1);
        assertSpanEquals(beams.getFirst(), SECOND_BEGIN_INDEX, BEAM_END_INDEX,
            "beam after a superseded begin");
    }

    @Test
    void testDanglingTiedStartProducesNoTie() throws Exception {
        var xml = scoreWithMeasureBody(
            """
                      <note>
                        <pitch><step>B</step><octave>4</octave></pitch>
                        <duration>480</duration>
                        <type>quarter</type>
                        <notations><tied type="start"/></notations>
                      </note>
                      <barline location="right"><bar-style>light-heavy</bar-style></barline>
                """
        );

        var song = parse(xml);
        assertThat(song.lineCount()).as("parsing completes with one line").isEqualTo(1);
        assertThat(song.getLine(0).findTies())
            .as("a tied start with no matching stop must build no span")
            .isEmpty();
    }

    @Test
    void testDanglingTupletStartThrows() {
        var xml = scoreWithMeasureBody(
            """
                      <note>
                        <pitch><step>B</step><octave>4</octave></pitch>
                        <duration>480</duration>
                        <type>quarter</type>
                        <time-modification><actual-notes>3</actual-notes><normal-notes>2</normal-notes></time-modification>
                        <notations><tuplet type="start" number="1"/></notations>
                      </note>
                      <barline location="right"><bar-style>light-heavy</bar-style></barline>
                """
        );

        assertThatThrownBy(() -> parse(xml))
            .as("a tuplet start with no matching stop is not a legitimate document state (#518)")
            .isInstanceOf(SAXException.class)
            .hasMessageContaining("tuplet");
    }

    @Test
    void testSingleNoteTupletThrows() {
        var xml = scoreWithMeasureBody(
            """
                      <note>
                        <pitch><step>B</step><octave>4</octave></pitch>
                        <duration>480</duration>
                        <type>quarter</type>
                        <time-modification><actual-notes>3</actual-notes><normal-notes>2</normal-notes></time-modification>
                        <notations>
                          <tuplet type="start" number="1"/>
                          <tuplet type="stop" number="1"/>
                        </notations>
                      </note>
                      <barline location="right"><bar-style>light-heavy</bar-style></barline>
                """
        );

        assertThatThrownBy(() -> parse(xml))
            .as("a tuplet spanning a single note has no meaningful span (#518)")
            .isInstanceOf(SAXException.class)
            .hasMessageContaining("tuplet does not span at least two non-rest notes");
    }

    @Test
    void testDanglingWavyLineStartProducesNoTrill() throws Exception {
        var xml = scoreWithMeasureBody(
            """
                      <note>
                        <pitch><step>B</step><octave>4</octave></pitch>
                        <duration>480</duration>
                        <type>quarter</type>
                        <notations><ornaments><trill-mark/><wavy-line type="start" number="1"/></ornaments></notations>
                      </note>
                      <barline location="right"><bar-style>light-heavy</bar-style></barline>
                """
        );

        var song = parse(xml);
        assertThat(song.lineCount()).as("parsing completes with one line").isEqualTo(1);
        assertThat(song.getLine(0).findSpans(Trill.class))
            .as("a wavy-line start with no matching stop must build no span")
            .isEmpty();
    }

    @Test
    void testUnpairedWedgeStartProducesNoHairpin() throws Exception {
        var xml = scoreWithMeasureBody(
            """
                      <direction><direction-type>\
                <wedge type="crescendo" number="1"/>\
                </direction-type></direction>
                      <note>
                        <pitch><step>B</step><octave>4</octave></pitch>
                        <duration>480</duration>
                        <type>quarter</type>
                      </note>
                      <barline location="right"><bar-style>light-heavy</bar-style></barline>
                """
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
        // dropPendingEnding drops it at part-end flush.
        var xml = scoreWithMeasureBody(
            """
                      <note>
                        <pitch><step>B</step><octave>4</octave></pitch>
                        <duration>480</duration>
                        <type>quarter</type>
                      </note>
                      <barline location="right"><bar-style>light-heavy</bar-style>\
                <ending number="1" type="start"/></barline>
                """
        );

        var song = parse(xml);
        assertThat(song.lineCount()).as("parsing completes with one line").isEqualTo(1);
        assertThat(song.getLine(0).findEndings())
            .as("an ending start with no number=2 stop must build no span")
            .isEmpty();
    }

    // -- Orphan stops/ends (no pending anchor) --

    @Test
    void testOrphanBeamEndIsIgnored() throws Exception {
        var xml = scoreWithMeasureBody(
            """
                      <note>
                        <pitch><step>B</step><octave>4</octave></pitch>
                        <duration>240</duration>
                        <type>eighth</type>
                        <beam number="1">end</beam>
                      </note>
                      <barline location="right"><bar-style>light-heavy</bar-style></barline>
                """
        );

        var song = parse(xml);
        assertThat(song.lineCount()).as("parsing completes with one line").isEqualTo(1);
        assertThat(song.getLine(0).findSpans(Beam.class))
            .as("a beam end with no pending begin must build no span")
            .isEmpty();
    }

    @Test
    void testOrphanTiedStopIsIgnored() throws Exception {
        var xml = scoreWithMeasureBody(
            """
                      <note>
                        <pitch><step>B</step><octave>4</octave></pitch>
                        <duration>480</duration>
                        <type>quarter</type>
                        <notations><tied type="stop"/></notations>
                      </note>
                      <barline location="right"><bar-style>light-heavy</bar-style></barline>
                """
        );

        var song = parse(xml);
        assertThat(song.lineCount()).as("parsing completes with one line").isEqualTo(1);
        assertThat(song.getLine(0).findTies())
            .as("a tied stop with no pending start must build no span")
            .isEmpty();
    }

    @Test
    void testOrphanTupletStopThrows() {
        var xml = scoreWithMeasureBody(
            """
                      <note>
                        <pitch><step>B</step><octave>4</octave></pitch>
                        <duration>480</duration>
                        <type>quarter</type>
                        <notations><tuplet type="stop" number="1"/></notations>
                      </note>
                      <barline location="right"><bar-style>light-heavy</bar-style></barline>
                """
        );

        assertThatThrownBy(() -> parse(xml))
            .as("a tuplet stop with no pending start is not a legitimate document state (#518)")
            .isInstanceOf(SAXException.class)
            .hasMessageContaining("tuplet");
    }

    @Test
    void testOrphanWavyLineStopIsIgnored() throws Exception {
        var xml = scoreWithMeasureBody(
            """
                      <note>
                        <pitch><step>B</step><octave>4</octave></pitch>
                        <duration>480</duration>
                        <type>quarter</type>
                        <notations><ornaments><wavy-line type="stop" number="1"/></ornaments></notations>
                      </note>
                      <barline location="right"><bar-style>light-heavy</bar-style></barline>
                """
        );

        var song = parse(xml);
        assertThat(song.lineCount()).as("parsing completes with one line").isEqualTo(1);
        assertThat(song.getLine(0).findSpans(Trill.class))
            .as("a wavy-line stop with no pending start must build no span")
            .isEmpty();
    }

    @Test
    void testOrphanWedgeStopIsIgnored() throws Exception {
        var xml = scoreWithMeasureBody(
            """
                      <direction><direction-type>\
                <wedge type="stop" number="1"/>\
                </direction-type></direction>
                      <note>
                        <pitch><step>B</step><octave>4</octave></pitch>
                        <duration>480</duration>
                        <type>quarter</type>
                      </note>
                      <barline location="right"><bar-style>light-heavy</bar-style></barline>
                """
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
            """
                      <note>
                        <pitch><step>B</step><octave>4</octave></pitch>
                        <duration>480</duration>
                        <type>quarter</type>
                      </note>
                      <barline location="right"><bar-style>light-heavy</bar-style>\
                <ending number="2" type="stop"/></barline>
                """
        );

        var song = parse(xml);
        assertThat(song.lineCount()).as("parsing completes with one line").isEqualTo(1);
        assertThat(song.getLine(0).findEndings())
            .as("an ending stop with no matching start must build no span")
            .isEmpty();
    }

    // -------------------------------------------------------------------------
    // <metronome> shapes a SongScribe-written file never contains: the writer
    // only emits recognised beat-unit/metronome-note tokens, and a tempo or
    // metric-modulation direction always resolves to either the song's own
    // tempo or a bound note. A file from another program can violate either
    // rule, and none of it may corrupt the read or bind a malformed/unbound
    // mark to the wrong note.
    // -------------------------------------------------------------------------

    private static final int TEMPO_BPM = 120;
    private static final String UNKNOWN_NOTE_TYPE_TOKEN = "bogus";

    /** A beat-unit tempo {@code <direction>}: {@code <beat-unit>} plus {@code <per-minute>}. */
    private static String tempoDirection(String beatUnitToken, int bpm) {
        return
            "      <" + MusicXmlTags.DIRECTION + ">\n" +
            "        <" + MusicXmlTags.DIRECTION_TYPE + "><" + MusicXmlTags.METRONOME + ">" +
            "<" + MusicXmlTags.BEAT_UNIT + ">" + beatUnitToken + "</" + MusicXmlTags.BEAT_UNIT + ">" +
            "<" + MusicXmlTags.PER_MINUTE + ">" + bpm + "</" + MusicXmlTags.PER_MINUTE + ">" +
            "</" + MusicXmlTags.METRONOME + "></" + MusicXmlTags.DIRECTION_TYPE + ">\n" +
            "      </" + MusicXmlTags.DIRECTION + ">\n";
    }

    /** An incomplete beat-unit tempo {@code <direction>}: no {@code <per-minute>}. */
    private static String beatUnitOnlyDirection(String beatUnitToken) {
        return
            "      <" + MusicXmlTags.DIRECTION + ">\n" +
            "        <" + MusicXmlTags.DIRECTION_TYPE + "><" + MusicXmlTags.METRONOME + ">" +
            "<" + MusicXmlTags.BEAT_UNIT + ">" + beatUnitToken + "</" + MusicXmlTags.BEAT_UNIT + ">" +
            "</" + MusicXmlTags.METRONOME + "></" + MusicXmlTags.DIRECTION_TYPE + ">\n" +
            "      </" + MusicXmlTags.DIRECTION + ">\n";
    }

    /**
     * A metric-modulation {@code <direction>} with one {@code <metronome-note>} per given
     * note-type token; a {@code null} token omits that note's {@code <metronome-type>}.
     */
    private static String metricModulationDirection(@Nullable String... noteTypeTokens) {
        var notes = new StringBuilder();

        for (var token : noteTypeTokens) {
            notes.append('<').append(MusicXmlTags.METRONOME_NOTE).append('>');

            if (token != null) {
                notes.append('<').append(MusicXmlTags.METRONOME_TYPE).append('>').append(token)
                    .append("</").append(MusicXmlTags.METRONOME_TYPE).append('>');
            }

            notes.append("</").append(MusicXmlTags.METRONOME_NOTE).append('>');
        }

        return
            "      <" + MusicXmlTags.DIRECTION + ">\n" +
            "        <" + MusicXmlTags.DIRECTION_TYPE + "><" + MusicXmlTags.METRONOME + ">" +
            notes +
            "</" + MusicXmlTags.METRONOME + "></" + MusicXmlTags.DIRECTION_TYPE + ">\n" +
            "      </" + MusicXmlTags.DIRECTION + ">\n";
    }

    @Test
    void testUnrecognisedBeatUnitTokenIsDropped() throws Exception {
        var xml = scoreWithMeasureBody(
            tempoDirection(UNKNOWN_NOTE_TYPE_TOKEN, TEMPO_BPM)
            + """
                      <note>
                        <pitch><step>B</step><octave>4</octave></pitch>
                        <duration>480</duration>
                        <type>quarter</type>
                      </note>
                      <barline location="right"><bar-style>light-heavy</bar-style></barline>
                """
        );

        var song = parse(xml);

        assertThat(song.getLine(0).getElement(0).findAttachment(TempoChangeAttachment.class))
            .as("an unrecognised beat-unit token must build no tempo")
            .isNull();
    }

    @Test
    void testBeatUnitWithoutPerMinuteIsDropped() throws Exception {
        var xml = scoreWithMeasureBody(
            beatUnitOnlyDirection(NoteTypeMapping.TYPE_QUARTER)
            + """
                      <note>
                        <pitch><step>B</step><octave>4</octave></pitch>
                        <duration>480</duration>
                        <type>quarter</type>
                      </note>
                      <barline location="right"><bar-style>light-heavy</bar-style></barline>
                """
        );

        var song = parse(xml);

        assertThat(song.getLine(0).getElement(0).findAttachment(TempoChangeAttachment.class))
            .as("a beat-unit with no per-minute must build no tempo")
            .isNull();
    }

    @Test
    void testMetricModulationWithWrongNoteCountIsDropped() throws Exception {
        // The metric-modulation form requires exactly two <metronome-note>s.
        var xml = scoreWithMeasureBody(
            metricModulationDirection(NoteTypeMapping.TYPE_QUARTER)
            + """
                      <note>
                        <pitch><step>B</step><octave>4</octave></pitch>
                        <duration>480</duration>
                        <type>quarter</type>
                      </note>
                      <barline location="right"><bar-style>light-heavy</bar-style></barline>
                """
        );

        var song = parse(xml);

        assertThat(song.getLine(0).getElement(0).findAttachment(BeatChangeAttachment.class))
            .as("a one-note metric modulation must build no beat change")
            .isNull();
    }

    @Test
    void testMetricModulationWithUnrecognisedNoteIsDropped() throws Exception {
        var xml = scoreWithMeasureBody(
            metricModulationDirection(NoteTypeMapping.TYPE_QUARTER, UNKNOWN_NOTE_TYPE_TOKEN)
            + """
                      <note>
                        <pitch><step>B</step><octave>4</octave></pitch>
                        <duration>480</duration>
                        <type>quarter</type>
                      </note>
                      <barline location="right"><bar-style>light-heavy</bar-style></barline>
                """
        );

        var song = parse(xml);

        assertThat(song.getLine(0).getElement(0).findAttachment(BeatChangeAttachment.class))
            .as("an unrecognised metronome-note must build no beat change")
            .isNull();
    }

    @Test
    void testMetronomeNoteMissingTypeIsDropped() throws Exception {
        // The second <metronome-note> carries no <metronome-type>, leaving a
        // one-note (wrong-count) modulation once the typeless note is dropped.
        var xml = scoreWithMeasureBody(
            metricModulationDirection(NoteTypeMapping.TYPE_QUARTER, null)
            + """
                      <note>
                        <pitch><step>B</step><octave>4</octave></pitch>
                        <duration>480</duration>
                        <type>quarter</type>
                      </note>
                      <barline location="right"><bar-style>light-heavy</bar-style></barline>
                """
        );

        var song = parse(xml);

        assertThat(song.getLine(0).getElement(0).findAttachment(BeatChangeAttachment.class))
            .as("a metronome-note missing its type must build no beat change")
            .isNull();
    }

    @Test
    void testIncompleteDirectionDoesNotLeakIntoNext() throws Exception {
        // An incomplete tempo direction (beat-unit, no per-minute) is dropped; a
        // second, complete tempo direction immediately after must still build
        // cleanly and bind to the following note — no state survives the drop.
        // Both directions sit in the second measure, past the song-tempo window,
        // so the surviving one is required to bind to the note rather than being
        // taken as the song's own tempo.
        var xml =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<score-partwise version=\"4.0\">\n" +
            SOFTWARE_IDENTIFICATION +
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
            "      </note>\n" +
            "    </measure>\n" +
            "    <measure number=\"2\">\n" +
            beatUnitOnlyDirection(NoteTypeMapping.TYPE_QUARTER) +
            tempoDirection(NoteTypeMapping.TYPE_QUARTER, TEMPO_BPM) +
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>480</duration>\n" +
            "        <type>quarter</type>\n" +
            "      </note>\n" +
            "      <barline location=\"right\"><bar-style>light-heavy</bar-style></barline>\n" +
            "    </measure>\n" +
            "  </part>\n" +
            "</score-partwise>\n";

        var song = parse(xml);
        var attachment = song.getLine(0).getElement(1).findAttachment(TempoChangeAttachment.class);

        assertThat(attachment).as("the second direction must build after the first was dropped").isNotNull();
        assertThat(attachment.getTempo().getVisibleTempo())
            .as("no state must leak from the dropped direction")
            .isEqualTo(TEMPO_BPM);
    }

    // -------------------------------------------------------------------------
    // Annotation <direction> shapes a SongScribe-written file never contains: the
    // writer always gives an annotation direction a <words> child and always
    // follows it with the element it binds to. A file from another program can
    // break either rule, and neither may corrupt or silently lose the read.
    // -------------------------------------------------------------------------

    @Test
    void testUnboundAnnotationAtPartEndIsDropped() throws Exception {
        // The annotation direction is the last thing in the part, so no element is
        // ever appended for it to bind to and AnnotationResolver drops it at
        // part-end flush.
        var xml = scoreWithMeasureBody(
            """
                      <note>
                        <pitch><step>B</step><octave>4</octave></pitch>
                        <duration>480</duration>
                        <type>quarter</type>
                      </note>
                      <barline location="right"><bar-style>light-heavy</bar-style></barline>
                """
            + annotationDirection(FIRST_ANNOTATION_TEXT)
        );

        var song = parse(xml);

        assertThat(song.lineCount()).as("parsing completes with one line").isEqualTo(1);
        assertThat(song.getLine(0).getElements())
            .as("an annotation direction with no element after it must bind to nothing")
            .allSatisfy(element ->
                assertThat(element.findAttachment(AnnotationAttachment.class)).isNull());
    }

    @Test
    void testAnnotationDirectionWithoutWordsBindsNothing() throws Exception {
        // A placement makes this an annotation direction, but a <rehearsal> child
        // leaves it with no text to build an Annotation from. The next note must
        // come through unannotated rather than picking up an empty annotation.
        var xml = scoreWithMeasureBody(
            """
                      <direction placement="above">
                        <direction-type><rehearsal>A</rehearsal></direction-type>
                      </direction>
                      <note>
                        <pitch><step>B</step><octave>4</octave></pitch>
                        <duration>480</duration>
                        <type>quarter</type>
                      </note>
                      <barline location="right"><bar-style>light-heavy</bar-style></barline>
                """
        );

        var song = parse(xml);

        assertThat(song.getLine(0).getElement(0).findAttachment(AnnotationAttachment.class))
            .as("an annotation direction with no <words> must build no annotation")
            .isNull();
    }

    @Test
    void testSecondAnnotationDirectionReplacesTheUnboundFirst() throws Exception {
        // Two annotation directions before one note. The resolver holds a single
        // pending annotation, so the second replaces the first (logged, not
        // silent) and the note ends up with exactly one — not two, and not the
        // stale first.
        var xml = scoreWithMeasureBody(
            annotationDirection(FIRST_ANNOTATION_TEXT)
            + annotationDirection(SECOND_ANNOTATION_TEXT)
            + """
                      <note>
                        <pitch><step>B</step><octave>4</octave></pitch>
                        <duration>480</duration>
                        <type>quarter</type>
                      </note>
                      <barline location="right"><bar-style>light-heavy</bar-style></barline>
                """
        );

        var song = parse(xml);
        var note = song.getLine(0).getElement(0);

        assertThat(note.getAttachments())
            .as("the single pending slot can leave at most one annotation on the note")
            .filteredOn(AnnotationAttachment.class::isInstance)
            .hasSize(1);

        var attachment = note.findAttachment(AnnotationAttachment.class);
        assertThat(attachment).as("the note must carry the surviving annotation").isNotNull();
        assertThat(attachment.getAnnotation().getAnnotation())
            .as("the second direction replaces the first in the pending slot")
            .isEqualTo(SECOND_ANNOTATION_TEXT);
    }

    /**
     * An annotation {@code <direction>} carrying {@code text}, in the shape the
     * writer emits: a placement plus a single {@code <words>} child.
     */
    private static String annotationDirection(String text) {
        return
            "      <direction placement=\"above\">\n" +
            "        <direction-type>" +
            "<words halign=\"left\" justify=\"left\" relative-y=\"0\">" + text + "</words>" +
            "</direction-type>\n" +
            "      </direction>\n";
    }

    // -------------------------------------------------------------------------
    // Phase 7: head <miscellaneous-field> error and treat-as-absent paths.
    //
    // A malformed enum/number value in a head field must throw (fail hard,
    // matching the reader's parse-or-throw convention), while a malformed date
    // is treated as absent (the model tolerates a blank date rather than
    // aborting the load).
    // -------------------------------------------------------------------------

    @Test
    void testMalformedLyricsSourceThrows() {
        var xml = scoreWithMiscellaneousField(MusicXmlTags.MISC_LYRICS_SOURCE, "bogus");

        assertThatThrownBy(() -> parse(xml))
            .isInstanceOf(SAXException.class)
            .hasMessageContaining(MusicXmlTags.MISC_LYRICS_SOURCE);
    }

    @Test
    void testMalformedRowHeightAdjustmentThrows() {
        var xml = scoreWithMiscellaneousField(MusicXmlTags.MISC_ROW_HEIGHT_ADJUSTMENT, "abc");

        assertThatThrownBy(() -> parse(xml))
            .isInstanceOf(SAXException.class)
            .hasMessageContaining(MusicXmlTags.MISC_ROW_HEIGHT_ADJUSTMENT);
    }

    @Test
    void testMalformedSubAttributionFontSizeThrows() {
        var xml = scoreWithMiscellaneousField(MusicXmlTags.MISC_SUB_ATTRIBUTION_FONT_SIZE, "abc");

        assertThatThrownBy(() -> parse(xml))
            .isInstanceOf(SAXException.class)
            .hasMessageContaining(MusicXmlTags.MISC_SUB_ATTRIBUTION_FONT_SIZE);
    }

    @Test
    void testMalformedCompositionDateIsTreatedAsAbsent() throws Exception {
        var xml = scoreWithMiscellaneousField(MusicXmlTags.MISC_COMPOSITION_DATE, "not-a-date");

        var song = parse(xml);

        // A malformed date parses to null in DateUtils, so the head date scratch
        // stays at its empty default and the reloaded song carries a blank date
        // rather than the load aborting.
        assertThat(song.getYear())
            .as("a malformed composition-date must reload as a blank year, not throw")
            .isEmpty();
        assertThat(song.getMonth())
            .as("a malformed composition-date must reload with month 0")
            .isZero();
        assertThat(song.getDay())
            .as("a malformed composition-date must reload with day 0")
            .isZero();
    }

    // -------------------------------------------------------------------------
    // Provenance/version characterization. Each case pins the exception type AND the
    // value it carries, because SongFileLoader turns those into different results for
    // the user — a foreign file, an unsupported format, a damaged file — and a
    // wrong-disjunct bug shows up only in the captured value.
    // -------------------------------------------------------------------------

    @Test
    void testForeignSoftwareThrows() {
        var xml = scoreWithSoftware("<software>" + FOREIGN_SOFTWARE + "</software>");

        var exception = assertThrows(MusicXmlReader.ForeignSoftwareException.class, () -> parse(xml));
        // Assert the captured value, not just the type: foreign and missing both
        // throw ForeignSoftwareException, so a wrong-disjunct bug would pass a
        // type-only check.
        assertThat(exception.software()).isEqualTo(FOREIGN_SOFTWARE);
    }

    /**
     * Foreign provenance <em>and</em> content a mapper rejects, in one document. Both
     * failures are available; the provenance gate runs first, so the user is told what
     * the file is rather than what is wrong inside it — "not a SongScribe file", not
     * "damaged file". Reversing the two gates fails here with
     * {@link MusicXmlReader.UnsupportedFormatException}.
     */
    @Test
    void testForeignSoftwareIsRejectedBeforeContentIsMapped() {
        var xml = scoreWithSoftwareAndBody(
            "<software>" + FOREIGN_SOFTWARE + "</software>", ONE_UNMAPPABLE_NOTE_BODY);

        var exception = assertThrows(MusicXmlReader.ForeignSoftwareException.class, () -> parse(xml));
        assertThat(exception.software()).isEqualTo(FOREIGN_SOFTWARE);
    }

    @Test
    void testBlankSoftwareThrows() {
        // A present-but-blank <software> is caught by checkProvenance's isBlank()
        // disjunct and carries "" — a distinct disjunct, and a distinct captured
        // value, from the missing-tag (null) case below.
        var xml = scoreWithSoftware("<software></software>");

        var exception = assertThrows(MusicXmlReader.ForeignSoftwareException.class, () -> parse(xml));
        assertThat(exception.software()).isBlank();
    }

    @Test
    void testMissingSoftwareThrows() {
        // No <identification>/<software> block at all → software() is null, the third
        // disjunct. The body below is a valid one-note part only so that nothing but the
        // missing tag can account for the failure.
        var xml =
            """
                <?xml version="1.0" encoding="UTF-8"?>
                <score-partwise version="4.0">
                  <part-list>
                    <score-part id="P1"><part-name></part-name></score-part>
                  </part-list>
                  <part id="P1">
                    <measure number="1">
                      <print new-system="yes"/>
                      <attributes>
                        <divisions>480</divisions>
                        <key><fifths>0</fifths></key>
                        <time print-object="no"><senza-misura/></time>
                        <clef><sign>G</sign><line>2</line></clef>
                      </attributes>
                      <note>
                        <pitch><step>B</step><octave>4</octave></pitch>
                        <duration>480</duration>
                        <type>quarter</type>
                      </note>
                      <barline location="right"><bar-style>light-heavy</bar-style></barline>
                    </measure>
                  </part>
                </score-partwise>
                """;

        var exception = assertThrows(MusicXmlReader.ForeignSoftwareException.class, () -> parse(xml));
        // Missing tag → software() is null, distinguishing it from the blank case.
        assertThat(exception.software()).isNull();
    }

    @Test
    void testMissingVersionThrows() {
        var xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<score-partwise></score-partwise>\n";

        var exception = assertThrows(MusicXmlReader.UnsupportedFormatException.class, () -> parse(xml));
        assertThat(exception.detail()).isEqualTo("missing version attribute");
    }

    @Test
    void testUnparseableVersionThrows() {
        var xml =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<score-partwise version=\"abc\"></score-partwise>\n";

        var exception = assertThrows(MusicXmlReader.UnsupportedFormatException.class, () -> parse(xml));
        // detail() names the offending value, distinguishing this branch from the
        // missing/too-old branches that share the exception type.
        assertThat(exception.detail()).contains("abc");
    }

    @Test
    void testTooOldVersionThrows() {
        var xml =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<score-partwise version=\"1.0\"></score-partwise>\n";

        var exception = assertThrows(MusicXmlReader.UnsupportedFormatException.class, () -> parse(xml));
        assertThat(exception.detail()).contains("1.0");
    }

    private static String scoreWithSoftware(String softwareElement) {
        return scoreWithSoftwareAndBody(softwareElement, ONE_VALID_NOTE_BODY);
    }

    /**
     * As {@link #scoreWithSoftware}, with the measure body given too — for the case that
     * has to make the provenance gate and a mapper fail on the same document.
     */
    private static String scoreWithSoftwareAndBody(String softwareElement, String measureBody) {
        return scoreWithMeasureBody(measureBody).replace(
            "<software>" + Constants.PACKAGE_NAME + "</software>",
            softwareElement
        );
    }
}
