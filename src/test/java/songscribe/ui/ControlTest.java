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

import songscribe.Strings;
import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;

class ControlTest extends UnitTest {

    // --- Row 17: MOUSE.getDescription() returns string for ACTION_CONTROL_MOUSE ---

    @Test
    void testMouseGetDescriptionMatchesStringsKey() {
        var description = Control.MOUSE.getDescription();
        assertThat(description).isEqualTo(Strings.get(Strings.ACTION_CONTROL_MOUSE));
    }

    // --- Row 18: KEYBOARD.getDescription() returns string for ACTION_CONTROL_KEYBOARD ---

    @Test
    void testKeyboardGetDescriptionMatchesStringsKey() {
        var description = Control.KEYBOARD.getDescription();
        assertThat(description).isEqualTo(Strings.get(Strings.ACTION_CONTROL_KEYBOARD));
    }
}
