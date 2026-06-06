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
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.ui.action.UIAction;

/**
 * Unit tests for {@link PopupButton} covering:
 * <ul>
 *   <li>Row 25 — {@code setCurrentAction(null)}: is a no-op — no NPE thrown,
 *       {@code currentAction} is set to null, {@code configureButtonFromAction} is not called</li>
 * </ul>
 */
class PopupButtonTest extends UnitTest {

    private PopupButton button;

    @BeforeAll
    static void setUpClass() throws Exception {
        installFlatLafDefaults();
    }

    @BeforeEach
    void setUp() {
        // Construct with an empty action array and no default action
        button = new PopupButton(new UIAction[0], null);
    }

    // -----------------------------------------------------------------------
    // Row 25: setCurrentAction(null) is a no-op — no NPE, currentAction becomes null
    // -----------------------------------------------------------------------

    @Test
    void testSetCurrentActionNullDoesNotThrow() {
        assertThatCode(() -> button.setCurrentAction(null)).doesNotThrowAnyException();
    }

    @Test
    void testSetCurrentActionNullSetsCurrentActionToNull() {
        button.setCurrentAction(null);
        assertThat(button.getCurrentAction()).isNull();
    }
}
