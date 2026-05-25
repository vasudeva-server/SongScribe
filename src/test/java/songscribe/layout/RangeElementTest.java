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

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.Tie;

/**
 * Tests for {@link songscribe.dom.RangeElement#getElementCount()}.
 */
class RangeElementTest extends UnitTest {

    // Number of notes placed between anchor and end (inclusive) in the normal case
    private static final int ELEMENT_COUNT_THREE = 3;

    // -----------------------------------------------------------------------
    // Row 32: getElementCount() — end − start + 1, or 0 when undefined
    // -----------------------------------------------------------------------

    // Normal case: anchor at index 0, end at index 2 → count = 3
    @Test
    void testGetElementCountReturnsEndMinusStartPlusOne() {
        var song = new Song();
        var line = song.getLine(0);
        var anchor = new StaffElement(ElementType.QUAVER);
        var middle = new StaffElement(ElementType.QUAVER);
        var end = new StaffElement(ElementType.QUAVER);

        song.withoutMutationTracking(() -> {
            line.addElement(anchor);
            line.addElement(middle);
            line.addElement(end);
        });

        var tie = new Tie(anchor, end);

        assertThat(tie.getElementCount()).isEqualTo(ELEMENT_COUNT_THREE);
    }

    // Null anchor → 0
    @Test
    void testGetElementCountReturnsZeroWhenAnchorIsNull() {
        // Construct with real elements then null out the anchor to exercise that branch
        var anchor = new StaffElement(ElementType.QUAVER);
        var end = new StaffElement(ElementType.QUAVER);
        var tie = new Tie(anchor, end);
        tie.setAnchorElement(null);

        assertThat(tie.getElementCount()).isZero();
    }

    // Null end → 0
    @Test
    void testGetElementCountReturnsZeroWhenEndIsNull() {
        // Construct with real elements then null out the end to exercise that branch
        var anchor = new StaffElement(ElementType.QUAVER);
        var end = new StaffElement(ElementType.QUAVER);
        var tie = new Tie(anchor, end);
        tie.setEndElement(null);

        assertThat(tie.getElementCount()).isZero();
    }

}
