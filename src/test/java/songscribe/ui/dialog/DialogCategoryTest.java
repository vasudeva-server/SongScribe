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
package songscribe.ui.dialog;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DialogCategory#isBlocking()} covering all three enum constants directly.
 */
class DialogCategoryTest extends UnitTest {

    @Test
    void testExclusiveIsBlocking() {
        assertThat(DialogCategory.EXCLUSIVE.isBlocking())
            .as("EXCLUSIVE must be blocking")
            .isTrue();
    }

    @Test
    void testOperationalIsBlocking() {
        assertThat(DialogCategory.OPERATIONAL.isBlocking())
            .as("OPERATIONAL must be blocking")
            .isTrue();
    }

    @Test
    void testInformationalIsNotBlocking() {
        assertThat(DialogCategory.INFORMATIONAL.isBlocking())
            .as("INFORMATIONAL must not be blocking")
            .isFalse();
    }
}
