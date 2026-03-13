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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import songscribe.music.Composition;
import songscribe.music.ElementType;
import songscribe.music.KeyType;
import songscribe.music.Line;
import songscribe.ui.component.MainFrame;

/**
 * Milestone 1 E2E tests: programmatic save/load round-trip, model equality.
 */
class SaveLoadRoundTripTest extends E2ETest {

    @Test
    void testSaveAndReloadPreservesKeySignature() throws Exception {
        var original = new Composition(MainFrame.getInstance().getProfileManager());
        original.setDefaultKeyType(KeyType.SHARPS);
        original.setDefaultKeyAccidentalCount(3);

        // Add a line so the composition is non-empty
        var line = new Line();
        var note = ElementType.CROTCHET.newInstance();
        note.setStaffPosition(0);
        line.addElement(note);
        original.addLine(line);

        var reloaded = roundTrip(original);

        assertThat(reloaded.getDefaultKeyType()).isEqualTo(KeyType.SHARPS);
        assertThat(reloaded.getDefaultKeyAccidentalCount()).isEqualTo(3);
    }

    @Test
    void testSaveAndReloadPreservesNotes() throws Exception {
        // Build a composition with notes of different types and staff positions
        var original = buildTestComposition();
        var originalLine = original.getLine(0);
        var noteCount = originalLine.elementCount();
        var originalTypes = new ElementType[noteCount];
        var originalPositions = new int[noteCount];

        for (var i = 0; i < noteCount; i++) {
            var note = originalLine.getElement(i);
            originalTypes[i] = note.getType();
            originalPositions[i] = note.getStaffPosition();
        }

        // Round-trip through XML
        var reloaded = roundTrip(original);

        // Verify structure
        assertThat(reloaded.lineCount()).isEqualTo(original.lineCount());

        var reloadedLine = reloaded.getLine(0);
        assertThat(reloadedLine.elementCount()).isEqualTo(noteCount);

        // Verify each note's type and staff position
        for (var i = 0; i < noteCount; i++) {
            var note = reloadedLine.getElement(i);
            assertThat(note.getType())
                .as("NoteType at index %d", i)
                .isEqualTo(originalTypes[i]);
            assertThat(note.getStaffPosition())
                .as("staffPosition at index %d", i)
                .isEqualTo(originalPositions[i]);
        }
    }


    // -- Helpers --

    private Composition buildTestComposition() {
        var composition = new Composition(MainFrame.getInstance().getProfileManager());
        var line = new Line();

        // Quarter at middle line
        var quarter = ElementType.CROTCHET.newInstance();
        quarter.setStaffPosition(0);
        line.addElement(quarter);

        // Eighth below
        var eighth = ElementType.QUAVER.newInstance();
        eighth.setStaffPosition(-4);
        line.addElement(eighth);

        // Half above
        var half = ElementType.MINIM.newInstance();
        half.setStaffPosition(4);
        line.addElement(half);

        // Sixteenth far below
        var sixteenth = ElementType.SEMIQUAVER.newInstance();
        sixteenth.setStaffPosition(-8);
        line.addElement(sixteenth);

        composition.addLine(line);
        return composition;
    }

}
