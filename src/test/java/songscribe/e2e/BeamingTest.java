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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.ui.action.Actions;

/**
 * Milestone 2 E2E tests: automatic beaming, manual toggle, stem direction.
 */
class BeamingTest extends E2ETest {

    @Test
    void testAutoBeamingOnInsertion() {
        enterEditMode();
        selectDuration(Actions.EIGHTH_NOTE_ACTION);

        // Insert two consecutive eighth notes
        clickAt(insertionPoint(0, 0));
        performLayout(0);
        clickAt(insertionPoint(0, -2));
        performLayout(0);

        var line = composition().getLine(0);
        assertThat(line.elementCount()).isEqualTo(2);

        // Both notes should be in a beam group
        assertThat(isBeamed(0, 0)).isTrue();
        assertThat(isBeamed(0, 1)).isTrue();

        // They should be in the same beam interval
        var beam = line.getBeamings().findInterval(0);
        assertThat(beam).isNotNull();
        assertThat(beam.getStart()).isEqualTo(0);
        assertThat(beam.getEnd()).isEqualTo(1);
    }

    @Nested
    class ManualBeamToggle {

        @Test
        void testToggleBeamingOnSelection() {
            // Build composition with two unbeamed eighth notes
            buildUnbeamedEighthNotes();

            var line = composition().getLine(0);
            assertThat(line.getBeamings().isEmpty()).isTrue();

            // Select both notes
            enterSelectMode();
            clickAt(noteScreenPosition(0, 0));
            shiftClickAt(noteScreenPosition(0, 1));
            assertThat(score().getSelectionSize()).isEqualTo(2);

            // Toggle beam on
            triggerAction(Actions.TOGGLE_BEAM_ACTION);
            performLayout(0);

            // Beam interval should now exist
            assertThat(isBeamed(0, 0)).isTrue();
            assertThat(isBeamed(0, 1)).isTrue();
        }

        @Test
        void testToggleBeamingRemovesExistingBeam() {
            // Build composition with beamed eighth notes
            buildBeamedEighthNotes();

            var line = composition().getLine(0);
            assertThat(line.getBeamings().isEmpty()).isFalse();

            // Select the beamed notes
            enterSelectMode();
            clickAt(noteScreenPosition(0, 0));
            shiftClickAt(noteScreenPosition(0, 1));
            assertThat(score().getSelectionSize()).isEqualTo(2);

            // Toggle beam off
            triggerAction(Actions.TOGGLE_BEAM_ACTION);
            performLayout(0);

            // Beam interval should be removed
            assertThat(isBeamed(0, 0)).isFalse();
            assertThat(isBeamed(0, 1)).isFalse();
        }
    }

    @Nested
    class StemDirection {

        @Test
        void testFlipStemDirection() {
            // Build composition with beamed eighth notes
            buildBeamedEighthNotes();

            var note = composition().getLine(0).getElement(0);
            assertThat(note.isStemDirectionAuto()).isTrue();
            var originalUpper = note.isUpper();

            // Select the note and flip stem
            enterSelectMode();
            clickAt(noteScreenPosition(0, 0));

            triggerAction(Actions.FLIP_STEM_DIRECTION_ACTION);

            assertThat(note.isStemDirectionAuto()).isFalse();
            assertThat(note.isUpper()).isNotEqualTo(originalUpper);
        }

        @Test
        void testFlipStemDirectionUnbeamed() {
            // Build composition with unbeamed quarter notes
            buildTwoQuarterNotes();

            var note = composition().getLine(0).getElement(0);
            assertThat(note.isStemDirectionAuto()).isTrue();
            var originalUpper = note.isUpper();

            // Select the note and flip stem
            enterSelectMode();
            clickAt(noteScreenPosition(0, 0));

            triggerAction(Actions.FLIP_STEM_DIRECTION_ACTION);

            assertThat(note.isStemDirectionAuto()).isFalse();
            assertThat(note.isUpper()).isNotEqualTo(originalUpper);
        }

        @Test
        void testStemDirectionPersistsThroughSaveLoad() throws Exception {
            // Build composition and flip a note's stem
            buildTwoQuarterNotes();

            var originalNote = composition().getLine(0).getElement(0);
            enterSelectMode();
            clickAt(noteScreenPosition(0, 0));

            triggerAction(Actions.FLIP_STEM_DIRECTION_ACTION);

            var flippedUpper = originalNote.isUpper();
            assertThat(originalNote.isStemDirectionAuto()).isFalse();

            // Round-trip save/load
            var reloaded = roundTrip(composition());

            var reloadedNote = reloaded.getLine(0).getElement(0);
            assertThat(reloadedNote.isStemDirectionAuto()).isFalse();
            assertThat(reloadedNote.isUpper()).isEqualTo(flippedUpper);
        }
    }


    // -- Helpers --

    private void buildUnbeamedEighthNotes() {
        // Insert two eighths (auto-beams them), then remove the beam
        selectDuration(Actions.EIGHTH_NOTE_ACTION);
        clickAt(insertionPoint(0, 0));
        performLayout(0);
        clickAt(insertionPoint(0, -2));
        performLayout(0);

        enterSelectMode();
        clickAt(noteScreenPosition(0, 0));
        shiftClickAt(noteScreenPosition(0, 1));
        triggerAction(Actions.TOGGLE_BEAM_ACTION);
        performLayout(0);
        enterEditMode();
    }

    private void buildBeamedEighthNotes() {
        // Insert two consecutive eighths — auto-beaming creates the beam
        selectDuration(Actions.EIGHTH_NOTE_ACTION);
        clickAt(insertionPoint(0, 0));
        performLayout(0);
        clickAt(insertionPoint(0, -2));
        performLayout(0);
    }

    private void buildTwoQuarterNotes() {
        selectDuration(Actions.QUARTER_NOTE_ACTION);
        clickAt(insertionPoint(0, 0));
        performLayout(0);
        clickAt(insertionPoint(0, -2));
        performLayout(0);
    }

}
