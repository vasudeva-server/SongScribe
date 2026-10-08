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

package songscribe.layout;

import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Accidental;
import songscribe.dom.AccidentalChain;
import songscribe.dom.AccidentalState;
import songscribe.dom.Key;
import songscribe.dom.Line;
import songscribe.dom.NoteElement;
import songscribe.dom.ProjectedEdit;
import songscribe.dom.Song;
import songscribe.dom.SongFactory;
import songscribe.dom.StaffElementFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.setLineWidth;

/**
 * {@link AccidentalFitGate#judge}'s promise: a refusal leaves every note — live and detached —
 * exactly as it was, and an acceptance leaves live notes exactly as they were too, stamping only
 * the notes not yet on any line, which nothing else will stamp before the caller inserts them.
 */
class AccidentalFitGateTest extends UnitTest {

    private static final int F = 3;
    private static final int D = 1;

    private static final double NARROWEST_LINE_WIDTH_SS = 1.0;
    private static final double WIDEST_LINE_WIDTH_SS = 100.0;

    /** Far narrower than any accidental, so the narrowest fitting width leaves no room for one. */
    private static final double LINE_WIDTH_STEP_SS = 0.1;

    /**
     * A line of two decided notes, a detached note projected between them carrying a sharp that
     * breaks the chain, and the walk's real restamps for that projection: one for the detached
     * note itself and one for the live note now stranded past it.
     */
    private record Fixture(Line line, NoteElement live, NoteElement detached,
                            AccidentalState liveStateBefore, AccidentalState detachedStateBefore,
                            List<AccidentalChain.Restamp> restamps) {}

    private static Fixture buildFixture() {
        var song = SongFactory.compactSong(Key.NO_ACCIDENTALS, F + "f " + F);
        var line = song.getLine(0);
        var leading = (NoteElement) line.getElement(0);
        var live = (NoteElement) line.getElement(1);
        var detached = StaffElementFactory.note(F, Accidental.SHARP);

        var restamps = ProjectedEdit.inserting(line, 1, List.of(detached)).walk();

        return new Fixture(line, live, detached, live.accidentalState(), detached.accidentalState(), restamps);
    }

    @Test
    void testARefusalLeavesEveryLiveAndDetachedNoteAsItWas() {
        var fixture = buildFixture();

        var verdict = AccidentalFitGate.judge(fixture.line(), fixture.restamps(), List.of(fixture.detached()), null, () -> false);

        assertThat(verdict).isEqualTo(AccidentalFitGate.Verdict.EDITED_LINE_FULL);
        assertThat(fixture.live().accidentalState()).isEqualTo(fixture.liveStateBefore());
        assertThat(fixture.detached().accidentalState())
            .as("a refusal never stamps a note the caller will not insert")
            .isEqualTo(fixture.detachedStateBefore());
    }

    @Test
    void testAnAcceptanceLeavesLiveNotesAsTheyWereAndStampsOnlyTheDetachedNote() {
        var fixture = buildFixture();

        var verdict = AccidentalFitGate.judge(fixture.line(), fixture.restamps(), List.of(fixture.detached()), null, () -> true);

        assertThat(verdict).isEqualTo(AccidentalFitGate.Verdict.FITS);
        assertThat(fixture.live().accidentalState())
            .as("the song's own walk stamps live notes for good once the edit is recorded; the gate must not")
            .isEqualTo(fixture.liveStateBefore());
        assertThat(fixture.detached().chainedAccidental())
            .as("a detached note is about to be inserted already decided, since nothing else will stamp it")
            .isEqualTo(fixture.restamps().stream()
                .filter(restamp -> restamp.note() == fixture.detached())
                .findFirst().orElseThrow().state());
    }

    @Test
    void testARestampThatWidensAnotherFullLineIsRefusedAndLeavesEveryNoteAsItWas() {
        // A line's first accidental hangs into the header gap and widens nothing, so the restamped
        // note on the other line follows another note.
        var song = SongFactory.compactSong(Key.NO_ACCIDENTALS, String.valueOf(F), D + " " + F);
        var editedLine = song.getLine(0);
        var otherLine = song.getLine(1);
        var other = (NoteElement) otherLine.getElement(1);
        var detached = StaffElementFactory.note(F, Accidental.FLAT);
        narrowToFit(song, otherLine);

        var restamps = ProjectedEdit.inserting(editedLine, editedLine.elementCount(), List.of(detached)).walk();
        var otherStateBefore = other.accidentalState();
        var detachedStateBefore = detached.accidentalState();

        assertThat(restamps)
            .as("the flat must reach the other line's note, which then has to draw a cancel")
            .anyMatch(restamp -> restamp.note() == other);

        var verdict = AccidentalFitGate.judge(editedLine, restamps, List.of(detached), null, () -> true);

        assertThat(verdict).isEqualTo(AccidentalFitGate.Verdict.OTHER_LINE_FULL);
        assertThat(other.accidentalState()).isEqualTo(otherStateBefore);
        assertThat(detached.accidentalState())
            .as("a refusal never stamps a note the caller will not insert")
            .isEqualTo(detachedStateBefore);
    }

    /**
     * Narrows {@code song}'s line width to the narrowest width in the sweep at which {@code line},
     * as it stands, still fits, so that any column it widens no longer fits.
     */
    private static void narrowToFit(Song song, Line line) {
        for (var lineWidthSs = NARROWEST_LINE_WIDTH_SS; lineWidthSs <= WIDEST_LINE_WIDTH_SS;
             lineWidthSs += LINE_WIDTH_STEP_SS) {
            setLineWidth(song, lineWidthSs);

            if (InsertionSpacingCalculator.fitsInPlace(line, line.effectiveElements(), null)) {
                return;
            }
        }

        throw new AssertionError("the line fits at no width in the sweep");
    }
}
