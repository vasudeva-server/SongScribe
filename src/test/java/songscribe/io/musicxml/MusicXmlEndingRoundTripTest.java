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
//   4. Split-less single-bracket ending — only a number="1" start → stop
//      pair; getSplitIndex() must return -1.
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
    void testSplitLessSingleBracketEndingRoundTrips() throws Exception {
        // Line layout: REPEAT_LEFT(0) | C(1) | FINAL_DOUBLE_BARLINE(2)
        // Ending: anchor=REPEAT_LEFT, no split, end=FINAL_DOUBLE_BARLINE
        //
        // A split-less ending emits only a number="1" start → stop pair.  The
        // reader finalizes it via finalizeOrDropPendingEnding at part end rather
        // than waiting for a number="2" stop.
        //
        // Writer emits:
        //   forward-left barline [1 start], single-bracket note
        //   light-heavy right barline [1 stop]   (endNumber=1 since hasSplit=false)
        //
        // Reader recovers REPEAT_LEFT(0), C(1), FINAL_DOUBLE_BARLINE(2):
        //   Ending(element0, element2); getSplitIndex scans [1,2) → -1 (no repeat)
        var song = buildSong(line -> {
            var anchorElement = ElementType.REPEAT_LEFT.newInstance();
            var endElement = ElementType.FINAL_DOUBLE_BARLINE.newInstance();
            line.addElement(anchorElement);
            line.addElement(ElementType.CROTCHET.newInstance());
            line.addElement(endElement);
            line.addRangeElement(new Ending(anchorElement, endElement));
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);
        var endings = LineEndingSupport.findEndings(line2);

        assertThat(endings).as("ending count").hasSize(1);
        var ending = endings.get(0);
        assertRangeElementEquals(ending, 0, 2, "split-less single-bracket ending");
        assertThat(ending.getSplitIndex(line2))
            .as("split index: no split element must return -1")
            .isEqualTo(-1);
    }
}
