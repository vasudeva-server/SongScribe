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

import songscribe.dom.ElementType;
import songscribe.layout.Ending;
import songscribe.layout.LineEndingSupport;

// -------------------------------------------------------------------------
// Range-Spans Phase 7b: ending round-trip tests
//
// A SongScribe Ending expands to two MusicXML voltas on write and collapses
// back to one Ending on read.  The anchor/end element indices are asserted
// explicitly (not just span existence) to enforce the one-element-per-barline
// invariant from Phase 5 Task 4.  The live split index is also verified to
// confirm that getSplitIndex() recomputes correctly from the recovered line.
//
// Each volta carries a note so the layouts are real music: a first/second
// ending always has measure content between anchor→split (volta 1) and
// split→end (volta 2).  The intervening notes also exercise the read-side
// element-ordering paths (a mid-line REPEAT_RIGHT split followed by content),
// which a barlines-only layout would not.
//
// Cases:
//   1. REPEAT_LEFT anchor, REPEAT_RIGHT split, FINAL_DOUBLE_BARLINE end —
//      the canonical two-bracket ending.
//   2. REPEAT_LEFT anchor, REPEAT_LEFT_RIGHT split, REPEAT_RIGHT end —
//      the REPEAT_LEFT_RIGHT-split path (split straddles a measure boundary).
//   3. SINGLE_BARLINE anchor — exercises the plain-barline element path
//      (anchor is not a forward repeat, so it rides on a right barline).
//
// Every ending must have a split (the two brackets are meaningless without a
// repeat between them), so a split-less span is not a valid ending: writing one
// throws, and importing one drops it. Both are asserted below.
// -------------------------------------------------------------------------
class MusicXmlEndingRoundTripTest extends MusicXmlRoundTripSupport {

    @Test
    void testTwoBracketEndingWithRepeatLeftAnchorRoundTrips() throws Exception {
        // Line layout:
        //   REPEAT_LEFT(0) | C(1) | REPEAT_RIGHT(2) | C(3) | FINAL_DOUBLE_BARLINE(4)
        // Ending: anchor=REPEAT_LEFT, split=REPEAT_RIGHT, end=FINAL_DOUBLE_BARLINE
        //
        // Writer emits:
        //   forward-left barline [1 start], volta-1 note
        //   backward-right barline [1 stop] + invisible-left [2 start], volta-2 note
        //   light-heavy right barline [2 stop]
        //
        // Reader recovers REPEAT_LEFT(0), C(1), REPEAT_RIGHT(2), C(3), FINAL(4):
        //   Ending(element0, element4); getSplitIndex scans [1,4) → 2
        var song = buildSong(line -> {
            var anchorElement = ElementType.REPEAT_LEFT.newInstance();
            var splitElement = ElementType.REPEAT_RIGHT.newInstance();
            var endElement = ElementType.FINAL_DOUBLE_BARLINE.newInstance();
            line.addElement(anchorElement);
            line.addElement(ElementType.CROTCHET.newInstance());
            line.addElement(splitElement);
            line.addElement(ElementType.CROTCHET.newInstance());
            line.addElement(endElement);
            line.addRangeElement(new Ending(anchorElement, endElement));
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var endings = LineEndingSupport.findEndings(line2);

        assertThat(endings).as("ending count").hasSize(1);
        var ending = endings.get(0);
        assertRangeElementEquals(ending, 0, 4, "two-bracket REPEAT_LEFT ending");
        assertThat(ending.getSplitIndex(line2))
            .as("split index: REPEAT_RIGHT must be at index 2")
            .isEqualTo(2);
    }

    @Test
    void testRepeatLeftRightSplitEndingRoundTrips() throws Exception {
        // Line layout:
        //   REPEAT_LEFT(0) | C(1) | REPEAT_LEFT_RIGHT(2) | C(3) | REPEAT_RIGHT(4)
        // Ending: anchor=REPEAT_LEFT, split=REPEAT_LEFT_RIGHT, end=REPEAT_RIGHT
        //
        // Writer emits:
        //   forward-left barline [1 start], volta-1 note
        //   backward-right barline [1 stop] + forward-left barline [2 start]
        //     (REPEAT_LEFT_RIGHT straddles a measure boundary), volta-2 note
        //   backward-right barline [2 stop]
        //
        // Reader recovers REPEAT_LEFT(0), C(1), REPEAT_LEFT_RIGHT(2), C(3),
        //   REPEAT_RIGHT(4) (terminal REPEAT_RIGHT deferred until </part>):
        //   Ending(element0, element4); getSplitIndex scans [1,4) → 2
        var song = buildSong(line -> {
            var anchorElement = ElementType.REPEAT_LEFT.newInstance();
            var splitElement = ElementType.REPEAT_LEFT_RIGHT.newInstance();
            var endElement = ElementType.REPEAT_RIGHT.newInstance();
            line.addElement(anchorElement);
            line.addElement(ElementType.CROTCHET.newInstance());
            line.addElement(splitElement);
            line.addElement(ElementType.CROTCHET.newInstance());
            line.addElement(endElement);
            line.addRangeElement(new Ending(anchorElement, endElement));
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var endings = LineEndingSupport.findEndings(line2);

        assertThat(endings).as("ending count").hasSize(1);
        var ending = endings.get(0);
        assertRangeElementEquals(ending, 0, 4, "REPEAT_LEFT_RIGHT-split ending");
        assertThat(ending.getSplitIndex(line2))
            .as("split index: REPEAT_LEFT_RIGHT must be at index 2")
            .isEqualTo(2);
    }

    @Test
    void testSingleBarlineAnchoredEndingRoundTrips() throws Exception {
        // Line layout:
        //   SINGLE_BARLINE(0) | C(1) | REPEAT_RIGHT(2) | C(3) | FINAL_DOUBLE_BARLINE(4)
        // Ending: anchor=SINGLE_BARLINE, split=REPEAT_RIGHT, end=FINAL_DOUBLE_BARLINE
        //
        // A SINGLE_BARLINE anchor is not a forward repeat, so it rides on a right
        // barline (not a forward-left barline), exercising the non-repeat anchor
        // path in buildSpanIndex and the one-element-per-barline invariant.
        //
        // Writer emits:
        //   right barline (regular) [1 start], volta-1 note
        //   backward-right barline [1 stop] + invisible-left [2 start], volta-2 note
        //   light-heavy right barline [2 stop]
        //
        // Reader recovers SINGLE_BARLINE(0), C(1), REPEAT_RIGHT(2), C(3), FINAL(4):
        //   Ending(element0, element4); getSplitIndex scans [1,4) → 2
        var song = buildSong(line -> {
            var anchorElement = ElementType.SINGLE_BARLINE.newInstance();
            var splitElement = ElementType.REPEAT_RIGHT.newInstance();
            var endElement = ElementType.FINAL_DOUBLE_BARLINE.newInstance();
            line.addElement(anchorElement);
            line.addElement(ElementType.CROTCHET.newInstance());
            line.addElement(splitElement);
            line.addElement(ElementType.CROTCHET.newInstance());
            line.addElement(endElement);
            line.addRangeElement(new Ending(anchorElement, endElement));
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var endings = LineEndingSupport.findEndings(line2);

        assertThat(endings).as("ending count").hasSize(1);
        var ending = endings.get(0);
        assertRangeElementEquals(ending, 0, 4, "SINGLE_BARLINE-anchored ending");
        assertThat(ending.getSplitIndex(line2))
            .as("split index: REPEAT_RIGHT must be at index 2")
            .isEqualTo(2);
    }

    @Test
    void testWritingSplitLessEndingThrows() {
        // Line layout: REPEAT_LEFT(0) | C(1) | FINAL_DOUBLE_BARLINE(2)
        // Ending: anchor=REPEAT_LEFT, no split, end=FINAL_DOUBLE_BARLINE.
        //
        // A split-less ending is not a valid ending: the writer resolves its split via
        // getSplitIndex(), which throws because there is no REPEAT between anchor and end.
        var song = buildSong(line -> {
            var anchorElement = ElementType.REPEAT_LEFT.newInstance();
            var endElement = ElementType.FINAL_DOUBLE_BARLINE.newInstance();
            line.addElement(anchorElement);
            line.addElement(ElementType.CROTCHET.newInstance());
            line.addElement(endElement);
            line.addRangeElement(new Ending(anchorElement, endElement));
        });

        assertThatThrownBy(() -> writeToString(song))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no split element");
    }

    @Test
    void testImportedSplitLessEndingIsDropped() throws Exception {
        // A foreign file whose volta spans a note with no REPEAT between anchor and end:
        //   invisible left barline [1 start]  → note-anchored start (issue #306)
        //   note
        //   light-heavy right barline [2 stop] → end, no split between the two
        //
        // The reader binds the anchor to the note and the end to the terminal barline,
        // then buildEnding finds no split and drops the ending. The note and its closing
        // barline survive, so import otherwise proceeds normally.
        var xml = scoreWithMeasureBody(
            "      <barline location=\"left\"><bar-style>none</bar-style>"
                + "<ending number=\"1\" type=\"start\"/></barline>\n"
            + "      <note>\n"
            + "        <pitch><step>B</step><octave>4</octave></pitch>\n"
            + "        <duration>480</duration>\n"
            + "        <type>quarter</type>\n"
            + "      </note>\n"
            + "      <barline location=\"right\"><bar-style>light-heavy</bar-style>"
                + "<ending number=\"2\" type=\"stop\"/></barline>\n"
        );

        var song = parse(xml);
        var line = song.getLine(0);

        assertThat(LineEndingSupport.findEndings(line))
            .as("a split-less ending must be dropped on import")
            .isEmpty();
        assertThat(line.getElements())
            .as("the note and its closing terminal barline survive")
            .hasSize(2);
    }

    @Test
    void testNoteTerminatedEndingRoundTrips() throws Exception {
        // Line layout: REPEAT_LEFT(0) | C(1) | REPEAT_RIGHT(2) | C(3)
        // Ending: anchor=REPEAT_LEFT, split=REPEAT_RIGHT, end=C(3) (a note, issue #306)
        //
        // The 2nd bracket ends on a note with no terminal barline, so the writer
        // has no barline to host the volta-2 stop.  It emits <ending number="2"
        // type="discontinue"> on the end-of-line invisible right barline (the note
        // is the line's last element, so the marker folds onto that barline rather
        // than a redundant second one).
        //
        // Reader recovers REPEAT_LEFT(0), C(1), REPEAT_RIGHT(2), C(3):
        //   the discontinue binds to the line's last element (the note) →
        //   Ending(element0, element3); getSplitIndex scans [1,3) → 2
        var song = buildSong(line -> {
            var anchorElement = ElementType.REPEAT_LEFT.newInstance();
            var splitElement = ElementType.REPEAT_RIGHT.newInstance();
            var endElement = ElementType.CROTCHET.newInstance();
            line.addElement(anchorElement);
            line.addElement(ElementType.CROTCHET.newInstance());
            line.addElement(splitElement);
            line.addElement(endElement);
            line.addRangeElement(new Ending(anchorElement, endElement));
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var endings = LineEndingSupport.findEndings(line2);

        assertThat(endings).as("ending count").hasSize(1);
        var ending = endings.get(0);
        assertRangeElementEquals(ending, 0, 3, "note-terminated ending");
        assertThat(ending.getSplitIndex(line2))
            .as("split index: REPEAT_RIGHT must be at index 2")
            .isEqualTo(2);
    }

    @Test
    void testNoteAnchoredAndNoteTerminatedEndingRoundTrips() throws Exception {
        // Line layout: C(0) | C(1) | REPEAT_RIGHT(2) | C(3)
        // Ending: anchor=C(0) (a note), split=REPEAT_RIGHT, end=C(3) (a note) — both
        // outer edges are notes (issue #306), the fully barline-free boundary case.
        //
        // Writer emits:
        //   invisible-left [1 start] before C(0), volta-1 notes
        //   backward-right barline [1 stop] + invisible-left [2 start], volta-2 note
        //   end-of-line invisible right barline [2 discontinue]
        //
        // Reader recovers C(0), C(1), REPEAT_RIGHT(2), C(3):
        //   the [1 start] on the invisible left barline binds to the next element
        //   (C(0)); the [2 discontinue] binds to the last element (C(3)) →
        //   Ending(element0, element3); getSplitIndex scans [1,3) → 2
        var song = buildSong(line -> {
            var anchorElement = ElementType.CROTCHET.newInstance();
            var splitElement = ElementType.REPEAT_RIGHT.newInstance();
            var endElement = ElementType.CROTCHET.newInstance();
            line.addElement(anchorElement);
            line.addElement(ElementType.CROTCHET.newInstance());
            line.addElement(splitElement);
            line.addElement(endElement);
            line.addRangeElement(new Ending(anchorElement, endElement));
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var endings = LineEndingSupport.findEndings(line2);

        assertThat(endings).as("ending count").hasSize(1);
        var ending = endings.get(0);
        assertRangeElementEquals(ending, 0, 3, "note-anchored, note-terminated ending");
        assertThat(ending.getSplitIndex(line2))
            .as("split index: REPEAT_RIGHT must be at index 2")
            .isEqualTo(2);
    }

    @Test
    void testMidLineNoteTerminatedEndingRoundTrips() throws Exception {
        // Line layout: REPEAT_LEFT(0) | C(1) | REPEAT_RIGHT(2) | C(3) | C(4)
        // Ending: anchor=REPEAT_LEFT, split=REPEAT_RIGHT, end=C(3) (a note) with a
        // trailing note C(4) after the ending (issue #306).
        //
        // Because the boundary note is NOT the line's last element, the writer
        // emits the volta-2 <ending ... type="discontinue"> on its own invisible
        // right barline immediately after C(3) (the inline path, not folded onto
        // the end-of-line barline).
        //
        // Reader recovers REPEAT_LEFT(0), C(1), REPEAT_RIGHT(2), C(3), C(4):
        //   the discontinue binds to the last element appended when it is parsed
        //   (C(3)) → Ending(element0, element3); getSplitIndex scans [1,3) → 2
        var song = buildSong(line -> {
            var anchorElement = ElementType.REPEAT_LEFT.newInstance();
            var splitElement = ElementType.REPEAT_RIGHT.newInstance();
            var endElement = ElementType.CROTCHET.newInstance();
            line.addElement(anchorElement);
            line.addElement(ElementType.CROTCHET.newInstance());
            line.addElement(splitElement);
            line.addElement(endElement);
            line.addElement(ElementType.CROTCHET.newInstance());
            line.addRangeElement(new Ending(anchorElement, endElement));
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var endings = LineEndingSupport.findEndings(line2);

        assertThat(endings).as("ending count").hasSize(1);
        var ending = endings.get(0);
        assertRangeElementEquals(ending, 0, 3, "mid-line note-terminated ending");
        assertThat(ending.getSplitIndex(line2))
            .as("split index: REPEAT_RIGHT must be at index 2")
            .isEqualTo(2);
    }
}
