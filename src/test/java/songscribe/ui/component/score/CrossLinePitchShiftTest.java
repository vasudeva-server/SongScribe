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

package songscribe.ui.component.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;

import javax.swing.JOptionPane;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.Tie;
import songscribe.message.mutation.Mutation;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.ui.OptionDialogs;
import songscribe.undo.UndoTestSupport;

/**
 * Pitch-shifting a note tied across a line break (#493).
 *
 * <p>{@link PitchShifter#buildPitchShiftGroup} used to expand a tie chain by walking a raw index
 * range on one line ({@code tie.getAnchorElementIndex()..tie.getEndElementIndex()}). For a
 * cross-line tie those two indices resolve through two different lines, so the walk either ran
 * zero times or over an unrelated range — shifting one endpoint would silently leave its tied
 * partner in the adjacent line at its old pitch. These tests pin down that raising or lowering
 * either endpoint of a cross-line tie moves both, that a chain crossing the break moves as one
 * unit, that undo restores every note the shift touched, and that an ordinary same-line chain
 * still moves as one unit exactly as before.
 */
class CrossLinePitchShiftTest extends UnitTest {

    private static final int ORIGINAL_POSITION_SP = 4;
    private static final int OTHER_POSITION_SP = -2;
    private static final int RAISE_ONE_POSITION = -1;
    private static final int LOWER_ONE_POSITION = 1;

    private static StaffElement crotchetAt(int staffPositionSp) {
        var note = ElementType.CROTCHET.newInstance();
        note.setStaffPosition(staffPositionSp);
        return note;
    }

    /** Silences the audio feedback a shift plays and the restatement dialog it may ask. */
    private static void shiftSilently(Runnable shift) {
        try (var optionDialogs = mockStatic(OptionDialogs.class);
             var prefs = mockStatic(Prefs.class)) {

            prefs.when(() -> Prefs.getBoolean(PrefsKey.PLAY_SELECTED_NOTE)).thenReturn(false);
            optionDialogs.when(() -> OptionDialogs.showConfirmDialog(
                any(), any(), any(), anyInt(), anyInt())).thenReturn(JOptionPane.NO_OPTION);

            shift.run();
        }
    }

    /**
     * A tie whose anchor is the last (only) note of {@code firstLine} and whose end is the
     * first (only) note of {@code secondLine} — the shape a cross-line tie always has (#493).
     */
    private record CrossLineFixture(Song song, Line firstLine, Line secondLine,
                                     StaffElement anchor, StaffElement end) {

        static CrossLineFixture of(int pitchStaffPositionSp) {
            var song = new Song();
            var firstLine = song.getLine(0);
            var secondLine = new Line(song);
            var anchor = crotchetAt(pitchStaffPositionSp);
            var end = crotchetAt(pitchStaffPositionSp);

            song.withoutMutationTracking(() -> {
                firstLine.addElement(anchor);
                song.addLine(secondLine);
                secondLine.addElement(end);
                firstLine.addTie(new Tie(anchor, end));
            });

            return new CrossLineFixture(song, firstLine, secondLine, anchor, end);
        }
    }

    @Test
    void testRaisingTheAnchorRaisesTheEndInTheOtherLineByTheSameAmount() {
        var fixture = CrossLineFixture.of(ORIGINAL_POSITION_SP);

        shiftSilently(() ->
            PitchShifter.shiftPitch(null, fixture.firstLine(), 0, 0, RAISE_ONE_POSITION));

        assertThat(fixture.anchor().getStaffPosition())
            .isEqualTo(ORIGINAL_POSITION_SP + RAISE_ONE_POSITION);
        assertThat(fixture.end().getStaffPosition())
            .as("the tied partner in the adjacent line moved with the anchor")
            .isEqualTo(ORIGINAL_POSITION_SP + RAISE_ONE_POSITION);
    }

    @Test
    void testLoweringTheAnchorLowersTheEndInTheOtherLineByTheSameAmount() {
        var fixture = CrossLineFixture.of(ORIGINAL_POSITION_SP);

        shiftSilently(() ->
            PitchShifter.shiftPitch(null, fixture.firstLine(), 0, 0, LOWER_ONE_POSITION));

        assertThat(fixture.anchor().getStaffPosition())
            .isEqualTo(ORIGINAL_POSITION_SP + LOWER_ONE_POSITION);
        assertThat(fixture.end().getStaffPosition())
            .as("the tied partner in the adjacent line moved with the anchor")
            .isEqualTo(ORIGINAL_POSITION_SP + LOWER_ONE_POSITION);
    }

    @Test
    void testRaisingTheEndInTheSecondLineRaisesTheAnchorInTheFirstLine() {
        // Shifting from the other side of the tie must reach back across the break too.
        var fixture = CrossLineFixture.of(ORIGINAL_POSITION_SP);

        shiftSilently(() ->
            PitchShifter.shiftPitch(null, fixture.secondLine(), 0, 0, RAISE_ONE_POSITION));

        assertThat(fixture.end().getStaffPosition())
            .isEqualTo(ORIGINAL_POSITION_SP + RAISE_ONE_POSITION);
        assertThat(fixture.anchor().getStaffPosition())
            .as("the tied partner in the first line moved too")
            .isEqualTo(ORIGINAL_POSITION_SP + RAISE_ONE_POSITION);
    }

    @Test
    void testChainCrossingTheBreakMovesAsOneUnit() {
        // firstLine: [noteA] --tie1--> secondLine: [noteB] --tie2--> secondLine: [noteC]
        // A chain with one link crossing the line break and one link entirely on the far side.
        var song = new Song();
        var firstLine = song.getLine(0);
        var secondLine = new Line(song);
        var noteA = crotchetAt(ORIGINAL_POSITION_SP);
        var noteB = crotchetAt(ORIGINAL_POSITION_SP);
        var noteC = crotchetAt(ORIGINAL_POSITION_SP);

        song.withoutMutationTracking(() -> {
            firstLine.addElement(noteA);
            song.addLine(secondLine);
            secondLine.addElement(noteB);
            secondLine.addElement(noteC);
            firstLine.addTie(new Tie(noteA, noteB));
            secondLine.addTie(new Tie(noteB, noteC));
        });

        shiftSilently(() -> PitchShifter.shiftPitch(null, firstLine, 0, 0, RAISE_ONE_POSITION));

        assertThat(noteA.getStaffPosition()).isEqualTo(ORIGINAL_POSITION_SP + RAISE_ONE_POSITION);
        assertThat(noteB.getStaffPosition())
            .as("the middle note of the chain moved")
            .isEqualTo(ORIGINAL_POSITION_SP + RAISE_ONE_POSITION);
        assertThat(noteC.getStaffPosition())
            .as("the far end of the chain, entirely on the second line, moved too")
            .isEqualTo(ORIGINAL_POSITION_SP + RAISE_ONE_POSITION);
    }

    @Test
    void testSameLineChainStillMovesAsOneUnit() {
        // note1 --tie1--> note2 --tie2--> note3, all on one line: the pre-existing behavior
        // this rewrite must not have disturbed.
        var line = detachedLine();
        var note1 = crotchetAt(ORIGINAL_POSITION_SP);
        var note2 = crotchetAt(ORIGINAL_POSITION_SP);
        var note3 = crotchetAt(ORIGINAL_POSITION_SP);
        line.addElement(note1);
        line.addElement(note2);
        line.addElement(note3);
        line.addTie(new Tie(note1, note2));
        line.addTie(new Tie(note2, note3));

        var group = PitchShifter.buildPitchShiftGroup(line, 0, 0);

        assertThat(group).extracting(PitchShifter.PitchShiftEntry::index)
            .as("the whole chain closed even though note2 is shared between two separate Ties")
            .containsExactlyInAnyOrder(0, 1, 2);
    }

    @Test
    void testUndoRestoresEveryMovedNoteAcrossBothLines() {
        var fixture = CrossLineFixture.of(ORIGINAL_POSITION_SP);
        var scoreView = UndoTestSupport.scoreViewFor(fixture.song());

        var batch = new ArrayList<Mutation>();
        shiftSilently(() -> batch.addAll(UndoTestSupport.captureBatch(fixture.song(),
            () -> PitchShifter.shiftPitch(null, fixture.firstLine(), 0, 0, RAISE_ONE_POSITION))));

        // Sanity: the shift actually moved both notes before undo is asked to reverse it.
        assertThat(fixture.anchor().getStaffPosition())
            .isEqualTo(ORIGINAL_POSITION_SP + RAISE_ONE_POSITION);
        assertThat(fixture.end().getStaffPosition())
            .isEqualTo(ORIGINAL_POSITION_SP + RAISE_ONE_POSITION);

        UndoTestSupport.replayUndo(scoreView, batch);

        assertThat(fixture.anchor().getStaffPosition())
            .as("undo restored the anchor in the first line")
            .isEqualTo(ORIGINAL_POSITION_SP);
        assertThat(fixture.end().getStaffPosition())
            .as("undo restored the tied partner in the second line")
            .isEqualTo(ORIGINAL_POSITION_SP);
    }

    @Test
    void testGroupIncludesOnlyTheTiedNotesNotUnrelatedOnesInTheSecondLine() {
        // secondLine also holds an untouched note after the tied one, to prove the cross-line
        // expansion reaches exactly the tie's partner and no further.
        var song = new Song();
        var firstLine = song.getLine(0);
        var secondLine = new Line(song);
        var anchor = crotchetAt(ORIGINAL_POSITION_SP);
        var end = crotchetAt(ORIGINAL_POSITION_SP);
        var untouched = crotchetAt(OTHER_POSITION_SP);

        song.withoutMutationTracking(() -> {
            firstLine.addElement(anchor);
            song.addLine(secondLine);
            secondLine.addElement(end);
            secondLine.addElement(untouched);
            firstLine.addTie(new Tie(anchor, end));
        });

        var group = PitchShifter.buildPitchShiftGroup(firstLine, 0, 0);

        assertThat(group).hasSize(2);
        assertThat(group).extracting(PitchShifter.PitchShiftEntry::line)
            .containsExactlyInAnyOrder(firstLine, secondLine);

        shiftSilently(() -> PitchShifter.shiftPitch(null, firstLine, 0, 0, RAISE_ONE_POSITION));

        assertThat(untouched.getStaffPosition())
            .as("the note after the tied one was never part of the tie and did not move")
            .isEqualTo(OTHER_POSITION_SP);
    }
}
