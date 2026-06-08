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

class ModeTest extends UnitTest {

    // --- Row 19: isAdjustmentMode returns true for ADJUSTMENT and VERTICAL_ADJUSTMENT ---

    @Test
    void testIsAdjustmentModeReturnsTrueForAdjustment() {
        assertThat(Mode.ADJUSTMENT.isAdjustmentMode()).isTrue();
    }

    @Test
    void testIsAdjustmentModeReturnsTrueForVerticalAdjustment() {
        assertThat(Mode.VERTICAL_ADJUSTMENT.isAdjustmentMode()).isTrue();
    }

    // --- Row 20: isAdjustmentMode returns false for SELECT and EDIT ---

    @Test
    void testIsAdjustmentModeReturnsFalseForSelect() {
        assertThat(Mode.SELECT.isAdjustmentMode()).isFalse();
    }

    @Test
    void testIsAdjustmentModeReturnsFalseForEdit() {
        assertThat(Mode.EDIT.isAdjustmentMode()).isFalse();
    }
}
