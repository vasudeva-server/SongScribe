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

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

class TupletToggleInfoTest extends UnitTest {

    @Test
    void testCoversExistingTrueWithNullExistingThrows() {
        // compact-constructor guard: coversExisting=true requires a non-null existing tuplet
        assertThatIllegalArgumentException().isThrownBy(
            () -> new TupletToggleInfo(true, null, true)
        );
    }

    @Test
    void testCoversExistingFalseWithNullExistingIsValid() {
        // the normal "no tuplet" case must not throw
        assertThatNoException().isThrownBy(
            () -> new TupletToggleInfo(true, null, false)
        );
    }
}
