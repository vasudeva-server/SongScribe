/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package songscribe.ui.component;

import static org.assertj.core.api.Assertions.assertThat;

import javax.swing.JTextField;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

/**
 * Unit tests for {@link SelectionHidingCaret} covering:
 * <ul>
 *   <li>Row 76 — {@code isSelectionActive}: null component → false; dot==mark → false; dot!=mark → true</li>
 * </ul>
 */
class SelectionHidingCaretTest extends UnitTest {

    // -----------------------------------------------------------------------
    // Row 76: isSelectionActive — three branches
    // -----------------------------------------------------------------------

    @Test
    void testIsSelectionActiveReturnsFalseWhenComponentIsNull() {
        // A freshly constructed caret has no component installed yet
        var caret = new SelectionHidingCaret();
        assertThat(caret.isSelectionActive()).isFalse();
    }

    @Test
    void testIsSelectionActiveReturnsFalseWhenDotEqualsMarkNoSelection() {
        var field = new JTextField("hello");
        var caret = new SelectionHidingCaret();
        field.setCaret(caret);

        // Clear any selection so dot == mark
        field.setCaretPosition(2);
        field.moveCaretPosition(2);

        assertThat(caret.getDot()).isEqualTo(caret.getMark());
        assertThat(caret.isSelectionActive()).isFalse();
    }

    @Test
    void testIsSelectionActiveReturnsTrueWhenDotDiffersFromMark() {
        var field = new JTextField("hello");
        var caret = new SelectionHidingCaret();
        field.setCaret(caret);

        // Select characters 1..3 so dot != mark
        field.setCaretPosition(1);
        field.moveCaretPosition(3);

        assertThat(caret.getDot()).isNotEqualTo(caret.getMark());
        assertThat(caret.isSelectionActive()).isTrue();
    }
}
