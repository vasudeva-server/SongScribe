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

import java.io.StringReader;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilderFactory;

import org.assertj.core.data.Offset;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import songscribe.dom.Crescendo;
import songscribe.dom.Diminuendo;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.ElementType;
import songscribe.dom.Hairpin;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;

class MusicXmlHairpinRoundTripTest extends MusicXmlRoundTripSupport {

    /**
     * A non-zero x1 shift (staff spaces) that survives the round-trip.
     * Must be an integer value because the reader rounds tenths back to
     * the nearest integer staff-space (tenthsToSs uses Math.round).
     */
    private static final double HAIRPIN_X1_SHIFT_SS = 2.0;

    /**
     * A non-zero x2 shift (staff spaces) that survives the round-trip.
     * Must be an integer value (see HAIRPIN_X1_SHIFT_SS).
     */
    private static final double HAIRPIN_X2_SHIFT_SS = -1.0;

    /**
     * A non-zero y shift (staff spaces) that survives the round-trip.
     * Must be an integer value (see HAIRPIN_X1_SHIFT_SS).
     */
    private static final double HAIRPIN_Y_SHIFT_SS = 3.0;

    /** MusicXML coordinate unit: 1 staff space = 10 tenths (wedge geometry). */
    private static final int WEDGE_TENTHS_PER_SS = 10;

    // -------------------------------------------------------------------------
    // Range-Spans Phase 7a: hairpin assertion helper
    //
    // Compares a round-tripped Hairpin to expected values field-by-field without
    // adding equals()/hashCode() to Hairpin/StaffElement (same constraint as the
    // beam/tie/tuplet/trill helpers above).
    // -------------------------------------------------------------------------

    /**
     * Asserts that {@code actual} is an instance of {@code expectedClass}, its
     * anchor/end element indices match, and all three shift fields survive the
     * round-trip.  The {@code context} string names the case in failure output.
     */
    private static void assertHairpinEquals(
            Hairpin actual,
            Class<? extends Hairpin> expectedClass,
            int expectedAnchor,
            int expectedEnd,
            double expectedX1ShiftSs,
            double expectedX2ShiftSs,
            double expectedYShiftSs,
            String context) {
        assertThat(actual).as("%s: subclass", context).isInstanceOf(expectedClass);
        assertThat(actual.getAnchorElementIndex()).as("%s: anchor index", context).isEqualTo(expectedAnchor);
        assertThat(actual.getEndElementIndex()).as("%s: end index", context).isEqualTo(expectedEnd);
        assertThat(actual.getX1ShiftSs()).as("%s: x1ShiftSs", context).isEqualTo(expectedX1ShiftSs);
        assertThat(actual.getX2ShiftSs()).as("%s: x2ShiftSs", context).isEqualTo(expectedX2ShiftSs);
        assertThat(actual.getYShiftSs()).as("%s: yShiftSs", context).isEqualTo(expectedYShiftSs);
    }

    // -------------------------------------------------------------------------
    // Range-Spans Phase 7a: wedge writer-output helper
    //
    // Returns the value of the named attribute on the first <wedge> element in
    // document order whose @type matches wedgeType, or null if none is found or
    // the attribute is absent.  Used by the writer-output assertion tests below.
    // -------------------------------------------------------------------------

    /**
     * Returns the value of {@code attrName} on the first {@code <wedge>} element
     * whose {@code type} attribute equals {@code wedgeType}, or {@code null} when
     * no matching wedge is present or the attribute is absent.
     * <p>
     * Wedge type values: {@code "crescendo"}, {@code "diminuendo"}, {@code "stop"}.
     */
    private static @Nullable String wedgeAttribute(String xml, String wedgeType, String attrName)
            throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        var doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        var wedges = doc.getElementsByTagName("wedge");

        for (var i = 0; i < wedges.getLength(); i++) {
            var wedge = (Element) wedges.item(i);

            if (wedgeType.equals(wedge.getAttribute("type"))) {
                var value = wedge.getAttribute(attrName);
                return value.isEmpty() ? null : value;
            }
        }

        return null;
    }

    // -------------------------------------------------------------------------
    // Range-Spans Phase 7a: crescendo round-trip tests
    // -------------------------------------------------------------------------

    @Test
    void testCrescendoRoundTrips() throws Exception {
        var song = buildSong(line -> {
            var note0 = ElementType.CROTCHET.newInstance();
            var note1 = ElementType.CROTCHET.newInstance();
            line.addElement(note0);
            line.addElement(note1);
            line.addCrescendo(new Crescendo(note0, note1));
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var crescendos = line2.getCrescendos();

        assertThat(crescendos).as("crescendo count after round-trip").hasSize(1);
        assertHairpinEquals(crescendos.getFirst(), Crescendo.class, 0, 1, 0.0, 0.0, 0.0, "crescendo");
    }

    @Test
    void testCrescendoWithShiftsRoundTrips() throws Exception {
        var song = buildSong(line -> {
            var note0 = ElementType.CROTCHET.newInstance();
            var note1 = ElementType.CROTCHET.newInstance();
            line.addElement(note0);
            line.addElement(note1);
            var crescendo = new Crescendo(note0, note1);
            crescendo.setX1ShiftSs(HAIRPIN_X1_SHIFT_SS);
            crescendo.setX2ShiftSs(HAIRPIN_X2_SHIFT_SS);
            crescendo.setYShiftSs(HAIRPIN_Y_SHIFT_SS);
            line.addCrescendo(crescendo);
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var crescendos = line2.getCrescendos();

        assertThat(crescendos).as("crescendo count after shift round-trip").hasSize(1);
        assertHairpinEquals(
            crescendos.getFirst(), Crescendo.class, 0, 1,
            HAIRPIN_X1_SHIFT_SS, HAIRPIN_X2_SHIFT_SS, HAIRPIN_Y_SHIFT_SS,
            "crescendo with shifts"
        );
    }

    // -------------------------------------------------------------------------
    // Range-Spans Phase 7a: diminuendo round-trip tests
    // -------------------------------------------------------------------------

    @Test
    void testDiminuendoRoundTrips() throws Exception {
        var song = buildSong(line -> {
            var note0 = ElementType.CROTCHET.newInstance();
            var note1 = ElementType.CROTCHET.newInstance();
            line.addElement(note0);
            line.addElement(note1);
            line.addDiminuendo(new Diminuendo(note0, note1));
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var diminuendos = line2.getDiminuendos();

        assertThat(diminuendos).as("diminuendo count after round-trip").hasSize(1);
        assertHairpinEquals(diminuendos.getFirst(), Diminuendo.class, 0, 1, 0.0, 0.0, 0.0, "diminuendo");
    }

    @Test
    void testDiminuendoWithShiftsRoundTrips() throws Exception {
        var song = buildSong(line -> {
            var note0 = ElementType.CROTCHET.newInstance();
            var note1 = ElementType.CROTCHET.newInstance();
            line.addElement(note0);
            line.addElement(note1);
            var diminuendo = new Diminuendo(note0, note1);
            diminuendo.setX1ShiftSs(HAIRPIN_X1_SHIFT_SS);
            diminuendo.setX2ShiftSs(HAIRPIN_X2_SHIFT_SS);
            diminuendo.setYShiftSs(HAIRPIN_Y_SHIFT_SS);
            line.addDiminuendo(diminuendo);
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var diminuendos = line2.getDiminuendos();

        assertThat(diminuendos).as("diminuendo count after shift round-trip").hasSize(1);
        assertHairpinEquals(
            diminuendos.getFirst(), Diminuendo.class, 0, 1,
            HAIRPIN_X1_SHIFT_SS, HAIRPIN_X2_SHIFT_SS, HAIRPIN_Y_SHIFT_SS,
            "diminuendo with shifts"
        );
    }

    // -------------------------------------------------------------------------
    // Range-Spans Phase 7a: wedge writer-output assertion tests
    //
    // The writer emits x1ShiftSs as relative-x on the start wedge, x2ShiftSs
    // as relative-x on the stop wedge, and yShiftSs as relative-y on the start
    // wedge.  These are write-forward assertions: round-trip alone cannot catch
    // a wrong placement.
    // -------------------------------------------------------------------------

    @Test
    void testCrescendoShiftsAreOnCorrectWedgesInOutput() throws Exception {
        // x1 → start wedge relative-x; x2 → stop wedge relative-x; y → start wedge relative-y.
        var song = buildSong(line -> {
            var note0 = ElementType.CROTCHET.newInstance();
            var note1 = ElementType.CROTCHET.newInstance();
            line.addElement(note0);
            line.addElement(note1);
            var crescendo = new Crescendo(note0, note1);
            crescendo.setX1ShiftSs(HAIRPIN_X1_SHIFT_SS);
            crescendo.setX2ShiftSs(HAIRPIN_X2_SHIFT_SS);
            crescendo.setYShiftSs(HAIRPIN_Y_SHIFT_SS);
            line.addCrescendo(crescendo);
        });

        var xml = writeToString(song);

        // Start wedge (type="crescendo") carries x1 as relative-x and y as relative-y.
        var startRelativeX = wedgeAttribute(xml, "crescendo", "relative-x");
        var startRelativeY = wedgeAttribute(xml, "crescendo", "relative-y");
        assertThat(startRelativeX).as("start wedge relative-x must be present").isNotNull();
        assertThat(startRelativeY).as("start wedge relative-y must be present").isNotNull();
        assertThat(Double.parseDouble(startRelativeX))
            .as("start wedge relative-x must equal x1ShiftSs × 10 tenths")
            .isCloseTo(HAIRPIN_X1_SHIFT_SS * WEDGE_TENTHS_PER_SS, Offset.offset(0.01));
        assertThat(Double.parseDouble(startRelativeY))
            .as("start wedge relative-y must equal yShiftSs × 10 tenths")
            .isCloseTo(HAIRPIN_Y_SHIFT_SS * WEDGE_TENTHS_PER_SS, Offset.offset(0.01));

        // Stop wedge (type="stop") carries x2 as relative-x; y is NOT on the stop wedge.
        var stopRelativeX = wedgeAttribute(xml, "stop", "relative-x");
        assertThat(stopRelativeX).as("stop wedge relative-x must be present").isNotNull();
        assertThat(Double.parseDouble(stopRelativeX))
            .as("stop wedge relative-x must equal x2ShiftSs × 10 tenths")
            .isCloseTo(HAIRPIN_X2_SHIFT_SS * WEDGE_TENTHS_PER_SS, Offset.offset(0.01));

        // y must NOT appear on the stop wedge.
        assertThat(wedgeAttribute(xml, "stop", "relative-y"))
            .as("stop wedge must not carry relative-y")
            .isNull();
    }

    // -------------------------------------------------------------------------
    // Range-Spans Phase 7a: wedge edge-case tests
    // -------------------------------------------------------------------------

    @Test
    void testBackToBackCrescendosLoadAsOneMergedCrescendo() throws Exception {
        // Two crescendos on one line: first spans [0,1], second spans [2,3], so the
        // stop wedge of the first is adjacent to the start wedge of the second (placed
        // before note1 and note2 respectively in the emitted XML).
        //
        // Back-to-back crescendos say nothing a single wider one does not, so they are
        // not a state the model holds: addCrescendo merges them however they arise —
        // drawn, pasted, or read back from a file another program wrote. Building this
        // fixture therefore needs the raw addSpan to get two wedges into the
        // XML at all; what the round-trip asserts is that reading them merges them.
        var song = buildSong(line -> {
            var note0 = ElementType.CROTCHET.newInstance();
            var note1 = ElementType.CROTCHET.newInstance();
            var note2 = ElementType.CROTCHET.newInstance();
            var note3 = ElementType.CROTCHET.newInstance();
            line.addElement(note0);
            line.addElement(note1);
            line.addElement(note2);
            line.addElement(note3);
            line.addSpan(new Crescendo(note0, note1));
            line.addSpan(new Crescendo(note2, note3));
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var crescendos = line2.getCrescendos();

        assertThat(crescendos)
            .as("two back-to-back wedges in the file must load as one crescendo")
            .hasSize(1);
        assertHairpinEquals(crescendos.getFirst(), Crescendo.class, 0, 3, 0.0, 0.0, 0.0, "merged crescendo");
    }

    @Test
    void testCrescendoAcrossMeasureBoundaryRoundTrips() throws Exception {
        // Layout: note0(0) SINGLE_BARLINE(1) note1(2)
        // The crescendo spans across the barline: anchor in measure 1, end in measure 2.
        var song = buildSong(line -> {
            var note0 = ElementType.CROTCHET.newInstance();
            var barline = ElementType.SINGLE_BARLINE.newInstance();
            var note1 = ElementType.CROTCHET.newInstance();
            line.addElement(note0);
            line.addElement(barline);
            line.addElement(note1);
            line.addCrescendo(new Crescendo(note0, note1));
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var crescendos = line2.getCrescendos();

        assertThat(crescendos).as("crescendo count after measure-boundary round-trip").hasSize(1);
        // note0 → 0, SINGLE_BARLINE → 1, note1 → 2.
        assertHairpinEquals(crescendos.getFirst(), Crescendo.class, 0, 2, 0.0, 0.0, 0.0, "measure-boundary crescendo");
    }

    @Test
    void testCrescendoAnchorIsFirstNoteOfMeasureRoundTrips() throws Exception {
        // Layout: SINGLE_BARLINE(0) note0(1) note1(2)
        // The crescendo's anchor is the first note after the barline (start of measure 2).
        var song = buildSong(line -> {
            var barline = ElementType.SINGLE_BARLINE.newInstance();
            var note0 = ElementType.CROTCHET.newInstance();
            var note1 = ElementType.CROTCHET.newInstance();
            line.addElement(barline);
            line.addElement(note0);
            line.addElement(note1);
            line.addCrescendo(new Crescendo(note0, note1));
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var crescendos = line2.getCrescendos();

        assertThat(crescendos).as("crescendo count when anchor is first note of measure").hasSize(1);
        // SINGLE_BARLINE → 0, note0 → 1, note1 → 2.
        assertHairpinEquals(crescendos.getFirst(), Crescendo.class, 1, 2, 0.0, 0.0, 0.0, "measure-start crescendo");
    }

    @Test
    void testOverlappingWedgeStartIsDropped() throws Exception {
        // Hand-crafted MusicXML with two crescendo starts before any stop:
        //   start(crescendo) → note0(anchor)    [hairpin 1 now open]
        //   start(crescendo) → note1             [dropped: hairpin already open]
        //   stop             → note2(end)        [closes hairpin 1]
        // Expected: exactly one Crescendo[0, 2]; the dropped start leaves no span.
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
            "      <direction><direction-type>" +
            "<wedge type=\"crescendo\" number=\"1\"/>" +
            "</direction-type></direction>\n" +
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>480</duration><type>quarter</type>\n" +
            "      </note>\n" +
            // Second start wedge while the first hairpin is open — must be dropped.
            "      <direction><direction-type>" +
            "<wedge type=\"crescendo\" number=\"1\"/>" +
            "</direction-type></direction>\n" +
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>480</duration><type>quarter</type>\n" +
            "      </note>\n" +
            "      <direction><direction-type>" +
            "<wedge type=\"stop\" number=\"1\"/>" +
            "</direction-type></direction>\n" +
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>480</duration><type>quarter</type>\n" +
            "      </note>\n" +
            "      <barline location=\"right\"><bar-style>none</bar-style></barline>\n" +
            "    </measure>\n" +
            "  </part>\n" +
            "</score-partwise>\n";

        var song = parse(xml);
        var line = song.getLine(0);

        // The dropped start must leave exactly one hairpin, spanning all three notes.
        var crescendos = line.getCrescendos();
        assertThat(crescendos)
            .as("exactly one crescendo must survive after the overlap drop")
            .hasSize(1);
        // note0 → index 0, note1 → index 1, note2 → index 2.
        assertHairpinEquals(crescendos.getFirst(), Crescendo.class, 0, 2, 0.0, 0.0, 0.0, "surviving crescendo");
    }

    // -------------------------------------------------------------------------
    // Back-to-back opposite-type hairpin tests
    // -------------------------------------------------------------------------

    /** Number of notes in the back-to-back opposite-hairpin fixtures. */
    private static final int BACK_TO_BACK_NOTE_COUNT = 9;

    /** Anchor index of the crescendo in the back-to-back opposite-hairpin fixtures. */
    private static final int BACK_TO_BACK_CRESCENDO_ANCHOR = 0;

    /**
     * The element both hairpins share in the back-to-back opposite-hairpin fixtures —
     * the crescendo's end and the diminuendo's anchor.
     */
    private static final int BACK_TO_BACK_SHARED_INDEX = 4;

    /** End index of the diminuendo in the back-to-back opposite-hairpin fixtures. */
    private static final int BACK_TO_BACK_DIMINUENDO_END = 8;

    private static Song buildBackToBackOppositeHairpinSong() {
        return buildSong(line -> {
            var notes = new ArrayList<StaffElement>();

            for (var i = 0; i < BACK_TO_BACK_NOTE_COUNT; i++) {
                var note = ElementType.CROTCHET.newInstance();
                line.addElement(note);
                notes.add(note);
            }

            line.addCrescendo(new Crescendo(
                notes.get(BACK_TO_BACK_CRESCENDO_ANCHOR), notes.get(BACK_TO_BACK_SHARED_INDEX)));
            line.addDiminuendo(new Diminuendo(
                notes.get(BACK_TO_BACK_SHARED_INDEX), notes.get(BACK_TO_BACK_DIMINUENDO_END)));
        });
    }

    @Test
    void testBackToBackOppositeHairpinsRoundTrip() throws Exception {
        // Crescendo [0,4] and diminuendo [4,8] share element 4 — the point where one
        // wedge stops and the next starts. Neither WedgeResolver.resolveWedge's
        // pending-stop-before-pending-start rule nor the writer's stop-before-start
        // wedge ordering may drop either hairpin.
        var song = buildBackToBackOppositeHairpinSong();

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var crescendos = line2.getCrescendos();
        var diminuendos = line2.getDiminuendos();

        assertThat(crescendos).as("crescendo count after back-to-back round-trip").hasSize(1);
        assertThat(diminuendos).as("diminuendo count after back-to-back round-trip").hasSize(1);
        assertHairpinEquals(
            crescendos.getFirst(), Crescendo.class,
            BACK_TO_BACK_CRESCENDO_ANCHOR, BACK_TO_BACK_SHARED_INDEX,
            0.0, 0.0, 0.0, "back-to-back crescendo");
        assertHairpinEquals(
            diminuendos.getFirst(), Diminuendo.class,
            BACK_TO_BACK_SHARED_INDEX, BACK_TO_BACK_DIMINUENDO_END,
            0.0, 0.0, 0.0, "back-to-back diminuendo");
    }

    @Test
    void testDynamicOnTheSharedElementOfBackToBackHairpinsRoundTrips() throws Exception {
        // A text dynamic on a hairpin bound rides inside <notations><dynamics> on the note
        // (MusicXmlNotationsWriter:54), while the wedges are <direction> siblings emitted before
        // it (MusicXmlHairpinWriter.writeHairpinWedges), so the two are orthogonal — this pins
        // that neither one's read/write path disturbs the other's.
        var song = buildBackToBackOppositeHairpinSong();
        var line = song.getLine(0);
        var sharedElement = line.getElement(BACK_TO_BACK_SHARED_INDEX);
        sharedElement.addAttachment(
            new DynamicAttachment(sharedElement, DynamicAttachment.DynamicType.FORTE));

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var crescendos = line2.getCrescendos();
        var diminuendos = line2.getDiminuendos();

        assertThat(crescendos)
            .as("crescendo count after shared-dynamic round-trip")
            .hasSize(1);
        assertThat(diminuendos)
            .as("diminuendo count after shared-dynamic round-trip")
            .hasSize(1);
        assertHairpinEquals(
            crescendos.getFirst(), Crescendo.class,
            BACK_TO_BACK_CRESCENDO_ANCHOR, BACK_TO_BACK_SHARED_INDEX,
            0.0, 0.0, 0.0, "shared-dynamic crescendo");
        assertHairpinEquals(
            diminuendos.getFirst(), Diminuendo.class,
            BACK_TO_BACK_SHARED_INDEX, BACK_TO_BACK_DIMINUENDO_END,
            0.0, 0.0, 0.0, "shared-dynamic diminuendo");

        var reloadedSharedElement = line2.getElement(BACK_TO_BACK_SHARED_INDEX);
        var reloadedDynamic = reloadedSharedElement.findAttachment(DynamicAttachment.class);
        assertThat(reloadedDynamic).as("shared element must still carry its dynamic").isNotNull();
        assertThat(reloadedDynamic.getType())
            .as("reloaded dynamic type")
            .isEqualTo(DynamicAttachment.DynamicType.FORTE);
    }

    @Test
    void testStopWedgePrecedesStartWedgeOnASharedNote() throws Exception {
        var song = buildBackToBackOppositeHairpinSong();
        var xml = writeToString(song);

        // Both wedges must be present at the shared note before their relative order
        // means anything.
        assertThat(wedgeAttribute(xml, "stop", "type")).as("stop wedge must be present").isEqualTo("stop");
        assertThat(wedgeAttribute(xml, "diminuendo", "type"))
            .as("diminuendo wedge must be present")
            .isEqualTo("diminuendo");

        var stopIndex = xml.indexOf("<wedge type=\"stop\"");
        var diminuendoIndex = xml.indexOf("<wedge type=\"diminuendo\"");
        assertThat(stopIndex).as("stop wedge must exist in the emitted XML").isNotEqualTo(-1);
        assertThat(diminuendoIndex).as("diminuendo wedge must exist in the emitted XML").isNotEqualTo(-1);
        assertThat(stopIndex)
            .as("the stop wedge must precede the diminuendo start wedge on the shared note, "
                + "else WedgeResolver's overlap guard drops the diminuendo on reload")
            .isLessThan(diminuendoIndex);
    }
}
