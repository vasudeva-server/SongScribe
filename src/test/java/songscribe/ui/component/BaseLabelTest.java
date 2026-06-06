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

import javax.swing.JList;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

/**
 * Unit tests for {@link BaseLabel} covering:
 * <ul>
 *   <li>Row 5 — Constructor: index==-1 uses list background; isSelected==true/false paths</li>
 * </ul>
 */
class BaseLabelTest extends UnitTest {

    private static JList<String> list;

    @BeforeAll
    static void setUpList() throws Exception {
        installFlatLafDefaults();
        list = new JList<>();
    }

    // -----------------------------------------------------------------------
    // Row 5: Constructor background/foreground selection logic
    // -----------------------------------------------------------------------

    @Test
    void testIndexMinusOneUsesListBackground() {
        var label = new BaseLabel("text", list, -1, false);
        assertThat(label.getBackground()).isEqualTo(list.getBackground());
    }

    @Test
    void testIndexMinusOneWithIsSelectedTrueStillUsesListBackground() {
        // When index == -1, isSelected is irrelevant — list background is always used
        var label = new BaseLabel("text", list, -1, true);
        assertThat(label.getBackground()).isEqualTo(list.getBackground());
    }

    @Test
    void testIsSelectedTrueUsesSelectionBackground() {
        var label = new BaseLabel("text", list, 0, true);
        assertThat(label.getBackground()).isEqualTo(list.getSelectionBackground());
    }

    @Test
    void testIsSelectedFalseUsesListBackground() {
        var label = new BaseLabel("text", list, 0, false);
        assertThat(label.getBackground()).isEqualTo(list.getBackground());
    }

    @Test
    void testIsSelectedTrueUsesSelectionForeground() {
        var label = new BaseLabel("text", list, 0, true);
        assertThat(label.getForeground()).isEqualTo(list.getSelectionForeground());
    }

    @Test
    void testIsSelectedFalseUsesListForeground() {
        var label = new BaseLabel("text", list, 0, false);
        assertThat(label.getForeground()).isEqualTo(list.getForeground());
    }

    @Test
    void testIndexMinusOneWithIsSelectedTrueUsesSelectionForeground() {
        // index==-1 short-circuits only the background; foreground still follows isSelected
        var label = new BaseLabel("text", list, -1, true);
        assertThat(label.getForeground()).isEqualTo(list.getSelectionForeground());
    }
}
