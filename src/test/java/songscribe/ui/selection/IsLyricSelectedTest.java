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

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.ui.component.ScoreView;

/**
 * Unit tests for {@link SelectionCoordinator#isLyricSelected}.
 *
 * <p>Rows 27 and 28 of the selection coordinator test matrix.
 */
class IsLyricSelectedTest extends UnitTest {

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
        lineA.addElement(ElementType.CROTCHET.newInstance());
        coordinator.registerLineState(LINE_0, new LineSelectionState(lineA));

        var lineB = new Line(song);
        lineB.addElement(ElementType.CROTCHET.newInstance());
        coordinator.registerLineState(LINE_1, new LineSelectionState(lineB));

        coordinator.activateLine(LINE_0);
        return coordinator;
    }

    // -------------------------------------------------------------------------
    // Row 27 — early-return paths
    // -------------------------------------------------------------------------

    /**
     * Row 27a: isLyricSelected returns false when activeLineIndex != lineIndex,
     * even when a lyric is selected on a different line.
     */
    @Test
    void testIsLyricSelectedReturnsFalseWhenLineIndexDoesNotMatchActiveLine() {
        var song = minimalSongMock();
        var coordinator = twoLineCoordinator(song);

        // Select a lyric on line 0 (the active line).
        var lineState = coordinator.getLineState(LINE_0);
        assertThat(lineState).isNotNull();

        var element = lineState.getLine().getElement(0);
        coordinator.selectLyric(element, VERSE_1);
        assertThat(coordinator.getActiveLineIndex()).as("active line after selectLyric").isEqualTo(LINE_0);

        // Query against line 1 — activeLineIndex(0) != lineIndex(1) => early return false.
        assertThat(coordinator.isLyricSelected(element, VERSE_1, LINE_1))
            .as("isLyricSelected when queried line does not match active line")
            .isFalse();
    }

    /**
     * Row 27b: isLyricSelected returns false when the active line matches but
     * lyricSelection is null (no lyric has been selected).
     */
    @Test
    void testIsLyricSelectedReturnsFalseWhenNoLyricSelected() {
        var song = minimalSongMock();
        var coordinator = twoLineCoordinator(song);
        // Line 0 is active; no lyric selected yet.
        assertThat(coordinator.hasLyricSelection()).as("lyricSelection before any selectLyric").isFalse();

        var lineState = coordinator.getLineState(LINE_0);
        assertThat(lineState).isNotNull();

        var element = lineState.getLine().getElement(0);

        assertThat(coordinator.isLyricSelected(element, VERSE_1, LINE_0))
            .as("isLyricSelected with no lyric selection on active line")
            .isFalse();
    }

    // -------------------------------------------------------------------------
    // Row 28 — element reference and verse matching
    // -------------------------------------------------------------------------

    /**
     * Row 28a: isLyricSelected returns true when the element reference and verse
     * both match the stored lyric selection.
     */
    @Test
    void testIsLyricSelectedReturnsTrueForMatchingElementAndVerse() {
        var song = minimalSongMock();
        var coordinator = twoLineCoordinator(song);
        var lineState = coordinator.getLineState(LINE_0);
        assertThat(lineState).isNotNull();

        var element = lineState.getLine().getElement(0);

        coordinator.selectLyric(element, VERSE_2);

        assertThat(coordinator.isLyricSelected(element, VERSE_2, LINE_0))
            .as("isLyricSelected for same element and same verse")
            .isTrue();
    }

    /**
     * Row 28b: isLyricSelected returns false when the element matches but the
     * verse is different.
     */
    @Test
    void testIsLyricSelectedReturnsFalseForWrongVerse() {
        var song = minimalSongMock();
        var coordinator = twoLineCoordinator(song);
        var lineState = coordinator.getLineState(LINE_0);
        assertThat(lineState).isNotNull();

        var element = lineState.getLine().getElement(0);

        coordinator.selectLyric(element, VERSE_2);

        assertThat(coordinator.isLyricSelected(element, VERSE_3, LINE_0))
            .as("isLyricSelected with correct element but wrong verse")
            .isFalse();
    }

    /**
     * Row 28c: isLyricSelected returns false when the verse matches but the
     * element reference is a different object.
     */
    @Test
    void testIsLyricSelectedReturnsFalseForDifferentElement() {
        var song = minimalSongMock();
        var coordinator = twoLineCoordinator(song);
        var lineState = coordinator.getLineState(LINE_0);
        assertThat(lineState).isNotNull();

        var lineA = lineState.getLine();
        var elementA = lineA.getElement(0);
        // Add a second element to line A so we have a distinct reference.
        lineA.addElement(ElementType.CROTCHET.newInstance());
        var elementB = lineA.getElement(1);

        coordinator.selectLyric(elementA, VERSE_2);

        assertThat(coordinator.isLyricSelected(elementB, VERSE_2, LINE_0))
            .as("isLyricSelected with correct verse but a different element reference")
            .isFalse();
    }
}
