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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.ui.action.Actions;

/**
 * Unit tests for {@link StaffAnnotationPopupButton} covering the enabled state derived from
 * {@link Actions#STAFF_ANNOTATION_ACTIONS} — disabled at construction when every action is
 * disabled, and enabled again when an action comes back to life afterwards.
 * <p>
 * {@code PopupMenuButtonTest} covers that derivation against mock actions whose listeners it
 * fires by hand. These tests are the counterpart that proves the wiring end to end: a real
 * {@code UIAction.setEnabled} call really does produce the event the button is listening for.
 * That is why they are worth keeping even though the logic itself lives in the base class.
 */
class StaffAnnotationPopupButtonTest extends MainFrameMockTest {

    private StaffAnnotationPopupButton button;

    @BeforeEach
    void setUp() {
        // Establish a known baseline. Nothing has to be restored afterwards: MainFrameMockTest
        // calls Actions.initialize() before every test, which replaces these actions outright.
        for (var action : Actions.STAFF_ANNOTATION_ACTIONS) {
            action.setEnabled(false);
        }

        button = new StaffAnnotationPopupButton();
    }

    // -----------------------------------------------------------------------
    // Enabled state derived from Actions.STAFF_ANNOTATION_ACTIONS
    // -----------------------------------------------------------------------

    @Test
    void testButtonIsDisabledWhenAllStaffAnnotationActionsAreDisabled() {
        // All actions are disabled (set up in @BeforeEach)
        assertThat(button.isEnabled()).isFalse();
    }

    @Test
    void testButtonBecomesEnabledWhenAStaffAnnotationActionIsEnabled() {
        // Enabling an action after construction must flip the button via the
        // property-change listener registered by BasePopupButton.
        Actions.STAFF_ANNOTATION_ACTIONS.getFirst().setEnabled(true);
        assertThat(button.isEnabled()).isTrue();
    }
}
