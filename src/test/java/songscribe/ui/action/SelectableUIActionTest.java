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

import module java.desktop;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.MainFrameMockTest;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class SelectableUIActionTest extends MainFrameMockTest {

    private MockedStatic<Prefs> prefsMock;

    @BeforeEach
    void setUpPrefs() {
        prefsMock = mockStatic(Prefs.class);
    }

    @AfterEach
    void tearDownPrefs() {
        prefsMock.close();
    }

    // -- isSelected() defaults false on construction --

    @Test
    void testIsSelectedReturnsFalseOnConstruction() {
        var action = new SelectableUIAction(mainFrame(), "Test", "test-cmd") {
            @Override
            protected void performAction(ActionEvent e) {}
        };

        assertThat(action.isSelected()).isFalse();
    }

    // Row 43: reset() sets selected to false

    @Test
    void testResetSetsSelectedToFalse() {
        var action = new SelectableUIAction(mainFrame(), "Test", "test-cmd") {
            @Override
            protected void performAction(ActionEvent e) {}
        };

        action.setSelected(true);
        action.reset();

        assertThat(action.isSelected()).isFalse();
    }

    // Row 44: prefsKey ctor calls setSelected(Prefs.getBoolean(prefsKey)) when key non-null

    @Test
    void testPrefsKeyCtorSetsSelectedFromPrefs() {
        prefsMock.when(() -> Prefs.getBoolean(PrefsKey.LOOP_PLAYBACK)).thenReturn(true);

        var action = new SelectableUIAction(mainFrame(), "Test", null, 0, "test-cmd", null, PrefsKey.LOOP_PLAYBACK) {
            @Override
            protected void performAction(ActionEvent e) {}
        };

        assertThat(action.isSelected()).isTrue();
    }
}
