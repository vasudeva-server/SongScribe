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

package songscribe.music;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.ui.MusicEditOperations;
import songscribe.ui.selection.ReflectionTestHelper;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Unit tests for beam toggling and stem direction flipping via
 * {@link MusicEditOperations}, using the {@code connections} fixture.
 */
class BeamToggleTest extends UnitTest {

    // Element indices in connections.mssw
    private static final int EIGHTH_1 = 1;
    private static final int EIGHTH_2 = 2;

    private static Composition composition;
    private static Line line;
    private static SelectionCoordinator coordinator;
    private static MusicEditOperations operations;

    @BeforeAll
    static void loadFixtureData() throws Exception {
        composition = loadFixture("connections");
        line = composition.getLine(0);
        coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
        operations = new MusicEditOperations(composition, coordinator);
    }

    @Nested
    class FlipStemDirection {

        @Test
        void testFlipStemBackRestoresDirection() {
            selectRange(EIGHTH_1, EIGHTH_2);
            operations.toggleBeaming();

            var note = line.getElement(EIGHTH_1);
            var upperBefore = note.isUpper();

            operations.flipStemDirection();
            operations.flipStemDirection();

            assertThat(note.isUpper())
                .as("isUpper restored after double flip")
                .isEqualTo(upperBefore);

            operations.toggleBeaming();
        }

        @Test
        void testFlipStemWhileBeamedChangesDirection() {
            selectRange(EIGHTH_1, EIGHTH_2);
            operations.toggleBeaming();

            var note = line.getElement(EIGHTH_1);
            var upperBefore = note.isUpper();

            operations.flipStemDirection();

            assertAll(
                () -> assertThat(note.isStemDirectionAuto())
                    .as("stem direction not auto").isFalse(),
                () -> assertThat(note.isUpper())
                    .as("isUpper changed").isNotEqualTo(upperBefore)
            );

            // Restore: flip back and remove beam
            operations.flipStemDirection();
            operations.toggleBeaming();
        }

        @Test
        void testFlipStemUnbeamedWithPersistence() throws Exception {
            selectRange(EIGHTH_1, EIGHTH_2);

            var note = line.getElement(EIGHTH_1);
            var autoBeforeFlip = note.isStemDirectionAuto();
            var upperBefore = note.isUpper();

            operations.flipStemDirection();

            assertThat(note.isStemDirectionAuto())
                .as("stem direction not auto").isFalse();
            assertThat(note.isUpper())
                .as("isUpper changed").isNotEqualTo(upperBefore);

            var flippedUpper = note.isUpper();
            var reloaded = roundTrip(composition);
            var reloadedNote = reloaded.getLine(0).getElement(EIGHTH_1);

            assertAll(
                () -> assertThat(reloadedNote.isStemDirectionAuto())
                    .as("save/load: not auto").isFalse(),
                () -> assertThat(reloadedNote.isUpper())
                    .as("save/load: isUpper preserved").isEqualTo(flippedUpper)
            );

            // Restore original state
            note.setStemDirectionAuto(autoBeforeFlip);
            note.setUpper(upperBefore);
        }
    }

    @Nested
    class ToggleBeam {

        @Test
        void testToggleBeamOff() {
            selectRange(EIGHTH_1, EIGHTH_2);
            operations.toggleBeaming();

            assertAll(
                () -> assertThat(line.getBeamings().findSpan(EIGHTH_1))
                    .as("note 1 beamed").isNotNull(),
                () -> assertThat(line.getBeamings().findSpan(EIGHTH_2))
                    .as("note 2 beamed").isNotNull()
            );

            operations.toggleBeaming();

            assertAll(
                () -> assertThat(line.getBeamings().findSpan(EIGHTH_1))
                    .as("note 1 unbeamed").isNull(),
                () -> assertThat(line.getBeamings().findSpan(EIGHTH_2))
                    .as("note 2 unbeamed").isNull()
            );
        }

        @Test
        void testToggleBeamOn() {
            selectRange(EIGHTH_1, EIGHTH_2);
            operations.toggleBeaming();

            assertAll(
                () -> assertThat(line.getBeamings().findSpan(EIGHTH_1))
                    .as("note 1 beamed").isNotNull(),
                () -> assertThat(line.getBeamings().findSpan(EIGHTH_2))
                    .as("note 2 beamed").isNotNull()
            );

            // Clean up
            operations.toggleBeaming();
        }
    }

    private static void selectRange(int from, int to) {
        ReflectionTestHelper.selectRange(coordinator, from, to);
    }
}
