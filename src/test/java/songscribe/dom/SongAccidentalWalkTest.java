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

package songscribe.dom;

import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.message.mutation.ElementField;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.compactSong;

/**
 * {@link Song#applyChange}'s promise that every recorded edit leaves the song exactly as a
 * fresh {@link AccidentalChain#walkSong} would decide it, and that undoing an edit — by
 * recording its structural inverse — restores every note's prior state.
 *
 * <p>Each case records one edit through the same public route the UI edits through (an
 * {@code addElement}, {@code removeElement}, {@code modifyElement}, {@code setKey},
 * {@code addLine} or {@code removeLine} inside a modification bracket), which is what makes the
 * song run the chain walk in the first place; there is no separate call to trigger it.
 */
class SongAccidentalWalkTest extends UnitTest {

    private static final int F = 3;
    private static final int OTHER = 4;

    /** One note's decided state, independent of the note object's identity. */
    private record NoteSnapshot(int staffPosition, ChainedAccidental chained) {}

    private static List<NoteSnapshot> snapshot(Song song) {
        return song.getLines().stream()
            .flatMap(line -> line.getElements().stream())
            .filter(NoteElement.class::isInstance)
            .map(NoteElement.class::cast)
            .map(note -> new NoteSnapshot(note.getStaffPosition().value(), note.chainedAccidental()))
            .toList();
    }

    /**
     * Records {@code edit}, asserts the song already matches a fresh walk, then records
     * {@code undo} and asserts every note is back where {@code before} found it.
     */
    private static void assertEditWalksItselfAndUndoRestores(Song song, Runnable edit, Runnable undo) {
        var before = snapshot(song);

        song.withModification(edit);
        assertThat(AccidentalChain.walkSong(new AccidentalScanView.Live(song)))
            .as("the recorded edit already leaves every note as a fresh walk would decide it")
            .isEmpty();

        song.withModification(undo);
        assertThat(snapshot(song)).as("undoing the edit restores every note's prior state").isEqualTo(before);
    }

    @Test
    void testANoteInsertion() {
        var song = compactSong(Key.NO_ACCIDENTALS, F + "f " + F);
        var line = song.getLine(0);
        var inserted = StaffElementFactory.note(OTHER);

        assertEditWalksItselfAndUndoRestores(
            song,
            () -> line.addElement(1, inserted),
            () -> line.removeElement(line.getElementIndex(inserted)));
    }

    @Test
    void testARangeDeletion() {
        var song = compactSong(Key.NO_ACCIDENTALS, F + "f " + F + " " + F);
        var line = song.getLine(0);
        var removed = (NoteElement) line.getElement(1);

        assertEditWalksItselfAndUndoRestores(
            song,
            () -> line.removeElement(1),
            () -> line.addElement(1, removed));
    }

    @Test
    void testAPitchChangeToAdopting() {
        var song = compactSong(Key.NO_ACCIDENTALS, F + "f " + F);
        var line = song.getLine(0);
        var note = (NoteElement) line.getElement(1);
        var beforeState = note.clone();

        assertEditWalksItselfAndUndoRestores(
            song,
            () -> line.modifyElement(1, ElementField.ACCIDENTAL, () -> note.setAccidentalState(new AccidentalState.Adopting())),
            () -> line.modifyElement(1, ElementField.ACCIDENTAL, () -> note.copyStateFrom(beforeState)));
    }

    @Test
    void testAKeyChangeInsertion() {
        var song = compactSong(Key.NO_ACCIDENTALS, F + "f " + F);
        var line = song.getLine(0);
        var keyChange = StaffElementFactory.keyChange(Key.ONE_SHARP);

        assertEditWalksItselfAndUndoRestores(
            song,
            () -> line.addElement(0, keyChange),
            () -> line.removeElement(line.getElementIndex(keyChange)));
    }

    @Test
    void testALineDeletion() {
        var song = compactSong(Key.NO_ACCIDENTALS, F + "f " + F, "" + F);
        var removedLine = song.getLine(1);

        assertEditWalksItselfAndUndoRestores(
            song,
            () -> song.removeLine(1),
            () -> song.addLine(1, removedLine));
    }
}
