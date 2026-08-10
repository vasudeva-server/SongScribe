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

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.hit.HitTarget;
import songscribe.ui.component.ScoreView;

/**
 * Unit tests for {@link SelectionCoordinator#isSelected} asked about a
 * {@link HitTarget.Lyric} — the one target that names its own line, and the one whose store
 * moved onto the coordinator rather than having always lived there.
 *
 * <p>Rows 27 and 28 of the selection coordinator test matrix.
 */
class IsSelectedForLyricTest extends UnitTest {

    // Named verse indices used across tests.
    private static final int VERSE_1 = 1;
    private static final int VERSE_2 = 2;
    private static final int VERSE_3 = 3;

    // Line index constants.
    private static final int LINE_0 = 0;
    private static final int LINE_1 = 1;

    // -------------------------------------------------------------------------
    // Shared fixture builder
    // -------------------------------------------------------------------------

    /**
     * Returns a coordinator with two registered lines, each containing one
     * quarter note, with line 0 activated but nothing selected yet.
     */
    private SelectionCoordinator twoLineCoordinator(Song song) {
        var coordinator = new SelectionCoordinator(mock(ScoreView.class));

        var lineA = new Line(song);
        lineA.addElement(crotchet());
        coordinator.registerLine(LINE_0, lineA);

        var lineB = new Line(song);
        lineB.addElement(crotchet());
        coordinator.registerLine(LINE_1, lineB);

        coordinator.activateLine(LINE_0);
        return coordinator;
    }

    // -------------------------------------------------------------------------
    // Row 27 — early-return paths
    // -------------------------------------------------------------------------

    /**
     * Row 27a: a selected lyric is not reported when activeLineIndex != lineIndex,
     * even though it is the score's one selected target.
     */
    @Test
    void testLyricIsNotSelectedWhenLineIndexDoesNotMatchActiveLine() {
        var song = minimalSongMock();
        var coordinator = twoLineCoordinator(song);

        // Select a lyric on line 0 (the active line).
        var line = coordinator.getLine(LINE_0);
        assertThat(line).isNotNull();

        var element = line.getElement(0);
        coordinator.selectLyric(element, VERSE_1);
        assertThat(coordinator.getActiveLineIndex()).as("active line after selectLyric").isEqualTo(LINE_0);

        // Query against line 1 — activeLineIndex(0) != lineIndex(1) => early return false.
        assertThat(coordinator.isSelected(new HitTarget.Lyric(element, VERSE_1), LINE_1))
            .as("isSelected(lyric) when queried line does not match active line")
            .isFalse();
    }

    /**
     * Row 27b: no lyric is reported when the active line matches but nothing is
     * selected at all.
     */
    @Test
    void testLyricIsNotSelectedWhenNothingIsSelected() {
        var song = minimalSongMock();
        var coordinator = twoLineCoordinator(song);
        // Line 0 is active; no lyric selected yet.
        assertThat(coordinator.getSelectedTarget()).as("selected target before any selectLyric").isNull();

        var line = coordinator.getLine(LINE_0);
        assertThat(line).isNotNull();

        var element = line.getElement(0);

        assertThat(coordinator.isSelected(new HitTarget.Lyric(element, VERSE_1), LINE_0))
            .as("isSelected(lyric) with no lyric selection on active line")
            .isFalse();
    }

    // -------------------------------------------------------------------------
    // Row 28 — element reference and verse matching
    // -------------------------------------------------------------------------

    /**
     * Row 28a: the lyric is reported when the element reference and verse both match
     * the selected target.
     */
    @Test
    void testLyricIsSelectedForMatchingElementAndVerse() {
        var song = minimalSongMock();
        var coordinator = twoLineCoordinator(song);
        var line = coordinator.getLine(LINE_0);
        assertThat(line).isNotNull();

        var element = line.getElement(0);

        coordinator.selectLyric(element, VERSE_2);

        assertThat(coordinator.isSelected(new HitTarget.Lyric(element, VERSE_2), LINE_0))
            .as("isSelected(lyric) for same element and same verse")
            .isTrue();
    }

    /**
     * Row 28b: no lyric is reported when the element matches but the verse is different.
     */
    @Test
    void testLyricIsNotSelectedForWrongVerse() {
        var song = minimalSongMock();
        var coordinator = twoLineCoordinator(song);
        var line = coordinator.getLine(LINE_0);
        assertThat(line).isNotNull();

        var element = line.getElement(0);

        coordinator.selectLyric(element, VERSE_2);

        assertThat(coordinator.isSelected(new HitTarget.Lyric(element, VERSE_3), LINE_0))
            .as("isSelected(lyric) with correct element but wrong verse")
            .isFalse();
    }

    /**
     * Row 28c: no lyric is reported when the verse matches but the element reference is a
     * different object.
     */
    @Test
    void testLyricIsNotSelectedForDifferentElement() {
        var song = minimalSongMock();
        var coordinator = twoLineCoordinator(song);
        var line = coordinator.getLine(LINE_0);
        assertThat(line).isNotNull();

        var lineA = line;
        var elementA = lineA.getElement(0);
        // Add a second element to line A so we have a distinct reference.
        lineA.addElement(crotchet());
        var elementB = lineA.getElement(1);

        coordinator.selectLyric(elementA, VERSE_2);

        assertThat(coordinator.isSelected(new HitTarget.Lyric(elementB, VERSE_2), LINE_0))
            .as("isSelected(lyric) with correct verse but a different element reference")
            .isFalse();
    }
}
