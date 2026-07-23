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

import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;

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
        song.withoutMutationTracking(() -> line.addElement(0, ElementType.CROTCHET.newInstance()));

        var coordinator = new SelectionCoordinator();
        coordinator.registerLineState(LINE_0, new LineSelectionState(line));
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
        song.withoutMutationTracking(() -> firstLine.addElement(0, ElementType.CROTCHET.newInstance()));
        song.addLine(new Line(song));

        var coordinator = new SelectionCoordinator();
        coordinator.registerLineState(LINE_0, new LineSelectionState(firstLine));
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
        var coordinator = new SelectionCoordinator();

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
        // Active line is not line-selected — no call to setLineSelected(true).

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
        Objects.requireNonNull(coordinator.getLineState(LINE_0)).setLineSelected(true);

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
        Objects.requireNonNull(coordinator.getLineState(LINE_0)).setLineSelected(true);

        assertThat(coordinator.canDeleteLine())
            .as("canDeleteLine when line is selected and song has two lines")
            .isTrue();
    }

    // -------------------------------------------------------------------------
    // canChangeTempo — row 34
    // -------------------------------------------------------------------------

    /**
     * Row 34: canChangeTempo returns false when there is no active selection
     * (getActiveSelection() returns null).
     */
    @Test
    void testCanChangeTempoReturnsFalseWithNoActiveSelection() {
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(ElementType.CROTCHET.newInstance()),
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
            List.of(ElementType.CROTCHET.newInstance(), ElementType.QUAVER.newInstance()),
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
            List.of(ElementType.CROTCHET.newInstance(), ElementType.QUAVER.newInstance()),
            List.of()
        );
        ReflectionTestHelper.selectNote(coordinator, 0);

        assertThat(coordinator.canChangeTempo())
            .as("canChangeTempo with exactly one element selected")
            .isTrue();
    }
}
