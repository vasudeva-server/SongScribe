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
import songscribe.ui.dialog.BaseDialog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class DialogOpenActionTest extends MainFrameMockTest {

    // Minimal concrete BaseDialog for testing the happy-path getDialog() caching
    static class StubDialog extends BaseDialog {
        public StubDialog() {
            super("Stub");
        }
    }

    // Row 80: Constructor derives actionCommand via toKebabCase(name)

    @Test
    void testConstructorDerivesActionCommandFromName() {
        var action = new DialogOpenAction<>(mainFrame(), "Song Settings", BaseDialog.class);
        assertThat(action.getActionCommand()).isEqualTo("song-settings");
    }

    // Row 81: getDialog lazy-initializes on first call and caches the same instance

    @Test
    void testGetDialogLazyInitializesAndCachesInstance() {
        var action = new DialogOpenAction<>(mainFrame(), "Stub", StubDialog.class);

        var first = action.getDialog();
        var second = action.getDialog();

        assertAll(
            () -> assertThat(first).isNotNull(),
            () -> assertThat(second).isSameAs(first)
        );
    }

    @Test
    void testGetDialogReturnsNullWhenDialogClassHasNoNoArgConstructor() {
        // BaseDialog has no no-arg constructor; getConstructor() throws NoSuchMethodException
        var action = new DialogOpenAction<>(mainFrame(), "Test", BaseDialog.class);
        assertThat(action.getDialog()).isNull();
    }
}
