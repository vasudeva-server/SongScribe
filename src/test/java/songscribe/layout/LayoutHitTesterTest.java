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

package songscribe.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.crotchet;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;

/**
 * Covers {@link LayoutHitTester#findElementAtXSs}'s {@link ColumnSpan} handling directly, beyond
 * the single-span coverage in {@link LayoutResultTest}: the {@code HEAD} vs {@code FULL_INK}
 * bound difference on a column with a leading accidental, the gap/past-the-end {@code -1} cases,
 * and the grace-host overlap tie-break (refs #560).
 */
class LayoutHitTesterTest extends UnitTest {

    private static final double ELEMENT_X_SS = 10.0;

    /** Left extent for a column whose left edge sits exactly at its X (no accidental). */
    private static final double NO_LEFT_EXTENT_SS = 0.0;

    /** Left extent of a column with a leading accidental — negative, so the left edge sits before X. */
    private static final double ACCIDENTAL_LEFT_EXTENT_SS = -2.0;
    private static final double HEAD_RIGHT_EXTENT_SS = 1.0;

    /** Falls inside the accidental's ink (left edge to X) but before the head's own left bound. */
    private static final double X_IN_ACCIDENTAL_ONLY_SS = 9.0;

    private static final double FIRST_ELEMENT_X_SS = 10.0;
    private static final double FIRST_RIGHT_EXTENT_SS = 1.0;
    private static final double SECOND_ELEMENT_X_SS = 20.0;
    private static final double MOUSE_IN_GAP_SS = 15.0;
    private static final double MOUSE_PAST_RIGHT_EDGE_SS = 12.0;

    private static final double GRACE_X_SS = 10.0;
    private static final double GRACE_RIGHT_EXTENT_SS = 2.0;
    private static final double HOST_X_SS = 11.0;
    private static final double HOST_RIGHT_EXTENT_SS = 3.0;

    /** Inside both the grace note's and the host's column bounds. */
    private static final double MOUSE_IN_OVERLAP_SS = 11.5;

    // HEAD excludes a leading accidental's ink; FULL_INK encloses it. Both share the same right bound.
    @Test
    void testHeadExcludesAccidentalFullInkIncludesIt() {
        var element = crotchet();
        var line = lineWithElements(element);
        var column = ElementColumnTestHelper.columnAt(
            element, ELEMENT_X_SS, ACCIDENTAL_LEFT_EXTENT_SS, HEAD_RIGHT_EXTENT_SS);
        var result = LayoutResult.builder().putElementColumn(element, column).build();

        assertThat(result.findElementAtXSs(X_IN_ACCIDENTAL_ONLY_SS, line, ColumnSpan.HEAD)).isEqualTo(-1);
        assertThat(result.findElementAtXSs(X_IN_ACCIDENTAL_ONLY_SS, line, ColumnSpan.FULL_INK)).isEqualTo(0);

        // Same right bound for both spans.
        var rightEdgeSs = ELEMENT_X_SS + HEAD_RIGHT_EXTENT_SS;
        assertThat(result.findElementAtXSs(rightEdgeSs, line, ColumnSpan.HEAD)).isEqualTo(0);
        assertThat(result.findElementAtXSs(rightEdgeSs, line, ColumnSpan.FULL_INK)).isEqualTo(0);
    }

    // An X in the gap between two columns' bounds finds no element, for either span.
    @Test
    void testGapBetweenColumnsReturnsMinusOne() {
        var first = crotchet();
        var second = crotchet();
        var line = lineWithElements(first, second);
        var result = LayoutResult.builder()
            .putElementColumn(first, ElementColumnTestHelper.columnAt(first, FIRST_ELEMENT_X_SS, NO_LEFT_EXTENT_SS, FIRST_RIGHT_EXTENT_SS))
            .putElementColumn(second, ElementColumnTestHelper.columnAt(second, SECOND_ELEMENT_X_SS, NO_LEFT_EXTENT_SS, FIRST_RIGHT_EXTENT_SS))
            .build();

        assertThat(result.findElementAtXSs(MOUSE_IN_GAP_SS, line, ColumnSpan.HEAD)).isEqualTo(-1);
        assertThat(result.findElementAtXSs(MOUSE_IN_GAP_SS, line, ColumnSpan.FULL_INK)).isEqualTo(-1);
    }

    // An X past the last column's right edge finds no element.
    @Test
    void testPastLastColumnRightEdgeReturnsMinusOne() {
        var element = crotchet();
        var line = lineWithElements(element);
        var result = LayoutResult.builder()
            .putElementColumn(element, ElementColumnTestHelper.columnAt(element, FIRST_ELEMENT_X_SS, NO_LEFT_EXTENT_SS, FIRST_RIGHT_EXTENT_SS))
            .build();

        assertThat(result.findElementAtXSs(MOUSE_PAST_RIGHT_EDGE_SS, line, ColumnSpan.HEAD)).isEqualTo(-1);
    }

    // A grace note's column can overlap its host's when the host's flag is discounted (refs #560).
    // An X inside the overlap resolves to the grace note — the lower index, first-match-wins.
    @Test
    void testGraceHostOverlapResolvesToGraceNote() {
        var grace = crotchet();
        var host = crotchet();
        var line = lineWithElements(grace, host);
        var result = LayoutResult.builder()
            .putElementColumn(grace, ElementColumnTestHelper.columnAt(grace, GRACE_X_SS, NO_LEFT_EXTENT_SS, GRACE_RIGHT_EXTENT_SS))
            .putElementColumn(host, ElementColumnTestHelper.columnAt(host, HOST_X_SS, NO_LEFT_EXTENT_SS, HOST_RIGHT_EXTENT_SS))
            .build();

        assertThat(result.findElementAtXSs(MOUSE_IN_OVERLAP_SS, line, ColumnSpan.HEAD)).isEqualTo(0);
    }

    // Builds a fresh single-line song, prepending the given elements before the line's
    // auto-maintained FINAL_DOUBLE_BARLINE terminal so they occupy indices 0..n-1.
    private static Line lineWithElements(StaffElement... elements) {
        var song = new Song();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> {
            for (var i = 0; i < elements.length; i++) {
                line.addElement(i, elements[i]);
            }
        });
        return line;
    }
}
