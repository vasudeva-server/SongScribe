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

import javax.swing.JFrame;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.RequiresDisplay;
import songscribe.UnitTest;

/**
 * Unit tests for {@link ActivationGate} covering:
 * <ul>
 *   <li>Row 48 — {@code activate()} makes glass pane visible;
 *       {@code deactivate()} hides it and stops the timer</li>
 *   <li>Row 49 — {@code appRaisedToForeground()} restarts the cmd+Tab timer</li>
 * </ul>
 */
@RequiresDisplay
class ActivationGateTest extends UnitTest {

    private JFrame frame;

    @BeforeEach
    void setUp() {
        // A hidden JFrame; install() attaches the glass pane to it.
        frame = new JFrame();
        ActivationGate.install(frame);
        // After install the glass pane is not yet visible.
    }

    @AfterEach
    void tearDown() {
        // Always stop the timer to avoid background threads leaking between tests.
        var timer = ActivationGate.cmdTabTimer;

        if (timer != null) {
            timer.stop();
        }

        frame.dispose();
        // Reset static state so each test starts clean.
        ActivationGate.glassPane = null;
        ActivationGate.cmdTabTimer = null;
    }

    // -----------------------------------------------------------------------
    // Row 48: activate() makes glass pane visible; deactivate() hides it and
    // stops the timer
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("NullAway")
    void testActivateMakesGlassPaneVisible() {
        ActivationGate.activate();

        var pane = ActivationGate.glassPane;
        assertThat(pane).isNotNull();

        if (pane == null) {
            return; // unreachable — satisfies NullAway
        }

        assertThat(pane.isVisible()).isTrue();
    }

    @Test
    @SuppressWarnings("NullAway")
    void testDeactivateHidesGlassPane() {
        ActivationGate.activate();
        ActivationGate.deactivate();

        var pane = ActivationGate.glassPane;
        assertThat(pane).isNotNull();

        if (pane == null) {
            return; // unreachable — satisfies NullAway
        }

        assertThat(pane.isVisible()).isFalse();
    }

    @Test
    @SuppressWarnings("NullAway")
    void testDeactivateStopsTimer() {
        // Start the timer by calling appRaisedToForeground, then deactivate.
        ActivationGate.appRaisedToForeground();
        ActivationGate.deactivate();

        var timer = ActivationGate.cmdTabTimer;
        assertThat(timer).isNotNull();

        if (timer == null) {
            return; // unreachable — satisfies NullAway
        }

        assertThat(timer.isRunning()).isFalse();
    }

    // -----------------------------------------------------------------------
    // Row 49: appRaisedToForeground() restarts the cmd+Tab timer
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("NullAway")
    void testAppRaisedToForegroundStartsTimer() {
        var timer = ActivationGate.cmdTabTimer;
        assertThat(timer).isNotNull();

        if (timer == null) {
            return; // unreachable — satisfies NullAway
        }

        assertThat(timer.isRunning()).isFalse();

        ActivationGate.appRaisedToForeground();

        assertThat(timer.isRunning()).isTrue();
    }

    @Test
    @SuppressWarnings("NullAway")
    void testAppRaisedToForegroundRestartsAlreadyRunningTimer() {
        // Start once, then call again — timer must still be running (restarted).
        ActivationGate.appRaisedToForeground();
        ActivationGate.appRaisedToForeground();

        var timer = ActivationGate.cmdTabTimer;
        assertThat(timer).isNotNull();

        if (timer == null) {
            return; // unreachable — satisfies NullAway
        }

        assertThat(timer.isRunning()).isTrue();
    }
}
