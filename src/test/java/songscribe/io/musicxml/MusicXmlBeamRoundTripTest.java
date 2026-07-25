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
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import songscribe.dom.Beam;
import songscribe.dom.ElementType;

class MusicXmlBeamRoundTripTest extends MusicXmlRoundTripSupport {

    /** Beam number for the primary (8th-note) beam level. */
    private static final int PRIMARY_BEAM_NUMBER = 1;

    /** Beam number for the secondary 16th-note beam level. */
    private static final int SECONDARY_BEAM_NUMBER = 2;

    /** Beam number for the tertiary 32nd-note beam level. */
    private static final int TERTIARY_BEAM_NUMBER = 3;

    // -------------------------------------------------------------------------
    // Range-Spans Phase 6a: beam writer-output helper
    //
    // Returns the text content of <beam number="beamNumber"> within the
    // noteIndex-th <note> element in document order, or null when absent.
    // Note index is 0-based; beam number is 1-based (1 = primary beam,
    // 2 = 16th level, 3 = 32nd level).
    // -------------------------------------------------------------------------

    private static @Nullable String beamValue(String xml, int noteIndex, int beamNumber)
            throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        var doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        var notes = doc.getElementsByTagName("note");

        if (noteIndex >= notes.getLength()) {
            return null;
        }

        var note = (Element) notes.item(noteIndex);
        var beams = note.getElementsByTagName("beam");

        for (var i = 0; i < beams.getLength(); i++) {
            var beam = (Element) beams.item(i);

            if (Integer.toString(beamNumber).equals(beam.getAttribute("number"))) {
                return beam.getTextContent().trim();
            }
        }

        return null;
    }

    // -------------------------------------------------------------------------
    // Range-Spans Phase 6a: beam round-trip tests
    //
    // Verify that beam spans re-collapse to the identical (anchor, end) index
    // pair after a Song → MusicXML → Song round-trip.
    // -------------------------------------------------------------------------

    @Test
    void testTwoNoteBeamRoundTrips() throws Exception {
        var song = buildSong(line -> {
            var note0 = ElementType.QUAVER.newInstance();
            var note1 = ElementType.QUAVER.newInstance();
            line.addElement(note0);
            line.addElement(note1);
            line.addBeaming(new Beam(note0, note1));
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var beams = line2.findRangeElements(Beam.class);

        assertThat(beams).as("beam count after round-trip").hasSize(1);
        assertRangeElementEquals(beams.get(0), 0, 1);
    }

    @Test
    void testFourNoteBeamRoundTrips() throws Exception {
        var song = buildSong(line -> {
            var note0 = ElementType.QUAVER.newInstance();
            var note1 = ElementType.QUAVER.newInstance();
            var note2 = ElementType.QUAVER.newInstance();
            var note3 = ElementType.QUAVER.newInstance();
            line.addElement(note0);
            line.addElement(note1);
            line.addElement(note2);
            line.addElement(note3);
            line.addBeaming(new Beam(note0, note3));
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var beams = line2.findRangeElements(Beam.class);

        assertThat(beams).as("beam count after round-trip").hasSize(1);
        assertRangeElementEquals(beams.get(0), 0, 3);
    }

    @Test
    void testTwoDisjointBeamsOnOneLineRoundTrip() throws Exception {
        // Two separate 2-note beam groups on the same line, at indices 0-1 and 2-3.
        // Both must survive the round-trip with their exact index pairs preserved.
        var song = buildSong(line -> {
            var note0 = ElementType.QUAVER.newInstance();
            var note1 = ElementType.QUAVER.newInstance();
            var note2 = ElementType.QUAVER.newInstance();
            var note3 = ElementType.QUAVER.newInstance();
            line.addElement(note0);
            line.addElement(note1);
            line.addElement(note2);
            line.addElement(note3);
            line.addBeaming(new Beam(note0, note1));
            line.addBeaming(new Beam(note2, note3));
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var beams = line2.findRangeElements(Beam.class);

        assertThat(beams).as("beam count after round-trip").hasSize(2);
        assertRangeElementEquals(beams.get(0), 0, 1, "first beam");
        assertRangeElementEquals(beams.get(1), 2, 3, "second beam");
    }

    // -------------------------------------------------------------------------
    // Range-Spans Phase 6a: beam writer-output assertion tests
    //
    // The reader ignores secondary beam values, so round-trip cannot verify
    // them.  These tests parse the raw XML string to assert the per-note,
    // per-level <beam> values that the writer emits for primary (number="1")
    // and secondary (number="2") beams, including all three stubRight branches:
    //
    //   forward hook  — stubRight == true  → note at group start
    //   backward hook — stubRight == false → note at group end (i == beamEnd)
    //   backward hook — stubRight == false → interior note (rightBeams < myBeams)
    // -------------------------------------------------------------------------

    @Test
    void testFourNoteBeamPrimaryValuesInOutput() throws Exception {
        // 4-note QUAVER beam: primary beam must emit begin/continue/continue/end.
        var song = buildSong(line -> {
            var note0 = ElementType.QUAVER.newInstance();
            var note1 = ElementType.QUAVER.newInstance();
            var note2 = ElementType.QUAVER.newInstance();
            var note3 = ElementType.QUAVER.newInstance();
            line.addElement(note0);
            line.addElement(note1);
            line.addElement(note2);
            line.addElement(note3);
            line.addBeaming(new Beam(note0, note3));
        });

        var xml = writeToString(song);

        assertThat(beamValue(xml, 0, 1)).as("note 0 primary beam").isEqualTo("begin");
        assertThat(beamValue(xml, 1, 1)).as("note 1 primary beam").isEqualTo("continue");
        assertThat(beamValue(xml, 2, 1)).as("note 2 primary beam").isEqualTo("continue");
        assertThat(beamValue(xml, 3, 1)).as("note 3 primary beam").isEqualTo("end");
    }

    @Test
    void testForwardHookAtGroupStartInOutput() throws Exception {
        // Group: SEMIQUAVER(0) – QUAVER(1).
        // The SEMIQUAVER is the first note in the group (i == beamStart), so
        // stubRight returns true → <beam number="2">forward hook</beam>.
        // The QUAVER has only one beam level — no number="2" element.
        var song = buildSong(line -> {
            var semi = ElementType.SEMIQUAVER.newInstance();
            var quaver = ElementType.QUAVER.newInstance();
            line.addElement(semi);
            line.addElement(quaver);
            line.addBeaming(new Beam(semi, quaver));
        });

        var xml = writeToString(song);

        // Primary beam.
        assertThat(beamValue(xml, 0, 1)).as("SEMI primary beam").isEqualTo("begin");
        assertThat(beamValue(xml, 1, 1)).as("QUAVER primary beam").isEqualTo("end");

        // Secondary beam: SEMI at group start → forward hook; QUAVER absent.
        assertThat(beamValue(xml, 0, 2)).as("SEMI secondary beam (group start)").isEqualTo("forward hook");
        assertThat(beamValue(xml, 1, 2)).as("QUAVER secondary beam").isNull();
    }

    @Test
    void testBackwardHookAtGroupEndInOutput() throws Exception {
        // Group: QUAVER(0) – SEMIQUAVER(1).
        // The SEMIQUAVER is the last note in the group (i == beamEnd), so
        // stubRight returns false → <beam number="2">backward hook</beam>.
        // The QUAVER has only one beam level — no number="2" element.
        var song = buildSong(line -> {
            var quaver = ElementType.QUAVER.newInstance();
            var semi = ElementType.SEMIQUAVER.newInstance();
            line.addElement(quaver);
            line.addElement(semi);
            line.addBeaming(new Beam(quaver, semi));
        });

        var xml = writeToString(song);

        // Primary beam.
        assertThat(beamValue(xml, 0, 1)).as("QUAVER primary beam").isEqualTo("begin");
        assertThat(beamValue(xml, 1, 1)).as("SEMI primary beam").isEqualTo("end");

        // Secondary beam: SEMI at group end → backward hook; QUAVER absent.
        assertThat(beamValue(xml, 0, 2)).as("QUAVER secondary beam").isNull();
        assertThat(beamValue(xml, 1, 2)).as("SEMI secondary beam (group end)").isEqualTo("backward hook");
    }

    @Test
    void testInteriorHookAtBeamBreakInOutput() throws Exception {
        // Group: QUAVER(0) – SEMIQUAVER(1) – QUAVER(2).
        // The SEMIQUAVER is interior (not beamStart, not beamEnd) but its right
        // neighbour is a QUAVER (rightBeams=1 < myBeams=2), which triggers the
        // rightBeams < myBeams branch → stubRight returns false → backward hook.
        var song = buildSong(line -> {
            var quaver0 = ElementType.QUAVER.newInstance();
            var semi = ElementType.SEMIQUAVER.newInstance();
            var quaver2 = ElementType.QUAVER.newInstance();
            line.addElement(quaver0);
            line.addElement(semi);
            line.addElement(quaver2);
            line.addBeaming(new Beam(quaver0, quaver2));
        });

        var xml = writeToString(song);

        // Primary beam: all three notes participate.
        assertThat(beamValue(xml, 0, 1)).as("note 0 primary beam").isEqualTo("begin");
        assertThat(beamValue(xml, 1, 1)).as("note 1 primary beam").isEqualTo("continue");
        assertThat(beamValue(xml, 2, 1)).as("note 2 primary beam").isEqualTo("end");

        // Secondary beam: SEMI interior with beam break on right → backward hook.
        assertThat(beamValue(xml, 0, 2)).as("QUAVER note 0 secondary beam").isNull();
        assertThat(beamValue(xml, 1, 2)).as("SEMI secondary beam (interior beam break)").isEqualTo("backward hook");
        assertThat(beamValue(xml, 2, 2)).as("QUAVER note 2 secondary beam").isNull();
    }

    @Test
    void testContinuousSecondaryBeamValuesInOutput() throws Exception {
        // Group: SEMIQUAVER(0) – SEMIQUAVER(1) – SEMIQUAVER(2).
        // Unlike the hook tests (a lone 16th between 8ths), every note here
        // participates at the 16th level, so the secondary beam is a CONTINUOUS
        // run — number="2" begin/continue/end — not a forward/backward hook.
        var song = buildSong(line -> {
            var note0 = ElementType.SEMIQUAVER.newInstance();
            var note1 = ElementType.SEMIQUAVER.newInstance();
            var note2 = ElementType.SEMIQUAVER.newInstance();
            line.addElement(note0);
            line.addElement(note1);
            line.addElement(note2);
            line.addBeaming(new Beam(note0, note2));
        });

        var xml = writeToString(song);

        // Primary beam (number="1").
        assertThat(beamValue(xml, 0, PRIMARY_BEAM_NUMBER)).as("note 0 primary beam").isEqualTo("begin");
        assertThat(beamValue(xml, 1, PRIMARY_BEAM_NUMBER)).as("note 1 primary beam").isEqualTo("continue");
        assertThat(beamValue(xml, 2, PRIMARY_BEAM_NUMBER)).as("note 2 primary beam").isEqualTo("end");

        // Continuous secondary beam (number="2"): begin/continue/end, no hooks.
        assertThat(beamValue(xml, 0, SECONDARY_BEAM_NUMBER)).as("note 0 secondary beam (continuous begin)").isEqualTo("begin");
        assertThat(beamValue(xml, 1, SECONDARY_BEAM_NUMBER)).as("note 1 secondary beam (continuous continue)").isEqualTo("continue");
        assertThat(beamValue(xml, 2, SECONDARY_BEAM_NUMBER)).as("note 2 secondary beam (continuous end)").isEqualTo("end");
    }

    @Test
    void testThirtySecondNoteBeamLevelValuesInOutput() throws Exception {
        // Group: DEMI_SEMIQUAVER(0) – DEMI_SEMIQUAVER(1) – DEMI_SEMIQUAVER(2).
        // Three 32nd notes exercise all three beam levels: primary (number="1"),
        // 16th (number="2"), and 32nd (number="3"). Every note participates at
        // every level, so each level emits a continuous begin/continue/end run —
        // the only path that produces a number="3" element.
        var song = buildSong(line -> {
            var note0 = ElementType.DEMI_SEMIQUAVER.newInstance();
            var note1 = ElementType.DEMI_SEMIQUAVER.newInstance();
            var note2 = ElementType.DEMI_SEMIQUAVER.newInstance();
            line.addElement(note0);
            line.addElement(note1);
            line.addElement(note2);
            line.addBeaming(new Beam(note0, note2));
        });

        var xml = writeToString(song);

        // Primary beam (number="1").
        assertThat(beamValue(xml, 0, PRIMARY_BEAM_NUMBER)).as("note 0 primary beam").isEqualTo("begin");
        assertThat(beamValue(xml, 1, PRIMARY_BEAM_NUMBER)).as("note 1 primary beam").isEqualTo("continue");
        assertThat(beamValue(xml, 2, PRIMARY_BEAM_NUMBER)).as("note 2 primary beam").isEqualTo("end");

        // 16th-level secondary beam (number="2").
        assertThat(beamValue(xml, 0, SECONDARY_BEAM_NUMBER)).as("note 0 16th beam").isEqualTo("begin");
        assertThat(beamValue(xml, 1, SECONDARY_BEAM_NUMBER)).as("note 1 16th beam").isEqualTo("continue");
        assertThat(beamValue(xml, 2, SECONDARY_BEAM_NUMBER)).as("note 2 16th beam").isEqualTo("end");

        // 32nd-level tertiary beam (number="3") — the level under test.
        assertThat(beamValue(xml, 0, TERTIARY_BEAM_NUMBER)).as("note 0 32nd beam").isEqualTo("begin");
        assertThat(beamValue(xml, 1, TERTIARY_BEAM_NUMBER)).as("note 1 32nd beam").isEqualTo("continue");
        assertThat(beamValue(xml, 2, TERTIARY_BEAM_NUMBER)).as("note 2 32nd beam").isEqualTo("end");
    }

    @Test
    void testGraceNoteInsideBeamGroupRoundTrips() throws Exception {
        // Group: SEMIQUAVER(0) – GRACE_QUAVER(1) – SEMIQUAVER(2).
        // Writing the group drives BeamMath.noteTypeInLevel down its grace-note
        // branch: the grace note has no intrinsic 16th-level membership, so its
        // participation is resolved by scanning its non-grace neighbours (both
        // 16ths). The beam span must re-collapse to [0, 2] and the grace note must
        // survive in the interior slot.
        var song = buildSong(line -> {
            var note0 = ElementType.SEMIQUAVER.newInstance();
            var grace = ElementType.GRACE_QUAVER.newInstance();
            var note2 = ElementType.SEMIQUAVER.newInstance();
            line.addElement(note0);
            line.addElement(grace);
            line.addElement(note2);
            line.addBeaming(new Beam(note0, note2));
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var beams = line2.findRangeElements(Beam.class);

        assertThat(beams).as("beam count after grace-in-group round-trip").hasSize(1);
        assertRangeElementEquals(beams.get(0), 0, 2);
        assertThat(line2.getElement(1).getType().isGraceNote())
            .as("interior grace note survives inside the beam group")
            .isTrue();
    }

    // #592: the beam passes over an interior grace note rather than including it, so the
    // grace note emits no <beam> of its own while its neighbours still begin and end the group.
    @Test
    void testGraceNoteInsideBeamGroupEmitsNoBeamElement() throws Exception {
        var song = buildSong(line -> {
            var note0 = ElementType.QUAVER.newInstance();
            var grace = ElementType.GRACE_QUAVER.newInstance();
            var note2 = ElementType.QUAVER.newInstance();
            line.addElement(note0);
            line.addElement(grace);
            line.addElement(note2);
            line.addBeaming(new Beam(note0, note2));
        });

        var xml = writeToString(song);

        assertThat(beamValue(xml, 0, PRIMARY_BEAM_NUMBER)).as("note 0 primary beam").isEqualTo("begin");
        assertThat(beamValue(xml, 1, PRIMARY_BEAM_NUMBER)).as("grace note primary beam").isNull();
        assertThat(beamValue(xml, 2, PRIMARY_BEAM_NUMBER)).as("note 2 primary beam").isEqualTo("end");
    }
}
