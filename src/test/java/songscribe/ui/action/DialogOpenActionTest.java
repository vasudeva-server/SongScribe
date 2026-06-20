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

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.ui.component.MainFrame;
import songscribe.ui.dialog.BaseDialog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;

class DialogOpenActionTest extends MainFrameMockTest {

    // Minimal concrete BaseDialog for testing the happy-path getDialog() caching.
    static class StubDialog extends BaseDialog {

        public StubDialog(MainFrame mainFrame) {
            super(mainFrame, "Stub");
        }
    }

    // Row 80: Constructor derives actionCommand via toKebabCase(name)

    @Test
    void testConstructorDerivesActionCommandFromName() {
        var action = new DialogOpenAction<>(mainFrame(), "Song Settings", frame -> new StubDialog(frame));
        assertThat(action.getActionCommand()).isEqualTo("song-settings");
    }

    // Row 81: getDialog lazy-initializes on first call and caches the same instance

    @Test
    void testGetDialogLazyInitializesAndCachesInstance() {
        var action = new DialogOpenAction<>(mainFrame(), "Stub", StubDialog::new);

        var first = action.getDialog();
        var second = action.getDialog();

        assertAll(
            () -> assertThat(first).isNotNull(),
            () -> assertThat(second).isSameAs(first)
        );
    }

    // Decision 10A: getDialog() builds the dialog via the INJECTED MainFrame (not getInstance()),
    // and the factory receives exactly the frame that was passed to DialogOpenAction's constructor.

    @Test
    void testGetDialogBuildsViaInjectedMainFrameNotGetInstance() {
        // Capture the MainFrame the factory receives — it must be the injected frame,
        // not whatever MainFrame.getInstance() might return.
        var capturedFrames = new ArrayList<MainFrame>();
        var injectedFrame = mainFrame();

        var action = new DialogOpenAction<BaseDialog>(injectedFrame, "Stub", frame -> {
            capturedFrames.add(frame);
            return new StubDialog(frame);
        });

        var dialog = action.getDialog();

        assertAll(
            () -> assertThat(capturedFrames).as("factory called exactly once").hasSize(1),
            () -> assertThat(capturedFrames.get(0))
                .as("factory receives the injected MainFrame, not a different frame")
                .isSameAs(injectedFrame),
            () -> assertThat(dialog).as("dialog is non-null").isNotNull()
        );
    }
}
