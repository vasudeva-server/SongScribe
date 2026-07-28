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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.message.MessageCenter;
import songscribe.ui.MusicEditOperations;
import songscribe.ui.selection.ReflectionTestHelper;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Unit tests for tie toggling and stem direction flipping on tied notes
 * via {@link MusicEditOperations}, using the {@code connections} fixture.
 */
@SuppressWarnings({ "OverlyBroadThrowsClause", "StaticVariableMayNotBeInitialized", "StaticVariableUsedBeforeInitialization" })
class TieToggleTest extends UnitTest {

    // Element indices in connections.mssw
    private static final int EIGHTH_1 = 1;
    private static final int EIGHTH_2 = 2;
    private static final int TIED_1 = 3;
    private static final int TIED_2 = 4;

    private static Song song;
    private static Line line;
    private static SelectionCoordinator coordinator;
    private static MusicEditOperations operations;

    @BeforeAll
    static void loadFixtureData() throws Exception {
        song = loadFixture("connections");
        line = song.getLine(0);
        coordinator = ReflectionTestHelper.createCoordinatorForLine(line);

        // These tests drive the coordinator directly; on the bus its songDidChange
        // would NPE on the uninitialized Actions constants every time the real
        // fixture song posts, aborting delivery to lower-priority subscribers.
        MessageCenter.unsubscribe(coordinator);

        operations = new MusicEditOperations(song, coordinator);
    }

    @Test
    void testFlipStemWhileTied() {
        selectRange(EIGHTH_1, EIGHTH_2);
        operations.toggleTie();

        var note = line.getElement(EIGHTH_1);
        var upperBefore = note.isUpper();

        operations.flipStemDirection();

        assertThat(note.isUpper())
            .as("stem flipped while tied").isNotEqualTo(upperBefore);

        // Restore
        operations.flipStemDirection();
        operations.toggleTie();
    }

    @Test
    void testTieCreationAndRemoval() {
        selectRange(EIGHTH_1, EIGHTH_2);

        assertThat(line.findTieAt(EIGHTH_1))
            .as("no tie before toggle").isNull();

        operations.toggleTie();

        assertAll(
            () -> assertThat(line.findTieAt(EIGHTH_1))
                .as("note 1 tied").isNotNull(),
            () -> assertThat(line.findTieAt(EIGHTH_2))
                .as("note 2 tied").isNotNull()
        );

        operations.toggleTie();

        assertAll(
            () -> assertThat(line.findTieAt(EIGHTH_1))
                .as("note 1 untied").isNull(),
            () -> assertThat(line.findTieAt(EIGHTH_2))
                .as("note 2 untied").isNull()
        );
    }

    @Test
    void testCanToggleTieWithRealSelection() {
        // The pre-tied pair is two adjacent same-pitch notes, so the command is offered
        // (here to remove the tie). Asserting through MusicEditOperations rather than the
        // selection state covers the delegation the menu actually calls.
        selectRange(TIED_1, TIED_2);

        assertThat(operations.canToggleTie())
            .as("tie command offered for a tied pair").isTrue();
    }

    @Test
    void testCannotToggleTieWithSingleElementSelected() {
        // A tie needs two notes, so one selected element must not offer the command.
        selectRange(EIGHTH_1, EIGHTH_1);

        assertThat(operations.canToggleTie())
            .as("tie command withheld for a single element").isFalse();
    }

    @Test
    void testTiePersistsThroughSaveLoad() throws Exception {
        var tie = line.findTieAt(TIED_1);
        assertThat(tie).as("pre-tied pair exists").isNotNull();

        var reloaded = roundTrip(song);
        var reloadedLine = reloaded.getLine(0);
        var reloadedTie = reloadedLine.findTieAt(TIED_1);

        assertAll(
            () -> assertThat(reloadedTie)
                .as("save/load: tie preserved").isNotNull(),
            () -> assertThat(java.util.Objects.requireNonNull(reloadedTie).getAnchorElementIndex())
                .as("tie start").isEqualTo(TIED_1),
            () -> assertThat(java.util.Objects.requireNonNull(reloadedTie).getEndElementIndex())
                .as("tie end").isEqualTo(TIED_2)
        );
    }

    private static void selectRange(int from, int to) {
        ReflectionTestHelper.selectRange(coordinator, from, to);
    }
}
