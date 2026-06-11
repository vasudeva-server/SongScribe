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

    // --- Row 22: get returns typed value when key is present ---

    @Test
    void testGetReturnsTypedValueWhenKeyPresent() {
        // installFlatLafDefaults() is called by UnitTest.suppressDialogs(); no extra setup needed.
        // DIALOG_COMPONENT_VERTICAL_GAP is defined as 5 in FlatLaf.properties.
        final int expectedGap = 5;
        var gap = FlatLafProps.getInt(FlatLafKey.DIALOG_COMPONENT_VERTICAL_GAP);
        assertThat(gap).isEqualTo(expectedGap);
    }

    // --- Row 23: get throws when the requested type does not match the property's type ---

    @Test
    void testGetThrowsWhenTypeMismatch() {
        // RuntimeError.exit() is redirected by UnitTest to throw AssertionError;
        // the wrong-type branch must trigger that path. DIALOG_COMPONENT_VERTICAL_GAP
        // is an Integer, so requesting it as a String must fail.
        assertThatThrownBy(() -> FlatLafProps.getString(FlatLafKey.DIALOG_COMPONENT_VERTICAL_GAP))
            .isInstanceOf(AssertionError.class);
    }
}
