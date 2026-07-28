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

import java.util.List;
import java.util.Set;

import javax.swing.JOptionPane;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
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
    private static final int THIRD_NOTE = 2;
    private static final int FOURTH_NOTE = 3;

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
        var element = ElementType.CROTCHET.newInstance();
        element.setStaffPosition(staffPosition);
        element.setAccidental(accidental);
        return element;
    }

    private static StaffElement note(int staffPosition) {
        var element = ElementType.CROTCHET.newInstance();
        element.setStaffPosition(staffPosition);
        return element;
    }

    /** Toggling the flat off {@code 1:0}, as the prompt describes it. */
    private List<AccidentalRestatements.RemovedAccidental> toggleOffTheFirstFlat() {
        return List.of(new AccidentalRestatements.RemovedAccidental(
            firstLine, FIRST_NOTE, F_STAFF_POSITION, StaffElement.Accidental.FLAT));
    }

    private Set<StaffElement> theToggledNote() {
        return Set.of(firstLine.getElement(FIRST_NOTE));
    }

    private AccidentalRestatements.Decision confirmWithAnswer(int answer) {
        try (var optionDialogs = mockStatic(OptionDialogs.class)) {
            optionDialogs.when(() -> OptionDialogs.showConfirmDialog(
                any(), any(), any(), anyInt(), anyInt(), anyInt())).thenReturn(answer);

            return AccidentalRestatements.confirm(null, toggleOffTheFirstFlat(), theToggledNote());
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
    void testNothingIsAskedWhenTheEditRemovesNoAccidental() {
        try (var optionDialogs = mockStatic(OptionDialogs.class)) {
            var decision = AccidentalRestatements.confirm(null, List.of(), Set.of());

            assertThat(decision.isCancelled()).isFalse();
            assertThat(decision.removal()).isEqualTo(AccidentalReconciliation.RestatementRemoval.NONE);
            optionDialogs.verifyNoInteractions();
        }
    }

    @Test
    void testNothingIsAskedWhenTheScanFindsNoRestatement() {
        // Strip both restatements: the flat at 1:0 is now the only one in the song.
        firstLine.getElement(THIRD_NOTE).setAccidental(null);
        secondLine.getElement(FIRST_NOTE).setAccidental(null);

        try (var optionDialogs = mockStatic(OptionDialogs.class)) {
            var decision = AccidentalRestatements.confirm(
                null, toggleOffTheFirstFlat(), theToggledNote());

            assertThat(decision.isCancelled()).isFalse();
            assertThat(decision.removal()).isEqualTo(AccidentalReconciliation.RestatementRemoval.NONE);
            optionDialogs.verifyNoInteractions();
        }
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
    void testTheDeletedRangeYieldsOneRemovalPerExplicitAccidentalAndExcludesEveryElement() {
        assertThat(AccidentalRestatements.inDeletedRange(secondLine, FIRST_NOTE, THIRD_NOTE))
            .containsExactly(new AccidentalRestatements.RemovedAccidental(
                secondLine, FIRST_NOTE, F_STAFF_POSITION, StaffElement.Accidental.FLAT));

        assertThat(AccidentalRestatements.elementsIn(secondLine, FIRST_NOTE, THIRD_NOTE))
            .containsExactlyInAnyOrder(
                secondLine.getElement(0), secondLine.getElement(1), secondLine.getElement(2));
    }
}
