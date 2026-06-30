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

import javax.xml.parsers.DocumentBuilderFactory;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import songscribe.dom.ElementType;
import songscribe.dom.Tie;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;

/**
 * Round-trip tests for the per-note range spans {@code Tie}, {@code Tuplet}, and
 * {@code Trill}, plus the mid-line and measure-boundary-crossing cases. (Beams
 * live in their own class because the hook/level logic is the densest part.)
 */
class MusicXmlSpanRoundTripTest extends MusicXmlRoundTripSupport {

    /**
     * Returns the text content of the {@code <actual-notes>} element from the
     * first {@code <time-modification>} in the document, or {@code null} if absent.
     */
    private static @Nullable String firstActualNotes(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        var doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        var nodes = doc.getElementsByTagName("actual-notes");
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent().trim();
    }

    /**
     * Returns the text content of the {@code <normal-notes>} element from the
     * first {@code <time-modification>} in the document, or {@code null} if absent.
     */
    private static @Nullable String firstNormalNotes(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        var doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        var nodes = doc.getElementsByTagName("normal-notes");
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent().trim();
    }

    /** Grade 3 (triplet): 3 actual notes in the time of 2 normal notes. */
    private static final int TRIPLET_GRADE = 3;

    /** Normal-note count for a triplet: largest power of two below 3. */
    private static final int TRIPLET_NORMAL_NOTES = 2;

    /** Grade 5 (quintuplet): 5 actual notes in the time of 4 normal notes. */
    private static final int QUINTUPLET_GRADE = 5;

    /** Normal-note count for a quintuplet: largest power of two below 5. */
    private static final int QUINTUPLET_NORMAL_NOTES = 4;

    /** A non-zero vertical position (staff spaces) that survives the round-trip. */
    private static final int TUPLET_VERTICAL_POSITION_SS = 2;

    /** A non-zero Y position (staff spaces) that survives the round-trip. */
    private static final int TRILL_Y_POSITION_SS = 3;

    // -------------------------------------------------------------------------
    // Range-Spans Phase 6b: tie round-trip tests
    //
    // A Tie is a pure index-pair span with no extra fields.  The reader's
    // addTie() merge logic collapses the interior stop+start pairs that the
    // writer emits on interior notes of a chain into one Tie(firstAnchor,
    // lastEnd).
    // -------------------------------------------------------------------------

    @Test
    void testTwoNoteTieRoundTrips() throws Exception {
        var song = buildSong(line -> {
            var note0 = ElementType.CROTCHET.newInstance();
            var note1 = ElementType.CROTCHET.newInstance();
            line.addElement(note0);
            line.addElement(note1);
            line.addTie(new Tie(note0, note1));
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var ties = line2.findTies();

        assertThat(ties).as("tie count after two-note tie round-trip").hasSize(1);
        assertRangeElementEquals(ties.get(0), 0, 1);
    }

    @Test
    void testThreeNoteTieChainRoundTrips() throws Exception {
        // The writer emits stop+start on the interior note (note1): the reader's
        // addTie() merge logic collapses the two adjacent pairs into one
        // Tie(note0, note2) covering the whole chain.
        var song = buildSong(line -> {
            var note0 = ElementType.CROTCHET.newInstance();
            var note1 = ElementType.CROTCHET.newInstance();
            var note2 = ElementType.CROTCHET.newInstance();
            line.addElement(note0);
            line.addElement(note1);
            line.addElement(note2);
            // addTie merges the two pairs into one span at build time.
            line.addTie(new Tie(note0, note1));
            line.addTie(new Tie(note1, note2));
        });

        // After the first two addTie calls the line already holds one merged
        // Tie(note0, note2); confirm the round-trip re-collapses to the same.
        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var ties = line2.findTies();

        assertThat(ties).as("tie count after three-note chain round-trip").hasSize(1);
        assertRangeElementEquals(ties.get(0), 0, 2);
    }

    // -------------------------------------------------------------------------
    // Range-Spans Phase 6b: tuplet round-trip tests
    //
    // Asserts that grade and verticalPositionSs survive the write→read cycle,
    // plus writer-output assertions for <actual-notes> and <normal-notes>
    // (normal-notes is write-forward only: the reader ignores it, so
    // round-trip alone cannot catch a wrong value).
    // -------------------------------------------------------------------------

    @Test
    void testTripletRoundTrips() throws Exception {
        var song = buildSong(line -> {
            var note0 = ElementType.CROTCHET.newInstance();
            var note1 = ElementType.CROTCHET.newInstance();
            var note2 = ElementType.CROTCHET.newInstance();
            line.addElement(note0);
            line.addElement(note1);
            line.addElement(note2);
            line.addTuplet(new Tuplet(note0, note2, TRIPLET_GRADE));
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var tuplets = line2.findRangeElements(Tuplet.class);

        assertThat(tuplets).as("tuplet count after triplet round-trip").hasSize(1);
        assertRangeElementEquals(tuplets.get(0), 0, 2, TRIPLET_GRADE, 0);
    }

    @Test
    void testTripletWithVerticalPositionRoundTrips() throws Exception {
        var song = buildSong(line -> {
            var note0 = ElementType.CROTCHET.newInstance();
            var note1 = ElementType.CROTCHET.newInstance();
            var note2 = ElementType.CROTCHET.newInstance();
            line.addElement(note0);
            line.addElement(note1);
            line.addElement(note2);
            var tuplet = new Tuplet(note0, note2, TRIPLET_GRADE);
            tuplet.setVerticalPositionSs(TUPLET_VERTICAL_POSITION_SS);
            line.addTuplet(tuplet);
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var tuplets = line2.findRangeElements(Tuplet.class);

        assertThat(tuplets).as("tuplet count after triplet+verticalPos round-trip").hasSize(1);
        assertRangeElementEquals(tuplets.get(0), 0, 2, TRIPLET_GRADE, TUPLET_VERTICAL_POSITION_SS);
    }

    @Test
    void testQuintupletRoundTrips() throws Exception {
        var song = buildSong(line -> {
            var note0 = ElementType.CROTCHET.newInstance();
            var note1 = ElementType.CROTCHET.newInstance();
            var note2 = ElementType.CROTCHET.newInstance();
            var note3 = ElementType.CROTCHET.newInstance();
            var note4 = ElementType.CROTCHET.newInstance();
            line.addElement(note0);
            line.addElement(note1);
            line.addElement(note2);
            line.addElement(note3);
            line.addElement(note4);
            line.addTuplet(new Tuplet(note0, note4, QUINTUPLET_GRADE));
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var tuplets = line2.findRangeElements(Tuplet.class);

        assertThat(tuplets).as("tuplet count after quintuplet round-trip").hasSize(1);
        assertRangeElementEquals(tuplets.get(0), 0, 4, QUINTUPLET_GRADE, 0);
    }

    @Test
    void testQuintupletWithVerticalPositionRoundTrips() throws Exception {
        var song = buildSong(line -> {
            var note0 = ElementType.CROTCHET.newInstance();
            var note1 = ElementType.CROTCHET.newInstance();
            var note2 = ElementType.CROTCHET.newInstance();
            var note3 = ElementType.CROTCHET.newInstance();
            var note4 = ElementType.CROTCHET.newInstance();
            line.addElement(note0);
            line.addElement(note1);
            line.addElement(note2);
            line.addElement(note3);
            line.addElement(note4);
            var tuplet = new Tuplet(note0, note4, QUINTUPLET_GRADE);
            tuplet.setVerticalPositionSs(TUPLET_VERTICAL_POSITION_SS);
            line.addTuplet(tuplet);
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var tuplets = line2.findRangeElements(Tuplet.class);

        assertThat(tuplets).as("tuplet count after quintuplet+verticalPos round-trip").hasSize(1);
        assertRangeElementEquals(tuplets.get(0), 0, 4, QUINTUPLET_GRADE, TUPLET_VERTICAL_POSITION_SS);
    }

    @Test
    void testTripletTimeModificationInOutput() throws Exception {
        // <normal-notes> is write-forward only: the reader ignores it, so only
        // a writer-output assertion can verify it is emitted correctly.
        var song = buildSong(line -> {
            var note0 = ElementType.CROTCHET.newInstance();
            var note1 = ElementType.CROTCHET.newInstance();
            var note2 = ElementType.CROTCHET.newInstance();
            line.addElement(note0);
            line.addElement(note1);
            line.addElement(note2);
            line.addTuplet(new Tuplet(note0, note2, TRIPLET_GRADE));
        });

        var xml = writeToString(song);

        assertThat(firstActualNotes(xml))
            .as("<actual-notes> for triplet must equal the grade (%d)", TRIPLET_GRADE)
            .isEqualTo(Integer.toString(TRIPLET_GRADE));
        assertThat(firstNormalNotes(xml))
            .as("<normal-notes> for triplet must be the largest power of two below grade (%d)", TRIPLET_NORMAL_NOTES)
            .isEqualTo(Integer.toString(TRIPLET_NORMAL_NOTES));
    }

    @Test
    void testQuintupletTimeModificationInOutput() throws Exception {
        var song = buildSong(line -> {
            var note0 = ElementType.CROTCHET.newInstance();
            var note1 = ElementType.CROTCHET.newInstance();
            var note2 = ElementType.CROTCHET.newInstance();
            var note3 = ElementType.CROTCHET.newInstance();
            var note4 = ElementType.CROTCHET.newInstance();
            line.addElement(note0);
            line.addElement(note1);
            line.addElement(note2);
            line.addElement(note3);
            line.addElement(note4);
            line.addTuplet(new Tuplet(note0, note4, QUINTUPLET_GRADE));
        });

        var xml = writeToString(song);

        assertThat(firstActualNotes(xml))
            .as("<actual-notes> for quintuplet must equal the grade (%d)", QUINTUPLET_GRADE)
            .isEqualTo(Integer.toString(QUINTUPLET_GRADE));
        assertThat(firstNormalNotes(xml))
            .as("<normal-notes> for quintuplet must be the largest power of two below grade (%d)", QUINTUPLET_NORMAL_NOTES)
            .isEqualTo(Integer.toString(QUINTUPLET_NORMAL_NOTES));
    }

    // -------------------------------------------------------------------------
    // Range-Spans Phase 6b: trill round-trip tests
    //
    // Single-note trills (anchor == end) and multi-note trills, with and
    // without a non-zero yPositionSs offset.
    // -------------------------------------------------------------------------

    @Test
    void testSingleNoteTrillRoundTrips() throws Exception {
        // anchor == end: the single-note constructor Trill(anchor) is equivalent.
        var song = buildSong(line -> {
            var note0 = ElementType.CROTCHET.newInstance();
            line.addElement(note0);
            line.addRangeElement(new Trill(note0));
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var trills = line2.findRangeElements(Trill.class);

        assertThat(trills).as("trill count after single-note trill round-trip").hasSize(1);
        assertRangeElementEquals(trills.get(0), 0, 0, 0);
    }

    @Test
    void testMultiNoteTrillRoundTrips() throws Exception {
        var song = buildSong(line -> {
            var note0 = ElementType.CROTCHET.newInstance();
            var note1 = ElementType.CROTCHET.newInstance();
            var note2 = ElementType.CROTCHET.newInstance();
            line.addElement(note0);
            line.addElement(note1);
            line.addElement(note2);
            line.addRangeElement(new Trill(note0, note2));
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var trills = line2.findRangeElements(Trill.class);

        assertThat(trills).as("trill count after multi-note trill round-trip").hasSize(1);
        assertRangeElementEquals(trills.get(0), 0, 2, 0);
    }

    @Test
    void testSingleNoteTrillWithYPositionRoundTrips() throws Exception {
        var song = buildSong(line -> {
            var note0 = ElementType.CROTCHET.newInstance();
            line.addElement(note0);
            var trill = new Trill(note0);
            trill.setYPositionSs(TRILL_Y_POSITION_SS);
            line.addRangeElement(trill);
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var trills = line2.findRangeElements(Trill.class);

        assertThat(trills).as("trill count after single-note trill+yPos round-trip").hasSize(1);
        assertRangeElementEquals(trills.get(0), 0, 0, TRILL_Y_POSITION_SS);
    }

    @Test
    void testMultiNoteTrillWithYPositionRoundTrips() throws Exception {
        var song = buildSong(line -> {
            var note0 = ElementType.CROTCHET.newInstance();
            var note1 = ElementType.CROTCHET.newInstance();
            line.addElement(note0);
            line.addElement(note1);
            var trill = new Trill(note0, note1);
            trill.setYPositionSs(TRILL_Y_POSITION_SS);
            line.addRangeElement(trill);
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var trills = line2.findRangeElements(Trill.class);

        assertThat(trills).as("trill count after multi-note trill+yPos round-trip").hasSize(1);
        assertRangeElementEquals(trills.get(0), 0, 1, TRILL_Y_POSITION_SS);
    }

    // -------------------------------------------------------------------------
    // Range-Spans Phase 6b: mid-line and measure-boundary-crossing span tests
    //
    // Mid-line: a span surrounded by notes it does not cover — verifies that
    // the per-index lookup does not assign markers to the wrong notes.
    //
    // Measure-boundary: a SINGLE_BARLINE sits between anchor and end, splitting
    // the span across two MusicXML measures.  The reader must still re-collapse
    // the markers to the correct (anchor, end) index pair on the same line.
    // -------------------------------------------------------------------------

    @Test
    void testMidLineSpanRoundTrips() throws Exception {
        // Layout: note0 note1(anchor) note2(end) note3
        // Indices:   0      1            2          3
        // The trill covers only [1, 2]; notes 0 and 3 must not be included.
        var song = buildSong(line -> {
            var note0 = ElementType.CROTCHET.newInstance();
            var note1 = ElementType.CROTCHET.newInstance();
            var note2 = ElementType.CROTCHET.newInstance();
            var note3 = ElementType.CROTCHET.newInstance();
            line.addElement(note0);
            line.addElement(note1);
            line.addElement(note2);
            line.addElement(note3);
            line.addRangeElement(new Trill(note1, note2));
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var trills = line2.findRangeElements(Trill.class);

        assertThat(trills).as("trill count after mid-line round-trip").hasSize(1);
        assertRangeElementEquals(trills.get(0), 1, 2, "mid-line trill");
    }

    @Test
    void testMeasureBoundaryCrossingTieRoundTrips() throws Exception {
        // Layout: note0(index 0) SINGLE_BARLINE(index 1) note1(index 2)
        // The tie crosses the barline; the writer emits the start on note0
        // (in measure 1) and the stop on note1 (in measure 2).  The reader
        // must re-collapse to Tie[0, 2].
        var song = buildSong(line -> {
            var note0 = ElementType.CROTCHET.newInstance();
            var barline = ElementType.SINGLE_BARLINE.newInstance();
            var note1 = ElementType.CROTCHET.newInstance();
            line.addElement(note0);
            line.addElement(barline);
            line.addElement(note1);
            line.addTie(new Tie(note0, note1));
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var ties = line2.findTies();

        assertThat(ties).as("tie count after measure-boundary round-trip").hasSize(1);
        // note0 → index 0, SINGLE_BARLINE → index 1, note1 → index 2.
        assertRangeElementEquals(ties.get(0), 0, 2);
    }

    @Test
    void testMeasureBoundaryCrossingTupletRoundTrips() throws Exception {
        // Layout: note0(index 0) SINGLE_BARLINE(index 1) note1(index 2)
        // The triplet straddles the barline: the writer emits <time-modification>
        // on both notes and the <tuplet> start/stop notations on note0 (measure 1)
        // and note1 (measure 2).  The reader must recover the grade from
        // <time-modification> and re-collapse the notations to Tuplet[0, 2].
        var song = buildSong(line -> {
            var note0 = ElementType.CROTCHET.newInstance();
            var barline = ElementType.SINGLE_BARLINE.newInstance();
            var note1 = ElementType.CROTCHET.newInstance();
            line.addElement(note0);
            line.addElement(barline);
            line.addElement(note1);
            line.addTuplet(new Tuplet(note0, note1, TRIPLET_GRADE));
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var tuplets = line2.findRangeElements(Tuplet.class);

        assertThat(tuplets).as("tuplet count after measure-boundary round-trip").hasSize(1);
        // note0 → index 0, SINGLE_BARLINE → index 1, note1 → index 2.
        assertRangeElementEquals(tuplets.get(0), 0, 2, TRIPLET_GRADE, 0);
    }
}
