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

package songscribe.ui.component.toolbar;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.ui.action.Actions;

/**
 * Verifies that constructing a {@link DurationToolbar} selects
 * {@link Actions#QUARTER_NOTE_ACTION} in {@link Actions#DURATION_ACTION_GROUP}.
 *
 * <p>This covers the construction-time default-selection path, which is distinct
 * from the post-load reset path covered by {@code ActionsResetOnDocumentLoadTest}
 * (that test fires a {@code DocumentDidLoadNotification}, not the constructor).
 */
class DurationToolbarTest extends MainFrameMockTest {

    @Test
    void testConstructorSelectsQuarterNoteAsDefault() {
        // Put the group in a different state to ensure the constructor actually sets it.
        Actions.DURATION_ACTION_GROUP.setSelected(Actions.HALF_NOTE_ACTION, true);

        new DurationToolbar();

        assertThat(Actions.DURATION_ACTION_GROUP.getSelected())
            .as("DURATION_ACTION_GROUP selected action after DurationToolbar construction")
            .isSameAs(Actions.QUARTER_NOTE_ACTION);
    }
}
