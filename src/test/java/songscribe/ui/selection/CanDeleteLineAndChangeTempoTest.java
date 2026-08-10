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

package songscribe.ui.selection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.quaver;

import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.hit.HitTarget;
import songscribe.ui.component.ScoreView;

/**
 * Unit tests for {@link SelectionCoordinator#canDeleteLine} and
 * {@link SelectionCoordinator#canChangeTempo}.
 *
 * <p>Rows 30–34 of the selection coordinator test matrix.
 */
class CanDeleteLineAndChangeTempoTest extends UnitTest {

    // Line index constant used across tests.
    private static final int LINE_0 = 0;

    // -------------------------------------------------------------------------
    // Shared fixture builders
    // -------------------------------------------------------------------------

    /**
     * Builds a coordinator whose song has exactly one line, with that line
     * registered at index 0 and activated. The line holds one quarter note.
     */
    private SelectionCoordinator oneLineSongCoordinator() {
        var song = new Song();
        var line = song.getLine(LINE_0);
        song.withoutMutationTracking(() -> line.addElement(0, crotchet()));

        var coordinator = new SelectionCoordinator(mock(ScoreView.class));
        coordinator.registerLine(LINE_0, line);
        coordinator.activateLine(LINE_0);
        return coordinator;
    }

    /**
     * Builds a coordinator whose song has two lines, with the first line
     * registered at index 0 and activated. The first line holds one quarter note.
     */
    private SelectionCoordinator twoLineSongCoordinator() {
        var song = new Song();
        var firstLine = song.getLine(LINE_0);
        song.withoutMutationTracking(() -> firstLine.addElement(0, crotchet()));
        song.addLine(new Line(song));

        var coordinator = new SelectionCoordinator(mock(ScoreView.class));
        coordinator.registerLine(LINE_0, firstLine);
        coordinator.activateLine(LINE_0);
        return coordinator;
    }

    // -------------------------------------------------------------------------
    // canDeleteLine — row 30
    // -------------------------------------------------------------------------

    /**
     * Row 30: canDeleteLine returns false when there is no active line
     * (activeLineIndex == -1).
     */
    @Test
    void testCanDeleteLineReturnsFalseWhenNoActiveLine() {
        // Coordinator with no registered lines and no active line.
        var coordinator = new SelectionCoordinator(mock(ScoreView.class));

        assertThat(coordinator.canDeleteLine())
            .as("canDeleteLine with no active line")
            .isFalse();
    }

    // -------------------------------------------------------------------------
    // canDeleteLine — row 31
    // -------------------------------------------------------------------------

    /**
     * Row 31: canDeleteLine returns false when the active line exists but is not
     * line-selected (isLineSelected is false).
     */
    @Test
    void testCanDeleteLineReturnsFalseWhenLineNotLineSelected() {
        var coordinator = oneLineSongCoordinator();
        // Active line is not line-selected — no HitTarget.StaffLine selected on it.

        assertThat(coordinator.canDeleteLine())
            .as("canDeleteLine when active line is not line-selected")
            .isFalse();
    }

    // -------------------------------------------------------------------------
    // canDeleteLine — row 32
    // -------------------------------------------------------------------------

    /**
     * Row 32: canDeleteLine returns true when the line is line-selected even
     * though the song has only one line (lineCount() == 1) — deleting the sole
     * remaining line is allowed and replaces it with a fresh empty line.
     */
    @Test
    void testCanDeleteLineReturnsTrueWhenOnlyOneLine() {
        var coordinator = oneLineSongCoordinator();
        coordinator.select(new HitTarget.StaffLine());

        assertThat(coordinator.canDeleteLine())
            .as("canDeleteLine when line is selected and song has only one line")
            .isTrue();
    }

    // -------------------------------------------------------------------------
    // canDeleteLine — row 33
    // -------------------------------------------------------------------------

    /**
     * Row 33: canDeleteLine returns true when the active line is line-selected
     * and the song has more than one line.
     */
    @Test
    void testCanDeleteLineReturnsTrueWhenLineSelectedAndMultipleLines() {
        var coordinator = twoLineSongCoordinator();
        coordinator.select(new HitTarget.StaffLine());

        assertThat(coordinator.canDeleteLine())
            .as("canDeleteLine when line is selected and song has two lines")
            .isTrue();
    }

    // -------------------------------------------------------------------------
    // canChangeTempo — row 34
    // -------------------------------------------------------------------------

    /**
     * Row 34: canChangeTempo returns false when there is no active selection
     * (getActiveLine() returns null).
     */
    @Test
    void testCanChangeTempoReturnsFalseWithNoActiveSelection() {
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(crotchet()),
            List.of()
        );
        // Nothing is selected.

        assertThat(coordinator.canChangeTempo())
            .as("canChangeTempo with no active selection")
            .isFalse();
    }

    // -------------------------------------------------------------------------
    // canChangeTempo — row 35
    // -------------------------------------------------------------------------

    /**
     * Row 35: canChangeTempo returns false when there is an active selection
     * but more than one element is selected (getSingleSelectedElement() returns null).
     */
    @Test
    void testCanChangeTempoReturnsFalseWithMultiElementSelection() {
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(crotchet(), quaver()),
            List.of()
        );
        ReflectionTestHelper.selectRange(coordinator, 0, 1);

        assertThat(coordinator.canChangeTempo())
            .as("canChangeTempo with two elements selected")
            .isFalse();
    }

    // -------------------------------------------------------------------------
    // canChangeTempo — row 36
    // -------------------------------------------------------------------------

    /**
     * Row 36: canChangeTempo returns true when exactly one element is selected.
     */
    @Test
    void testCanChangeTempoReturnsTrueWithSingleElementSelected() {
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(crotchet(), quaver()),
            List.of()
        );
        ReflectionTestHelper.selectNote(coordinator, 0);

        assertThat(coordinator.canChangeTempo())
            .as("canChangeTempo with exactly one element selected")
            .isTrue();
    }

    // -------------------------------------------------------------------------
    // canChangeTempo — the song's first element is off limits
    // -------------------------------------------------------------------------

    /**
     * Builds a coordinator over a real two-note song, so the coordinator's score view answers
     * with a song that has a genuine first element. A song mock answers null there, which
     * leaves the first-element rule inert.
     */
    private SelectionCoordinator realSongCoordinator() {
        var song = new Song();
        var line = song.getLine(LINE_0);
        song.withoutMutationTracking(() -> {
            line.addElement(0, crotchet());
            line.addElement(1, quaver());
        });

        return ReflectionTestHelper.createCoordinatorForLine(line);
    }

    /**
     * A tempo change on the song's first element is forbidden: the song's own tempo is already
     * drawn at the staff header there, and Song Settings is where it is edited. Without this,
     * the user could put a second, competing tempo mark on the very first note.
     */
    @Test
    void testCanChangeTempoReturnsFalseWhenTheSongsFirstElementIsSelected() {
        var coordinator = realSongCoordinator();
        ReflectionTestHelper.selectNote(coordinator, 0);

        assertThat(coordinator.canChangeTempo())
            .as("canChangeTempo with the song's first element selected")
            .isFalse();
    }

    /**
     * The companion to the test above, on the same song: every element other than the first
     * still accepts a tempo change, so the rule cannot be satisfied by refusing everything.
     */
    @Test
    void testCanChangeTempoReturnsTrueWhenALaterElementIsSelected() {
        var coordinator = realSongCoordinator();
        ReflectionTestHelper.selectNote(coordinator, 1);

        assertThat(coordinator.canChangeTempo())
            .as("canChangeTempo with the song's second element selected")
            .isTrue();
    }
}
