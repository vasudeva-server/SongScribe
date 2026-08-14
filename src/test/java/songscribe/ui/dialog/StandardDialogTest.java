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

import module java.desktop;

import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.MainFrameMockTest;
import songscribe.prefs.Prefs;
import songscribe.ui.component.MainFrame;
import songscribe.util.UIUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;

/**
 * Tests for {@link StandardDialog} OK/Cancel button lifecycle logic.
 */
class StandardDialogTest extends MainFrameMockTest {

    private MockedStatic<UIUtils> uiUtilsMock;
    private MockedStatic<Prefs> prefsMock;

    @BeforeEach
    void setUp() {
        uiUtilsMock = mockStatic(UIUtils.class);
        prefsMock = mockStatic(Prefs.class);
        prefsMock.when(() -> Prefs.getMap(any())).thenReturn(Collections.emptyMap());
        BaseDialogTestHelper.configureMockFrame(mainFrame());
        BaseDialog.resetVisibleBlockingDialogCount();
        BaseDialog.resetSavedGeometry();
    }

    @AfterEach
    void tearDown() {
        prefsMock.close();
        uiUtilsMock.close();
    }

    // -- OK button: commitOnOk false --

    @Test
    void testOkClickWhenCommitOnOkFalseDoesNotCommit() {
        var dialog = new TrackingDialog(mainFrame(), false);
        fireOkAction(dialog);
        assertThat(dialog.commitCallCount).as("commit not recorded when commitOnOk returns false").isZero();
    }

    @Test
    void testOkClickWhenCommitOnOkFalseDoesNotCloseDialog() {
        var dialog = new TrackingDialog(mainFrame(), false);
        fireOkAction(dialog);
        assertThat(dialog.closeCallCount).as("setVisible(false) not called when commitOnOk returns false").isZero();
    }

    // -- OK button: commitOnOk true --

    @Test
    void testOkClickWhenCommitOnOkTrueCommits() {
        var dialog = new TrackingDialog(mainFrame(), true);
        fireOkAction(dialog);
        assertThat(dialog.commitCallCount).as("commit recorded when commitOnOk returns true").isEqualTo(1);
    }

    @Test
    void testOkClickWhenCommitOnOkTrueClosesDialog() {
        var dialog = new TrackingDialog(mainFrame(), true);
        fireOkAction(dialog);
        assertThat(dialog.closeCallCount).as("setVisible(false) called after commitOnOk returns true").isEqualTo(1);
    }

    // -- Cancel button --

    @Test
    void testCancelClickDoesNotCommit() {
        var dialog = new TrackingDialog(mainFrame(), true);
        fireCancelAction(dialog);
        assertThat(dialog.commitCallCount).as("commit not recorded on Cancel click").isZero();
    }

    @Test
    void testCancelClickClosesDialog() {
        var dialog = new TrackingDialog(mainFrame(), true);
        fireCancelAction(dialog);
        assertThat(dialog.closeCallCount).as("setVisible(false) called on Cancel click").isEqualTo(1);
    }

    // -- modifyButtonPanel once-only guard --

    @Test
    void testModifyButtonPanelCalledOnlyOnFirstShow() {
        try (var ignored = mockConstruction(JDialog.class,
                (d, ctx) -> BaseDialogTestHelper.configureMockDialog(d, new Point(100, 100)))) {
            var dialog = new ModifyButtonPanelCountingDialog(mainFrame());
            dialog.setVisible(true);
            dialog.setVisible(false);
            dialog.setVisible(true);
            assertThat(dialog.modifyButtonPanelCallCount)
                .as("modifyButtonPanel called exactly once across two opens")
                .isEqualTo(1);
        }
    }

    // -- helpers --

    private static void fireOkAction(StandardDialog dialog) {
        var listeners = dialog.okButton.getActionListeners();
        var event = new ActionEvent(dialog.okButton, ActionEvent.ACTION_PERFORMED, "");
        listeners[0].actionPerformed(event);
    }

    private static void fireCancelAction(StandardDialog dialog) {
        var listeners = dialog.cancelButton.getActionListeners();
        var event = new ActionEvent(dialog.cancelButton, ActionEvent.ACTION_PERFORMED, "");
        listeners[0].actionPerformed(event);
    }

    /**
     * Concrete {@link StandardDialog} subclass that:
     * <ul>
     *   <li>tracks how many times {@link #commitOnOk()} actually committed</li>
     *   <li>overrides {@code setVisible(false)} to count close calls without
     *       triggering the full dialog teardown (which would NPE with a null
     *       underlying {@link JDialog})</li>
     * </ul>
     */
    private static class TrackingDialog extends StandardDialog {

        final boolean validData;
        int commitCallCount = 0;
        int closeCallCount = 0;

        TrackingDialog(MainFrame mainFrame, boolean validData) {
            super(mainFrame, "Tracking Dialog");
            this.validData = validData;
        }

        @Override
        protected boolean commitOnOk() {
            if (!validData) {
                return false;
            }

            commitCallCount++;
            return true;
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

    /**
     * Concrete {@link StandardDialog} subclass that counts how many times
     * {@link #modifyButtonPanel()} is called, used to verify the once-only guard.
     * Close calls skip {@code super} to avoid NPE on the uninitialized JDialog field.
     */
    private static class ModifyButtonPanelCountingDialog extends StandardDialog {

        int modifyButtonPanelCallCount = 0;

        ModifyButtonPanelCountingDialog(MainFrame mainFrame) {
            super(mainFrame, "ModifyButtonPanel Dialog");
        }

        @Override
        protected Object modifyButtonPanel() {
            modifyButtonPanelCallCount++;
            return super.modifyButtonPanel();
        }

        @Override
        public void setVisible(boolean visible) {
            // If not visible, skip super to avoid NPE on the disposed JDialog
            if (visible) {
                super.setVisible(true);
            }
        }
    }
}
