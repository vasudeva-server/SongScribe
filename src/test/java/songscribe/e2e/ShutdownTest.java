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
package songscribe.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import module java.desktop;

import java.util.concurrent.atomic.AtomicBoolean;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.lifecycle.Shutdown;
import songscribe.ui.OptionDialogs;
import songscribe.ui.action.Actions;
import songscribe.ui.action.CloseWindowAction;
import songscribe.ui.component.MainFrame;

/**
 * Verifies that every user-invoked quit path now funnels through {@link Shutdown#now()}.
 * <p>
 * Tests run with dialog suppression enabled so the registered "save-dirty-doc"
 * confirm task returns false (via {@link JOptionPane#CLOSED_OPTION}) on a dirty
 * doc, vetoing the shutdown and keeping the JVM alive. A sentinel confirm task
 * is registered once per class to record that the shutdown sequence was actually
 * triggered — proving each entry point invokes the registry rather than silently
 * no-op'ing or going around it. The sentinel always vetoes as a second line of
 * defense against {@code System.exit}.
 */
class ShutdownTest extends E2ETest {

    private static final AtomicBoolean SENTINEL_FIRED = new AtomicBoolean(false);
    private static boolean sentinelRegistered = false;

    @BeforeAll
    void registerSentinel() {
        OptionDialogs.setSuppressDialogs(true);

        // Registry is a process-global singleton; register the sentinel exactly once.
        if (!sentinelRegistered) {
            Shutdown.registerConfirmTask("e2e-sentinel-veto", () -> {
                SENTINEL_FIRED.set(true);
                return false; // veto — never let the JVM exit
            });
            sentinelRegistered = true;
        }
    }

    @AfterAll
    void unsuppressDialogs() {
        OptionDialogs.setSuppressDialogs(false);
    }

    @BeforeEach
    void resetState() {
        SENTINEL_FIRED.set(false);
        resetSong();
    }

    /**
     * E0: CloseWindowAction — a distinct quit entry point built fresh in the File menu
     * rather than exposed as an {@code Actions} singleton — also funnels through
     * {@link Shutdown#now()}. On a clean doc the save-dirty-doc confirm passes and the
     * sentinel fires (and vetoes), proving the wiring matches {@code QuitAction}.
     */
    @Test
    void closeWindowActionTriggersShutdown() {
        GuiActionRunner.execute(() -> {
            var closeAction = CloseWindowAction.createAction(MainFrame.getInstance());
            closeAction.actionPerformed(
                new ActionEvent(closeAction, ActionEvent.ACTION_PERFORMED, ""));
        });

        assertThat(SENTINEL_FIRED).isTrue();
        assertThat(MainFrame.getInstance().isEnabled()).isTrue(); // veto path: frame stays enabled
    }

    /** E1: QuitAction now triggers Shutdown.now() (was a no-op before this change). */
    @Test
    void quitActionTriggersShutdown() {
        GuiActionRunner.execute(() -> Actions.QUIT_ACTION.actionPerformed(
            new ActionEvent(Actions.QUIT_ACTION, ActionEvent.ACTION_PERFORMED, "")));

        assertThat(SENTINEL_FIRED).isTrue();
        assertThat(MainFrame.getInstance().isEnabled()).isTrue(); // veto path: frame stays enabled
    }

    /** E2: Window-close with Save-As-cancel (or any save dialog dismissal) keeps the app alive. */
    @Test
    void windowCloseOnDirtyDocCancelKeepsAppAlive() {
        GuiActionRunner.execute(() -> {
            song().setModified(true);
            var frame = MainFrame.getInstance();
            frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
        });

        // save-dirty-doc returns false on suppressed dialog (CLOSED_OPTION),
        // so the sentinel never runs and the app remains alive.
        assertThat(SENTINEL_FIRED).isFalse();
        assertThat(MainFrame.getInstance().isEnabled()).isTrue();
        assertThat(MainFrame.getInstance().isDisplayable()).isTrue();
    }

    /**
     * E3: Window-close on a clean doc progresses past the save-dirty-doc confirm task.
     * <p>
     * On a clean doc, save-dirty-doc returns true immediately (no dialog). The sentinel
     * then fires and vetoes, so the JVM stays alive — but its firing proves the shutdown
     * sequence ran end-to-end on the clean-doc path without bailing out at the save step.
     */
    @Test
    void windowCloseOnCleanDocProgressesPastSaveCheck() {
        GuiActionRunner.execute(() -> {
            song().setModified(false);
            var frame = MainFrame.getInstance();
            frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
        });

        assertThat(SENTINEL_FIRED).isTrue();
        assertThat(MainFrame.getInstance().isEnabled()).isTrue();
    }
}
