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
import songscribe.ui.FlatLafKey;
import songscribe.util.UIUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

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

    // -- OK button: isValidData false --

    @Test
    void testOkClickWhenIsValidDataFalseDoesNotCallSetData() {
        var dialog = new TrackingDialog(mainFrame(), false);
        fireOkAction(dialog);
        assertThat(dialog.setDataCallCount).as("setData not called when isValidData returns false").isZero();
    }

    @Test
    void testOkClickWhenIsValidDataFalseDoesNotCloseDialog() {
        var dialog = new TrackingDialog(mainFrame(), false);
        fireOkAction(dialog);
        assertThat(dialog.closeCallCount).as("setVisible(false) not called when isValidData returns false").isZero();
    }

    // -- OK button: isValidData true --

    @Test
    void testOkClickWhenIsValidDataTrueCallsSetData() {
        var dialog = new TrackingDialog(mainFrame(), true);
        fireOkAction(dialog);
        assertThat(dialog.setDataCallCount).as("setData called when isValidData returns true").isEqualTo(1);
    }

    @Test
    void testOkClickWhenIsValidDataTrueClosesDialog() {
        var dialog = new TrackingDialog(mainFrame(), true);
        fireOkAction(dialog);
        assertThat(dialog.closeCallCount).as("setVisible(false) called after setData on OK click").isEqualTo(1);
    }

    // -- Cancel button --

    @Test
    void testCancelClickDoesNotCallSetData() {
        var dialog = new TrackingDialog(mainFrame(), true);
        fireCancelAction(dialog);
        assertThat(dialog.setDataCallCount).as("setData not called on Cancel click").isZero();
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

    // -- isValidData tab iteration: first failing tab short-circuits --

    @Test
    void testIsValidDataTabIterationShortCircuitsOnFirstInvalidTab() {
        var dialog = new TabbedStandardDialog(mainFrame());
        var tab0 = dialog.new ValidationTab(false);
        var tab1 = dialog.new ValidationTab(true);
        dialog.registerTab(tab0);
        dialog.registerTab(tab1);

        fireOkAction(dialog);

        assertThat(tab0.isValidDataCallCount).as("tab[0].isValidData called").isEqualTo(1);
        assertThat(tab1.isValidDataCallCount).as("tab[1].isValidData not called after tab[0] fails").isZero();
        assertThat(dialog.setDataCallCount).as("setData not called when isValidData returns false").isZero();
    }

    // -- setData iterates all registered tabs --

    @Test
    void testSetDataIteratesAllRegisteredTabs() {
        var dialog = new TabbedStandardDialog(mainFrame());
        var tab0 = dialog.new ValidationTab(true);
        var tab1 = dialog.new ValidationTab(true);
        dialog.registerTab(tab0);
        dialog.registerTab(tab1);

        fireOkAction(dialog);

        assertThat(tab0.tabSetDataCallCount).as("tab[0].setData called").isEqualTo(1);
        assertThat(tab1.tabSetDataCallCount).as("tab[1].setData called").isEqualTo(1);
    }

    // -- repaintScore null-safe when scoreView is null --

    @Test
    void testRepaintScoreIsNullSafeWhenScoreViewIsNull() {
        when(mainFrame().getScoreView()).thenReturn(null);
        var dialog = new TrackingDialog(mainFrame(), true);

        assertThatNoException()
            .as("OK click with null scoreView must not throw")
            .isThrownBy(() -> fireOkAction(dialog));
        assertThat(dialog.setDataCallCount)
            .as("setData called even when scoreView is null")
            .isEqualTo(1);
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
     *   <li>tracks how many times {@link #setData()} was called</li>
     *   <li>overrides {@code setVisible(false)} to count close calls without
     *       triggering the full dialog teardown (which would NPE with a null
     *       underlying {@link JDialog})</li>
     * </ul>
     */
    private static class TrackingDialog extends StandardDialog {

        final boolean validData;
        int setDataCallCount = 0;
        int closeCallCount = 0;

        TrackingDialog(MainFrame mainFrame, boolean validData) {
            super(mainFrame, "Tracking Dialog");
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

    /**
     * Concrete {@link StandardDialog} subclass that tracks how many times
     * its base-class {@link #setData()} is called, used to verify tab iteration.
     * Close calls skip {@code super} to avoid NPE on the uninitialized JDialog field.
     */
    private static class TabbedStandardDialog extends StandardDialog {

        int setDataCallCount = 0;

        TabbedStandardDialog(MainFrame mainFrame) {
            super(mainFrame, "Tabbed Standard Dialog");
        }

        @Override
        protected void setData() {
            setDataCallCount++;
            super.setData();
        }

        @Override
        public void setVisible(boolean visible) {
            // If not visible, skip super to avoid NPE on the disposed JDialog
            if (visible) {
                super.setVisible(true);
            }
        }

        /**
         * A {@link Tab} that tracks {@link #isValidData()} and {@link #setData()} calls.
         * {@code valid} controls the return value of {@code isValidData()}.
         */
        class ValidationTab extends Tab {

            final boolean valid;
            int isValidDataCallCount = 0;
            int tabSetDataCallCount = 0;

            ValidationTab(boolean valid) {
                super("Validation Tab", FlatLafKey.DIALOG_STD_PADDING);
                this.valid = valid;
            }

            @Override
            protected void initContents() {}

            @Override
            protected boolean isValidData() {
                isValidDataCallCount++;
                return valid;
            }

            @Override
            protected void setData() {
                tabSetDataCallCount++;
            }
        }
    }
}
