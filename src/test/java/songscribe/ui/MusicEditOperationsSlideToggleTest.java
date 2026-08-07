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
package songscribe.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.createNote;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.ui.selection.ReflectionTestHelper;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Tests that {@link MusicEditOperations#canToggleGlissando()}, {@link
 * MusicEditOperations#toggleGlissando}, {@link MusicEditOperations#canToggleFall()} and
 * {@link MusicEditOperations#toggleFall} actually delegate to {@link songscribe.ui.selection.RangeQueries}
 * and {@link SlideOperations} on a real selection, mirroring {@code TieToggleTest}'s coverage of the
 * tie equivalents. {@link MusicEditOperationsNullStateTest} covers only the {@code getRange() == null}
 * guard on these methods; this class covers the non-null branch every one of them falls through to.
 */
class MusicEditOperationsSlideToggleTest extends UnitTest {

    private Line line;
    private SelectionCoordinator coordinator;
    private MusicEditOperations operations;

    @BeforeEach
    void setUp() {
        var song = new Song();
        line = new Line(song);

        song.withoutMutationTracking(() -> {
            line.addElement(createNote(0, false));
            line.addElement(createNote(1, false));
            song.addLine(line);
        });

        coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
        // As in TieToggleTest: songDidChange would NPE on the uninitialized Actions constants
        // every time these real mutations post, aborting delivery to lower-priority subscribers.
        coordinator.unsubscribeForTest();
        operations = new MusicEditOperations(song, coordinator);
    }

    @Test
    void testCanToggleGlissandoDelegatesToRangeQueries() {
        ReflectionTestHelper.selectRange(coordinator, 0, 1);

        assertThat(operations.canToggleGlissando())
            .as("two notes of different pitch offer the glissando toggle").isTrue();
    }

    @Test
    void testToggleGlissandoAddsAGlissandoToTheSource() {
        ReflectionTestHelper.selectRange(coordinator, 0, 1);

        operations.toggleGlissando(null);

        assertThat(line.getElement(0).hasGlissando())
            .as("the glissando lands on the source, the first of the selected pair").isTrue();
    }

    @Test
    void testCanToggleFallDelegatesToRangeQueries() {
        ReflectionTestHelper.selectRange(coordinator, 0, 0);

        assertThat(operations.canToggleFall())
            .as("a lone pitched note offers the fall toggle").isTrue();
    }

    @Test
    void testToggleFallAddsAFallToTheSelectedNote() {
        ReflectionTestHelper.selectRange(coordinator, 0, 0);

        operations.toggleFall(null);

        assertThat(line.getElement(0).hasFall()).isTrue();
    }
}
