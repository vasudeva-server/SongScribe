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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

/**
 * Tests for {@link ElementLocation} — constructor validation, matches(), and
 * boundary construction.
 */
class ElementLocationTest extends UnitTest {

    // Indices used for the "valid" location in matches tests
    private static final int LINE_INDEX = 3;
    private static final int ELEMENT_INDEX = 7;

    // A line index that differs from LINE_INDEX — used to test partial mismatch
    private static final int OTHER_LINE_INDEX = 5;

    // An element index that differs from ELEMENT_INDEX — used to test partial mismatch
    private static final int OTHER_ELEMENT_INDEX = 2;

    // Negative values used to exercise the guard in the compact constructor
    private static final int NEGATIVE_LINE_INDEX = -1;
    private static final int NEGATIVE_ELEMENT_INDEX = -1;

    // -----------------------------------------------------------------------
    // Row 5: Constructor rejects negative line index and negative element index
    // -----------------------------------------------------------------------

    @Test
    void testConstructorRejectsNegativeLineIndex() {
        assertThatThrownBy(() -> new ElementLocation(NEGATIVE_LINE_INDEX, ELEMENT_INDEX))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testConstructorRejectsNegativeElementIndex() {
        assertThatThrownBy(() -> new ElementLocation(LINE_INDEX, NEGATIVE_ELEMENT_INDEX))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // -----------------------------------------------------------------------
    // Row 6: matches() returns true iff both indices equal
    // -----------------------------------------------------------------------

    @Test
    void testMatchesReturnsTrueWhenBothIndicesEqual() {
        var location = new ElementLocation(LINE_INDEX, ELEMENT_INDEX);

        assertThat(location.matches(LINE_INDEX, ELEMENT_INDEX)).isTrue();
    }

    @Test
    void testMatchesReturnsFalseWhenLineDiffers() {
        var location = new ElementLocation(LINE_INDEX, ELEMENT_INDEX);

        assertThat(location.matches(OTHER_LINE_INDEX, ELEMENT_INDEX)).isFalse();
    }

    @Test
    void testMatchesReturnsFalseWhenElementDiffers() {
        var location = new ElementLocation(LINE_INDEX, ELEMENT_INDEX);

        assertThat(location.matches(LINE_INDEX, OTHER_ELEMENT_INDEX)).isFalse();
    }

    // -----------------------------------------------------------------------
    // Row 7: Zero indices are valid — construction with (0, 0) must succeed
    // -----------------------------------------------------------------------

    @Test
    void testConstructorAcceptsZeroIndices() {
        var location = new ElementLocation(0, 0);

        assertThat(location.lineIndex()).isZero();
        assertThat(location.elementIndex()).isZero();
    }
}
