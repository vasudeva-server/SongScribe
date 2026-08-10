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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.answerVoid;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static songscribe.dom.StaffElementFactory.crotchet;

import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.ElementModification;
import songscribe.message.mutation.Mutation;

/**
 * Tests for {@link AccidentalMaterializer}, the one place that applies an edit's accidental
 * changes, gates them on the edit being accepted, and guarantees a refusal leaves the line exactly
 * as it was. Every call site depends on that contract, so it is asserted here rather than at each
 * of them.
 */
class AccidentalMaterializerTest extends UnitTest {

    // Staff position 0 is B4 and positions grow downwards, so each step down is one letter back.
    private static final int G_STAFF_POSITION = 2;
    private static final int F_STAFF_POSITION = 3;

    private static final int FIRST_NOTE = 0;
    private static final int SECOND_NOTE = 1;
    private static final int THIRD_NOTE = 2;

    private static final int TWO_CHANGES = 2;

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

    /**
     * F♯ G F♮(parenthesized) — one note whose accidental an edit would clear and one it would set,
     * with the parentheses flag on the note being cleared, since that flag is the part
     * {@link StaffElement#setAccidental} silently drops.
     */
    private static Line fixtureLine() {
        var line = detachedLine();
        line.addElement(note(F_STAFF_POSITION, StaffElement.Accidental.SHARP));
        line.addElement(note(G_STAFF_POSITION));
        line.addElement(note(F_STAFF_POSITION, StaffElement.Accidental.NATURAL));
        line.getElement(THIRD_NOTE).setAccidentalInParentheses(true);
        return line;
    }

    /**
     * Switches {@code line}'s song mock from "tracking suspended" — the state the fixture is built
     * under — to one that records, so a test can observe what {@link Line#modifyElement} emits.
     * The recorded mutation's own runnable is a no-op by contract, so running it is harmless.
     */
    private static Song startRecording(Line line) {
        var song = line.getSong();
        when(song.isMutationTrackingSuspended()).thenReturn(false);
        when(song.isModifying()).thenReturn(true);
        doAnswer(answerVoid((Mutation mutation, Runnable mutator) -> mutator.run())).when(song).applyChange(any(Mutation.class), any(Runnable.class));
        return song;
    }

    /** A removal and a materialization in one edit — the shape a toggle-off produces. */
    private static List<AccidentalReconciliation.AccidentalChange> clearAndSet(Line line) {
        return List.of(
            new AccidentalReconciliation.AccidentalChange(line.getElement(THIRD_NOTE), null),
            new AccidentalReconciliation.AccidentalChange(
                line.getElement(SECOND_NOTE), StaffElement.Accidental.FLAT));
    }

    @Test
    void testARefusedGateRestoresEveryAccidentalAndParenthesesFlagAndRecordsNothing() {
        var line = fixtureLine();
        var song = startRecording(line);

        var accepted = AccidentalMaterializer.applyIfAccepted(line, clearAndSet(line), List.of(), () -> false);

        assertThat(accepted).isFalse();
        assertThat(line.getElement(FIRST_NOTE).getAccidental()).isEqualTo(StaffElement.Accidental.SHARP);
        assertThat(line.getElement(SECOND_NOTE).getAccidental()).isNull();
        assertThat(line.getElement(THIRD_NOTE).getAccidental()).isEqualTo(StaffElement.Accidental.NATURAL);
        // The parentheses flag is the part setAccidental silently drops on a removal, so a
        // restore that only put the accidental back would lose it here.
        assertThat(line.getElement(THIRD_NOTE).isAccidentalInParentheses()).isTrue();
        verify(song, never()).applyChange(any(), any());
    }

    @Test
    void testAnAcceptedGateAppliesEachChangeAndRecordsItExactlyOnce() {
        var line = fixtureLine();
        var song = startRecording(line);

        var accepted = AccidentalMaterializer.applyIfAccepted(line, clearAndSet(line), List.of(), () -> true);

        assertThat(accepted).isTrue();
        assertThat(line.getElement(THIRD_NOTE).getAccidental()).isNull();
        assertThat(line.getElement(THIRD_NOTE).isAccidentalInParentheses()).isFalse();
        assertThat(line.getElement(SECOND_NOTE).getAccidental()).isEqualTo(StaffElement.Accidental.FLAT);
        verify(song, times(TWO_CHANGES)).applyChange(any(ElementModification.class), any());
    }

    @Test
    void testADetachedElementIsChangedButNeitherSavedNorRecorded() {
        // A clone or a preview element carries no line back-reference, so there is nothing to roll
        // back and nothing an undo step could reach.
        var line = fixtureLine();
        var song = startRecording(line);
        var detached = note(F_STAFF_POSITION);

        var accepted = AccidentalMaterializer.applyIfAccepted(
            line,
            List.of(new AccidentalReconciliation.AccidentalChange(detached, StaffElement.Accidental.SHARP)),
            List.of(detached),
            () -> true);

        assertThat(accepted).isTrue();
        assertThat(detached.getAccidental()).isEqualTo(StaffElement.Accidental.SHARP);
        verify(song, never()).applyChange(any(), any());
    }

    @Test
    void testADetachedElementKeepsItsAccidentalEvenWhenTheGateRefuses() {
        // Nothing is restored for a detached element, because nothing was saved: the caller
        // discards the element itself on refusal.
        var line = fixtureLine();
        var detached = note(F_STAFF_POSITION);

        var accepted = AccidentalMaterializer.applyIfAccepted(
            line,
            List.of(new AccidentalReconciliation.AccidentalChange(detached, StaffElement.Accidental.SHARP)),
            List.of(detached),
            () -> false);

        assertThat(accepted).isFalse();
        assertThat(detached.getAccidental()).isEqualTo(StaffElement.Accidental.SHARP);
    }

    @Test
    void testClearingAParenthesizedAccidentalNamesTheParenthesesFieldAsWellAsTheAccidental() {
        // The field set identifies which fields changed, so a subscriber filtering on it would
        // otherwise miss the parentheses the removal drops.
        var parenthesized = note(F_STAFF_POSITION, StaffElement.Accidental.NATURAL);
        parenthesized.setAccidentalInParentheses(true);

        assertThat(AccidentalMaterializer.changedFields(parenthesized, null))
            .containsExactlyInAnyOrder(ElementField.ACCIDENTAL, ElementField.ACCIDENTAL_IN_PARENS);
    }

    @Test
    void testClearingAnUnparenthesizedAccidentalNamesOnlyTheAccidentalField() {
        // The parentheses field is named only when the flag will actually flip. Naming it
        // unconditionally on a removal would tell a subscriber a field changed that did not.
        var plain = note(F_STAFF_POSITION, StaffElement.Accidental.NATURAL);

        assertThat(AccidentalMaterializer.changedFields(plain, null))
            .containsExactly(ElementField.ACCIDENTAL);
    }

    @Test
    void testSettingAnAccidentalNamesOnlyTheAccidentalField() {
        var parenthesized = note(F_STAFF_POSITION, StaffElement.Accidental.NATURAL);
        parenthesized.setAccidentalInParentheses(true);

        assertThat(AccidentalMaterializer.changedFields(parenthesized, StaffElement.Accidental.SHARP))
            .containsExactly(ElementField.ACCIDENTAL);
    }
}
