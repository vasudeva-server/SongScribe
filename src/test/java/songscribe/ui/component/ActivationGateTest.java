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
 *       {@code deactivate()} hides it and cancels the debounce</li>
 *   <li>Row 49 — {@code appRaisedToForeground()} retriggers the cmd+Tab debounce</li>
 * </ul>
 * These cases cover the wiring only — that the gate arms and cancels the debounce it owns.
 * Whether a trigger actually extends the deadline, and whether the action eventually runs, is
 * {@link songscribe.util.Debounce}'s own contract and is proved in {@code DebounceTest}; from
 * out here {@code isArmed()} cannot tell an extended window from the remainder of an old one.
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
        // Always cancel the debounce to avoid background threads leaking between tests.
        var debounce = ActivationGate.cmdTabDebounce;

        if (debounce != null) {
            debounce.cancel();
        }

        frame.dispose();
        // Reset static state so each test starts clean.
        ActivationGate.glassPane = null;
        ActivationGate.cmdTabDebounce = null;
    }

    // -----------------------------------------------------------------------
    // Row 48: activate() makes glass pane visible; deactivate() hides it and
    // cancels the debounce
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
    void testDeactivateCancelsDebounce() {
        // Arm the debounce by calling appRaisedToForeground, then deactivate.
        ActivationGate.appRaisedToForeground();
        ActivationGate.deactivate();

        var debounce = ActivationGate.cmdTabDebounce;
        assertThat(debounce).isNotNull();

        if (debounce == null) {
            return; // unreachable — satisfies NullAway
        }

        assertThat(debounce.isArmed()).isFalse();
    }

    // -----------------------------------------------------------------------
    // Row 49: appRaisedToForeground() retriggers the cmd+Tab debounce
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("NullAway")
    void testAppRaisedToForegroundArmsDebounce() {
        var debounce = ActivationGate.cmdTabDebounce;
        assertThat(debounce).isNotNull();

        if (debounce == null) {
            return; // unreachable — satisfies NullAway
        }

        assertThat(debounce.isArmed()).isFalse();

        ActivationGate.appRaisedToForeground();

        assertThat(debounce.isArmed()).isTrue();
    }

    @Test
    @SuppressWarnings("NullAway")
    void testAppRaisedToForegroundKeepsDebounceArmedWhenAlreadyArmed() {
        // Named for what this can actually prove: a second trigger must not disarm the debounce.
        // That it also pushes the deadline out is Debounce's contract, tested there — isArmed()
        // reads the same either way.
        ActivationGate.appRaisedToForeground();
        ActivationGate.appRaisedToForeground();

        var debounce = ActivationGate.cmdTabDebounce;
        assertThat(debounce).isNotNull();

        if (debounce == null) {
            return; // unreachable — satisfies NullAway
        }

        assertThat(debounce.isArmed()).isTrue();
    }
}
