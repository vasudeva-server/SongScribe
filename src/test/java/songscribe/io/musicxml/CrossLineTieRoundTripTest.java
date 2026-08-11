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
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.assertSpanEquals;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.buildSong;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.countOccurrences;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.parse;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.roundTrip;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.writeToString;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.dom.Tie;

/**
 * Round-trip tests for a tie whose anchor and end sit in different lines
 * (issue #493, phase 12). The writer emits ties as per-note {@code <tied>}
 * markers rather than an index pair, so each line's note only carries a marker
 * for the endpoint that resolves to a position in that line being written.
 * Both lines hold a cross-line tie, so without that a single tie would emit a
 * start and a stop in each of them. The read side collapses a pending
 * {@code <tied type="start">} with the next matching {@code type="stop"} via
 * {@code MeasureMapper.resolveTie}, then hands the built {@link Tie} to
 * {@code Line.addTie}, which reaches {@code Line.appendChild} — the phase-1
 * chokepoint that adds a span to both lines of a pair when its endpoints
 * straddle a line boundary. These tests exercise that path end to end rather
 * than assume it.
 */
class CrossLineTieRoundTripTest extends UnitTest {

    /**
     * Enough notes per line that the anchor sits at a high index and the far line has an
     * element at that same index. A one- or two-note line hides the write-side defect
     * {@link #testCrossLineTieWritesExactlyOneStartAndOneStopMarker} pins.
     */
    private static final int NOTES_PER_LINE = 4;

    private static final String TIED_START_MARKER = "<tied type=\"start\"";
    private static final String TIED_STOP_MARKER = "<tied type=\"stop\"";

    /** Adds {@code count} crotchets to {@code line} and returns them in order. */
    private static List<StaffElement> addNotes(Line line, int count) {
        var notes = new ArrayList<StaffElement>(count);

        for (var i = 0; i < count; i++) {
            var note = crotchet();
            line.addElement(note);
            notes.add(note);
        }

        return notes;
    }

    @Test
    void testCrossLineTieRoundTrips() throws Exception {
        var elements = new StaffElement[2];

        var song = buildSong(
            line -> {
                var note0 = crotchet();
                line.addElement(note0);
                elements[0] = note0;
            },
            line -> {
                var note1 = crotchet();
                line.addElement(note1);
                elements[1] = note1;
                line.addTie(new Tie(elements[0], note1));
            }
        );

        var song2 = roundTrip(song);
        var line0 = song2.getLine(0);
        var line1 = song2.getLine(1);
        var tiesOnLine0 = line0.findTies();
        var tiesOnLine1 = line1.findTies();

        assertThat(tiesOnLine0).as("cross-line tie must be present in the anchor line's spans").hasSize(1);
        assertThat(tiesOnLine1).as("cross-line tie must be present in the end line's spans").hasSize(1);
        assertThat(tiesOnLine0.getFirst())
            .as("both lines must hold the same Tie instance, not two copies")
            .isSameAs(tiesOnLine1.getFirst());

        var tie = tiesOnLine0.getFirst();

        assertThat(tie.getAnchorElement())
            .as("anchor element must be line 0's note")
            .isSameAs(line0.getElement(0));
        assertThat(tie.getEndElement())
            .as("end element must be line 1's note")
            .isSameAs(line1.getElement(0));
    }

    @Test
    void testSameLineTieOnMultiLineSongIsUnaffected() throws Exception {
        // A tie entirely inside one line of a multi-line song must not spill into
        // the adjacent line — appendChild's otherLineOf must find both endpoints
        // in the same line and return null.
        var song = buildSong(
            line -> {
                var note0 = crotchet();
                var note1 = crotchet();
                line.addElement(note0);
                line.addElement(note1);
                line.addTie(new Tie(note0, note1));
            },
            line -> {
                var note2 = crotchet();
                line.addElement(note2);
            }
        );

        var song2 = roundTrip(song);
        var line0 = song2.getLine(0);
        var line1 = song2.getLine(1);

        assertThat(line0.findTies()).as("same-line tie count on its own line").hasSize(1);
        assertThat(line1.findTies()).as("same-line tie must not appear on the adjacent line").isEmpty();
        assertSpanEquals(line0.findTies().getFirst(), 0, 1);
    }

    @Test
    void testTieChainCrossingLineBreakRoundTrips() throws Exception {
        // Layout: line0 = note0 note1 ; line1 = note2 note3
        // Ties: note0-note1 (same line), note1-note2 (crosses the break), note2-note3 (same line).
        var elements = new StaffElement[4];

        var song = buildSong(
            line -> {
                var note0 = crotchet();
                var note1 = crotchet();
                line.addElement(note0);
                line.addElement(note1);
                elements[0] = note0;
                elements[1] = note1;
                line.addTie(new Tie(note0, note1));
            },
            line -> {
                var note2 = crotchet();
                var note3 = crotchet();
                line.addElement(note2);
                line.addElement(note3);
                elements[2] = note2;
                elements[3] = note3;
                line.addTie(new Tie(elements[1], note2));
                line.addTie(new Tie(note2, note3));
            }
        );

        var song2 = roundTrip(song);
        var line0 = song2.getLine(0);
        var line1 = song2.getLine(1);
        var tiesOnLine0 = line0.findTies();
        var tiesOnLine1 = line1.findTies();

        assertThat(tiesOnLine0)
            .as("line 0 must hold its own tie plus its half of the cross-line tie")
            .hasSize(2);
        assertThat(tiesOnLine1)
            .as("line 1 must hold its own tie plus its half of the cross-line tie")
            .hasSize(2);

        var crossLineTieOnLine0 = tiesOnLine0.stream()
            .filter(tie -> tie.getAnchorElement() == line0.getElement(1))
            .findFirst()
            .orElseThrow();
        var crossLineTieOnLine1 = tiesOnLine1.stream()
            .filter(tie -> tie.getEndElement() == line1.getElement(0))
            .findFirst()
            .orElseThrow();

        assertThat(crossLineTieOnLine0)
            .as("the cross-line tie found via line 0 and via line 1 must be the same instance")
            .isSameAs(crossLineTieOnLine1);
        assertThat(crossLineTieOnLine0.getEndElement())
            .as("cross-line tie's end element must be line 1's first note")
            .isSameAs(line1.getElement(0));
    }

    @Test
    void testCrossLineTieWritesExactlyOneStartAndOneStopMarker() throws Exception {
        // The cross-line tie is in both lines' findTies(). Bucketing it by its raw index pair
        // gives each line the anchor's index resolved in line 0 and the end's resolved in
        // line 1, so both lines write both markers: line 0 a spurious stop at its index 0 and
        // line 1 a spurious start at the anchor's index. Both lines need a note at both
        // indices for that to be visible, which is why each line holds NOTES_PER_LINE notes
        // and the anchor is the last of line 0.
        var anchor = new StaffElement[1];

        var song = buildSong(
            line -> anchor[0] = addNotes(line, NOTES_PER_LINE).getLast(),
            line -> {
                var notes = addNotes(line, NOTES_PER_LINE);
                line.addTie(new Tie(anchor[0], notes.getFirst()));
            }
        );

        var xml = writeToString(song);

        assertThat(countOccurrences(xml, TIED_START_MARKER))
            .as("the one cross-line tie must write exactly one <tied type=\"start\"/>")
            .isEqualTo(1);
        assertThat(countOccurrences(xml, TIED_STOP_MARKER))
            .as("the one cross-line tie must write exactly one <tied type=\"stop\"/>")
            .isEqualTo(1);

        var song2 = parse(xml);
        var line0 = song2.getLine(0);
        var line1 = song2.getLine(1);
        var tiesOnLine0 = line0.findTies();
        var tiesOnLine1 = line1.findTies();

        assertThat(tiesOnLine0).as("line 0 must hold its half of the one tie, and nothing else").hasSize(1);
        assertThat(tiesOnLine1).as("line 1 must hold its half of the one tie, and nothing else").hasSize(1);

        var tie = tiesOnLine0.getFirst();

        assertThat(tie).as("both lines must hold the same Tie instance").isSameAs(tiesOnLine1.getFirst());
        assertThat(tie.getAnchorElement())
            .as("anchor element must be line 0's last note")
            .isSameAs(line0.getElement(NOTES_PER_LINE - 1));
        assertThat(tie.getEndElement())
            .as("end element must be line 1's first note")
            .isSameAs(line1.getElement(0));
    }
}
