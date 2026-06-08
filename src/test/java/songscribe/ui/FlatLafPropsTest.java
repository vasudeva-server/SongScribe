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

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlatLafPropsTest extends UnitTest {

    // --- Row 21: get throws RuntimeError when key is absent from UIManager ---

    @Test
    void testGetThrowsWhenKeyIsAbsent() {
        // RuntimeError.exit() is redirected by UnitTest to throw AssertionError;
        // the missing-key branch must trigger that path.
        assertThatThrownBy(() -> FlatLafProps.get("SongScribe.nonexistent.key"))
            .isInstanceOf(AssertionError.class);
    }

    // --- Row 22: get returns typed value when key is present ---

    @Test
    void testGetReturnsTypedValueWhenKeyPresent() {
        // installFlatLafDefaults() is called by UnitTest.suppressDialogs(); no extra setup needed.
        // DIALOG_COMPONENT_VERTICAL_GAP is defined as 5 in FlatLaf.properties.
        final int expectedGap = 5;
        int gap = FlatLafProps.get(FlatLafKeys.DIALOG_COMPONENT_VERTICAL_GAP);
        assertThat(gap).isEqualTo(expectedGap);
    }
}
