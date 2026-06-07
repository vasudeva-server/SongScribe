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

import java.awt.event.ActionEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link StandardDialog} OK/Cancel button lifecycle logic.
 */
class StandardDialogTest extends MainFrameMockTest {

    @BeforeEach
    void setUp() {
        BaseDialogTestHelper.configureMockFrame(mainFrame());
        BaseDialog.resetVisibleBlockingDialogCount();
        BaseDialog.resetSavedGeometry();
    }

    // -- OK button: isValidData false --

    @Test
    void testOkClickWhenIsValidDataFalseDoesNotCallSetData() {
        var dialog = new TrackingDialog(false);
        fireOkAction(dialog);
        assertThat(dialog.setDataCallCount).as("setData not called when isValidData returns false").isZero();
    }

    @Test
    void testOkClickWhenIsValidDataFalseDoesNotCloseDialog() {
        var dialog = new TrackingDialog(false);
        fireOkAction(dialog);
        assertThat(dialog.closeCallCount).as("setVisible(false) not called when isValidData returns false").isZero();
    }

    // -- OK button: isValidData true --

    @Test
    void testOkClickWhenIsValidDataTrueCallsSetData() {
        var dialog = new TrackingDialog(true);
        fireOkAction(dialog);
        assertThat(dialog.setDataCallCount).as("setData called when isValidData returns true").isEqualTo(1);
    }

    @Test
    void testOkClickWhenIsValidDataTrueClosesDialog() {
        var dialog = new TrackingDialog(true);
        fireOkAction(dialog);
        assertThat(dialog.closeCallCount).as("setVisible(false) called after setData on OK click").isEqualTo(1);
    }

    // -- Cancel button --

    @Test
    void testCancelClickDoesNotCallSetData() {
        var dialog = new TrackingDialog(true);
        fireCancelAction(dialog);
        assertThat(dialog.setDataCallCount).as("setData not called on Cancel click").isZero();
    }

    @Test
    void testCancelClickClosesDialog() {
        var dialog = new TrackingDialog(true);
        fireCancelAction(dialog);
        assertThat(dialog.closeCallCount).as("setVisible(false) called on Cancel click").isEqualTo(1);
    }

    // -- helpers --

    private static void fireOkAction(TrackingDialog dialog) {
        var listeners = dialog.okButton.getActionListeners();
        var event = new ActionEvent(dialog.okButton, ActionEvent.ACTION_PERFORMED, "");
        listeners[0].actionPerformed(event);
    }

    private static void fireCancelAction(TrackingDialog dialog) {
        var listeners = dialog.cancelButton.getActionListeners();
        var event = new ActionEvent(dialog.cancelButton, ActionEvent.ACTION_PERFORMED, "");
        listeners[0].actionPerformed(event);
    }

    /**
     * Concrete {@link StandardDialog} subclass that:
     * <ul>
     *   <li>tracks how many times {@link #setData()} was called</li>
     *   <li>overrides {@code setVisible(false)} to count close calls without
     *       triggering the full dialog teardown (which would NPE with a null
     *       underlying {@link javax.swing.JDialog})</li>
     * </ul>
     */
    private static class TrackingDialog extends StandardDialog {

        final boolean validData;
        int setDataCallCount = 0;
        int closeCallCount = 0;

        TrackingDialog(boolean validData) {
            super("Tracking Dialog");
            this.validData = validData;
        }

        @Override
        protected boolean isValidData() {
            return validData;
        }

        @Override
        protected void setData() {
            setDataCallCount++;
        }

        @Override
        public void setVisible(boolean visible) {
            if (!visible) {
                closeCallCount++;
                // Skip super to avoid NPE on the uninitialized JDialog field
            } else {
                super.setVisible(visible);
            }
        }
    }
}
