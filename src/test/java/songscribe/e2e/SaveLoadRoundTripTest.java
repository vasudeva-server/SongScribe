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

package songscribe.e2e;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import songscribe.music.Composition;
import songscribe.music.KeyType;
import songscribe.music.Line;
import songscribe.music.NoteType;
import songscribe.ui.component.MainFrame;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Milestone 1 E2E tests: programmatic save/load round-trip, model equality.
 */
@Order(6)
class SaveLoadRoundTripTest extends BaseSwingTest {

    @Test @Order(1)
    void testSaveAndReloadPreservesNotes() throws Exception {
        // Build a composition with notes of different types and staff positions
        var original = buildTestComposition();
        var originalLine = original.getLine(0);
        var noteCount = originalLine.noteCount();
        var originalTypes = new NoteType[noteCount];
        var originalPositions = new int[noteCount];

        for (var i = 0; i < noteCount; i++) {
            var note = originalLine.getNote(i);
            originalTypes[i] = note.getNoteType();
            originalPositions[i] = note.getStaffPosition();
        }

        // Round-trip through XML
        var reloaded = roundTrip(original);

        // Verify structure
        assertThat(reloaded.lineCount()).isEqualTo(original.lineCount());

        var reloadedLine = reloaded.getLine(0);
        assertThat(reloadedLine.noteCount()).isEqualTo(noteCount);

        // Verify each note's type and staff position
        for (var i = 0; i < noteCount; i++) {
            var note = reloadedLine.getNote(i);
            assertThat(note.getNoteType())
                .as("NoteType at index %d", i)
                .isEqualTo(originalTypes[i]);
            assertThat(note.getStaffPosition())
                .as("staffPosition at index %d", i)
                .isEqualTo(originalPositions[i]);
        }
    }

    @Test @Order(2)
    void testSaveAndReloadPreservesKeySignature() throws Exception {
        var original = new Composition(MainFrame.getInstance());
        original.setDefaultKeyType(KeyType.SHARPS);
        original.setDefaultKeyAccidentalCount(3);

        // Add a line so the composition is non-empty
        var line = new Line();
        var note = NoteType.CROTCHET.newInstance();
        note.setStaffPosition(0);
        line.addNote(note);
        original.addLine(line);

        var reloaded = roundTrip(original);

        assertThat(reloaded.getDefaultKeyType()).isEqualTo(KeyType.SHARPS);
        assertThat(reloaded.getDefaultKeyAccidentalCount()).isEqualTo(3);
    }


    // -- Helpers --

    private Composition buildTestComposition() {
        var composition = new Composition(MainFrame.getInstance());
        var line = new Line();

        // Quarter at middle line
        var quarter = NoteType.CROTCHET.newInstance();
        quarter.setStaffPosition(0);
        line.addNote(quarter);

        // Eighth below
        var eighth = NoteType.QUAVER.newInstance();
        eighth.setStaffPosition(-4);
        line.addNote(eighth);

        // Half above
        var half = NoteType.MINIM.newInstance();
        half.setStaffPosition(4);
        line.addNote(half);

        // Sixteenth far below
        var sixteenth = NoteType.SEMIQUAVER.newInstance();
        sixteenth.setStaffPosition(-8);
        line.addNote(sixteenth);

        composition.addLine(line);
        return composition;
    }

}
