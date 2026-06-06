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

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

/**
 * Unit tests for {@link ComponentNames} covering:
 * <ul>
 *   <li>Row 38 — {@code line(index)}: concatenates {@code LINE_PREFIX} + index correctly</li>
 * </ul>
 */
class ComponentNamesTest extends UnitTest {

    // -----------------------------------------------------------------------
    // Row 38: line(index) = LINE_PREFIX + index
    // -----------------------------------------------------------------------

    @Test
    void testLineZeroReturnsPrefixPlusZero() {
        assertThat(ComponentNames.line(0)).isEqualTo(ComponentNames.LINE_PREFIX + "0");
    }

    @Test
    void testLineThreeReturnsPrefixPlusThree() {
        assertThat(ComponentNames.line(3)).isEqualTo(ComponentNames.LINE_PREFIX + "3");
    }

    @Test
    void testLineConcatenatesCorrectFormat() {
        // Guards against accidental changes to the separator or prefix
        assertThat(ComponentNames.line(0)).isEqualTo("line-0");
        assertThat(ComponentNames.line(3)).isEqualTo("line-3");
    }
}
