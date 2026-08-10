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

package songscribe.ui.edit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.graceQuaver;

import java.util.List;

import javax.swing.JOptionPane;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.KeyType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.layout.AccidentalReconciliation;
import songscribe.ui.OptionDialogs;

/**
 * Tests for {@link AccidentalRestatements}, the prompt that asks whether an edit removing an
 * explicit accidental should also remove the later notes that restate it.
 *
 * <p>The fixture is the worked example of the #681 plan, in D♭ major — five flats, so F is
 * unaltered by the key and every flat on an F is explicit:
 *
 * <pre>
 * line 1:  F♭  G   F♭
 * line 2:  F♭  A   F   F♮
 * </pre>
 *
 * Toggling the flat off {@code 1:0} finds {@code 1:2} and {@code 2:0}. {@code 2:0} is the case
 * that needs asking: it is not redundant on its own line, because accidental context resets at the
 * line boundary.
 */
class AccidentalRestatementsTest extends UnitTest {

    // Staff position 0 is B4 and positions grow downwards, so each step down is one letter back.
    private static final int A_STAFF_POSITION = 1;
    private static final int G_STAFF_POSITION = 2;
    private static final int F_STAFF_POSITION = 3;

    private static final int FIRST_NOTE = 0;
    private static final int SECOND_NOTE = 1;
    private static final int THIRD_NOTE = 2;
    private static final int FOURTH_NOTE = 3;
    private static final int FIFTH_NOTE = 4;

    /** D♭ major: B E A D G are flattened, so an F needs an explicit flat to sound flat. */
    private static final int FIVE_FLATS = 5;

    private Song song = minimalSongMock();
    private Line firstLine = new Line(song);
    private Line secondLine = new Line(song);

    @BeforeEach
    void buildWorkedExample() {
        song = minimalSongMock();
        firstLine = flatKeyLine(
            note(F_STAFF_POSITION, StaffElement.Accidental.FLAT),
            note(G_STAFF_POSITION),
            note(F_STAFF_POSITION, StaffElement.Accidental.FLAT));
        secondLine = flatKeyLine(
            note(F_STAFF_POSITION, StaffElement.Accidental.FLAT),
            note(A_STAFF_POSITION),
            note(F_STAFF_POSITION),
            note(F_STAFF_POSITION, StaffElement.Accidental.NATURAL));

        when(song.lineCount()).thenReturn(2);
        when(song.getLine(0)).thenReturn(firstLine);
        when(song.getLine(1)).thenReturn(secondLine);
        when(song.indexOfLine(firstLine)).thenReturn(0);
        when(song.indexOfLine(secondLine)).thenReturn(1);
    }

    private Line flatKeyLine(StaffElement... elements) {
        var line = new Line(song);

        for (var element : elements) {
            line.addElement(element);
        }

        line.setKeyType(KeyType.FLATS);
        line.setKeyAccidentalCount(FIVE_FLATS);
        return line;
    }

    private static StaffElement note(int staffPosition, StaffElement.Accidental accidental) {
        var element = crotchet();
        element.setStaffPosition(staffPosition);
        element.setAccidental(accidental);
        return element;
    }

    private static StaffElement note(int staffPosition) {
        var element = crotchet();
        element.setStaffPosition(staffPosition);
        return element;
    }

    /** Toggling the flat off {@code 1:0}, as the prompt describes it. */
    private static List<AccidentalRestatements.EditedNote> toggleOffTheFirstFlat() {
        return List.of(new AccidentalRestatements.EditedNote(
            FIRST_NOTE, F_STAFF_POSITION, StaffElement.Accidental.FLAT, null));
    }

    private AccidentalRestatements.Decision confirmWithAnswer(int answer) {
        return confirmWithAnswer(answer, firstLine, toggleOffTheFirstFlat());
    }

    private static AccidentalRestatements.Decision confirmWithAnswer(
        int answer, Line line, List<AccidentalRestatements.EditedNote> edited) {

        try (var optionDialogs = mockStatic(OptionDialogs.class)) {
            optionDialogs.when(() -> OptionDialogs.showConfirmDialog(
                any(), any(), any(), anyInt(), anyInt())).thenReturn(answer);

            return AccidentalRestatements.confirm(null, line, edited);
        }
    }

    /**
     * Runs the confirm and asserts the dialog never appeared, which is the only observable
     * difference between "found nothing to ask about" and "asked and was answered No".
     */
    private static AccidentalRestatements.Decision confirmExpectingNoDialog(
        Line line, List<AccidentalRestatements.EditedNote> edited) {

        try (var optionDialogs = mockStatic(OptionDialogs.class)) {
            var decision = AccidentalRestatements.confirm(null, line, edited);

            optionDialogs.verifyNoInteractions();
            return decision;
        }
    }

    @Test
    void testYesOffersEveryRestatementAndSuppressesTheRemovedPosition() {
        var decision = confirmWithAnswer(JOptionPane.YES_OPTION);

        assertThat(decision.answer()).isEqualTo(AccidentalRestatements.Answer.YES);
        assertThat(decision.isCancelled()).isFalse();
        assertThat(decision.removal().notes()).containsExactlyInAnyOrder(
            firstLine.getElement(THIRD_NOTE), secondLine.getElement(FIRST_NOTE));
        assertThat(decision.removal().suppressedStaffPositions()).containsExactly(F_STAFF_POSITION);
        assertThat(decision.lines()).containsExactly(firstLine, secondLine);
    }

    @Test
    void testNoRemovesNothingButStillLetsTheEditProceed() {
        var decision = confirmWithAnswer(JOptionPane.NO_OPTION);

        assertThat(decision.answer()).isEqualTo(AccidentalRestatements.Answer.NO);
        assertThat(decision.isCancelled()).isFalse();
        assertThat(decision.removal()).isEqualTo(AccidentalReconciliation.RestatementRemoval.NONE);
        assertThat(decision.lines()).isEmpty();
    }

    @Test
    void testCancelAbortsTheWholeEdit() {
        var decision = confirmWithAnswer(JOptionPane.CANCEL_OPTION);

        assertThat(decision.isCancelled()).isTrue();
        assertThat(decision.removal()).isEqualTo(AccidentalReconciliation.RestatementRemoval.NONE);
        assertThat(decision.lines()).isEmpty();
    }

    @Test
    void testNothingIsAskedWhenTheEditChangesNothing() {
        var decision = confirmExpectingNoDialog(firstLine, List.of());

        assertThat(decision.isCancelled()).isFalse();
        assertThat(decision.removal()).isEqualTo(AccidentalReconciliation.RestatementRemoval.NONE);
    }

    @Test
    void testNothingIsAskedWhenTheEditRemovesNoAccidental() {
        // The G at 1:1 has no accidental to lose, so there is nothing to consent to.
        var decision = confirmExpectingNoDialog(firstLine, List.of(
            new AccidentalRestatements.EditedNote(SECOND_NOTE, G_STAFF_POSITION, null, null)));

        assertThat(decision.removal()).isEqualTo(AccidentalReconciliation.RestatementRemoval.NONE);
    }

    @Test
    void testNothingIsAskedWhenTheScanFindsNoRestatement() {
        // Strip both restatements: the flat at 1:0 is now the only one in the song.
        firstLine.getElement(THIRD_NOTE).setAccidental(null);
        secondLine.getElement(FIRST_NOTE).setAccidental(null);

        var decision = confirmExpectingNoDialog(firstLine, toggleOffTheFirstFlat());

        assertThat(decision.isCancelled()).isFalse();
        assertThat(decision.removal()).isEqualTo(AccidentalReconciliation.RestatementRemoval.NONE);
    }

    @Test
    void testWritingAnAccidentalWhereNoneWasRemovesNothing() {
        // The note had nothing to lose: spelling it with an explicit natural adds a symbol rather
        // than taking one away, so no later note has lost anything and the notator is not
        // interrupted.
        var decision = confirmExpectingNoDialog(firstLine, List.of(
            new AccidentalRestatements.EditedNote(
                FIRST_NOTE, F_STAFF_POSITION,
                null, StaffElement.Accidental.NATURAL)));

        assertThat(decision.removal()).isEqualTo(AccidentalReconciliation.RestatementRemoval.NONE);
    }

    @Test
    void testRemovingAnExplicitNaturalCountsEvenThoughItBendsNoPitch() {
        // A natural bends the pitch by zero, exactly as writing no accidental does — but it is
        // there to cancel an earlier flat, so taking it away really does change what the note
        // sounds, and a later natural really is restating it. Give 2:3's natural a restatement.
        secondLine.addElement(note(F_STAFF_POSITION, StaffElement.Accidental.NATURAL));

        var decision = confirmWithAnswer(JOptionPane.YES_OPTION, secondLine, List.of(
            new AccidentalRestatements.EditedNote(
                FOURTH_NOTE, F_STAFF_POSITION, StaffElement.Accidental.NATURAL, null)));

        assertThat(decision.answer()).isEqualTo(AccidentalRestatements.Answer.YES);
        assertThat(decision.removal().notes()).containsExactly(secondLine.getElement(FIFTH_NOTE));
    }

    @Test
    void testAGraceNotesAccidentalIsNeverTreatedAsARemoval() {
        // A grace note sits outside the accidental-context system — the reconciliation walk skips
        // everything that is not a full-size note — so its accidental never lent anything to a
        // later note and cannot have been restated. Editing one must not offer 1:2 or 2:0.
        var graceNote = graceQuaver();
        graceNote.setStaffPosition(F_STAFF_POSITION);
        graceNote.setAccidental(StaffElement.Accidental.FLAT);
        firstLine.setElement(FIRST_NOTE, graceNote);

        var decision = confirmExpectingNoDialog(firstLine, toggleOffTheFirstFlat());

        assertThat(decision.removal()).isEqualTo(AccidentalReconciliation.RestatementRemoval.NONE);
    }

    @Test
    void testCommittingEveryLineClearsTheEditedLineTooAndLetsThePitchChangePropagate() {
        var decision = confirmWithAnswer(JOptionPane.YES_OPTION);

        AccidentalRestatements.commitAllLines(decision);

        assertThat(firstLine.getElement(THIRD_NOTE).getAccidental()).isNull();
        assertThat(secondLine.getElement(FIRST_NOTE).getAccidental()).isNull();

        // Suppression at the F position is what lets 2:2 change pitch instead of being handed the
        // flat straight back — the consented inversion the feature exists for. And 2:3's natural,
        // no longer cancelling anything, goes the ordinary way: the mirror rule takes it.
        assertThat(secondLine.getElement(THIRD_NOTE).getAccidental()).isNull();
        assertThat(secondLine.getElement(FOURTH_NOTE).getAccidental()).isNull();
    }

    @Test
    void testCommittingTheOtherLinesLeavesTheEditedLineToItsOwnReconciliation() {
        var decision = confirmWithAnswer(JOptionPane.YES_OPTION);

        AccidentalRestatements.commitOtherLines(decision, firstLine);

        assertThat(firstLine.getElement(THIRD_NOTE).getAccidental())
            .isEqualTo(StaffElement.Accidental.FLAT);
        assertThat(secondLine.getElement(FIRST_NOTE).getAccidental()).isNull();
    }

    @Test
    void testTheDeletedRangeDescribesEveryElementAsLosingWhateverItCarries() {
        // Every element in the range, not only the ones with accidentals: they are all going away,
        // so none may be offered back or allowed to stand in for a cancellation.
        assertThat(AccidentalRestatements.inDeletedRange(secondLine, FIRST_NOTE, THIRD_NOTE))
            .containsExactly(
                new AccidentalRestatements.EditedNote(
                    FIRST_NOTE, F_STAFF_POSITION, StaffElement.Accidental.FLAT, null),
                new AccidentalRestatements.EditedNote(SECOND_NOTE, A_STAFF_POSITION, null, null),
                new AccidentalRestatements.EditedNote(THIRD_NOTE, F_STAFF_POSITION, null, null));
    }
}
