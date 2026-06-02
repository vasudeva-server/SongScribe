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

package songscribe.ui.action;

import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;

import static org.assertj.core.api.Assertions.assertThat;

class InsertLineActionTest extends MainFrameMockTest {

    // Row 7: getActionCommand branches — ADD→"add-line", 0→"insert-line-before", 1→"insert-line-after"

    @Test
    void testAddLineActionCommandIsAddLine() {
        var action = InsertLineAction.createAddLineAction(mainFrame());
        assertThat(action.getActionCommand()).isEqualTo("add-line");
    }

    @Test
    void testInsertLineBeforeActionCommandIsInsertLineBefore() {
        var action = InsertLineAction.createInsertLineBeforeAction(mainFrame());
        assertThat(action.getActionCommand()).isEqualTo("insert-line-before");
    }

    @Test
    void testInsertLineAfterActionCommandIsInsertLineAfter() {
        var action = InsertLineAction.createInsertLineAfterAction(mainFrame());
        assertThat(action.getActionCommand()).isEqualTo("insert-line-after");
    }
}
