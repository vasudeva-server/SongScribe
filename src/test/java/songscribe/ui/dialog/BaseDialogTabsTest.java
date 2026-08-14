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

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.MainFrameMockTest;
import songscribe.ui.component.MainFrame;
import songscribe.message.MessageCenter;
import songscribe.message.notification.PrefsDidChangeNotification;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.ui.FlatLafKey;
import songscribe.util.UIUtils;

import org.jspecify.annotations.Nullable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link BaseDialog} tab lifecycle and geometry-reset subscriber behavior.
 */
class BaseDialogTabsTest extends MainFrameMockTest {

    private static final Point DIALOG_POSITION = new Point(200, 300);

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
        BaseDialog.resetVisibleBlockingDialogCount();
        BaseDialog.resetSavedGeometry();
        prefsMock.close();
        uiUtilsMock.close();
    }

    // -- GeometryResetSubscriber --

    @Nested
    class GeometryResetSubscriberTests {

        @Test
        void testDialogGeometryKeyClearsSavedGeometry() {
            // Establish saved geometry by opening and closing a dialog
            try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
                var dialog = new TestDialog(mainFrame());
                dialog.setVisible(true);
                dialog.setVisible(false);
            }

            // Post the notification with DIALOG_GEOMETRY key — must clear the saved geometry
            MessageCenter.post(new PrefsDidChangeNotification(PrefsKey.DIALOG_GEOMETRY));

            // Reset invocation history so we only count calls from the next open
            uiUtilsMock.clearInvocations();

            // Reopen — saved geometry is gone, so positionDialog must be called
            try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
                new TestDialog(mainFrame()).setVisible(true);
                uiUtilsMock.verify(() -> UIUtils.positionDialog(any(), any()));
            }
        }

        @Test
        void testAllKeyClearsSavedGeometry() {
            // Establish saved geometry by opening and closing a dialog
            try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
                var dialog = new TestDialog(mainFrame());
                dialog.setVisible(true);
                dialog.setVisible(false);
            }

            // Post with ALL key — must also clear saved geometry
            MessageCenter.post(new PrefsDidChangeNotification(PrefsKey.ALL));

            // Reset invocation history so we only count calls from the next open
            uiUtilsMock.clearInvocations();

            // Reopen — no saved geometry, so positionDialog must be called
            try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
                new TestDialog(mainFrame()).setVisible(true);
                uiUtilsMock.verify(() -> UIUtils.positionDialog(any(), any()));
            }
        }
    }

    // -- createTabbedContent --

    @Test
    void testCreateTabbedContentReturnsSameCompositeOnEveryCall() {
        var dialog = new TabbedTestDialog(mainFrame());
        var firstContent = dialog.createTabbedContent();
        var secondContent = dialog.createTabbedContent();

        // There is a single sidebar/card composite per dialog — every call returns it.
        assertThat(secondContent).isSameAs(firstContent);
        assertThat(dialog.getTabbedContent()).isSameAs(firstContent);
    }

    // -- tab lifecycle via sidebar selection --

    @Test
    void testTabWillShowAndTabWillHideFiredOnTabSwitch() {
        var dialog = new TabbedTestDialog(mainFrame());
        dialog.createTabbedContent();

        var tab0 = dialog.new TrackingTab("Tab 0");
        var tab1 = dialog.new TrackingTab("Tab 1");
        dialog.addTab(tab0);
        dialog.addTab(tab1);

        // Switch from tab 0 to tab 1 — fires the sidebar's ListSelectionListener
        dialog.getTabList().setSelectedIndex(1);

        assertThat(tab1.willShowCount).as("tab1.tabWillShow called on switch to tab1").isEqualTo(1);
        assertThat(tab0.willHideCount).as("tab0.tabWillHide called on switch away").isEqualTo(1);
        assertThat(tab0.willShowCount).as("tab0.tabWillShow not called when switching away").isZero();
        assertThat(tab1.willHideCount).as("tab1.tabWillHide not called when switching to it").isZero();
    }

    // -- tabWillShow on initial setVisible(true) --

    @Test
    void testTabWillShowFiredForInitiallySelectedTabOnShow() {
        try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
            var dialog = new TabbedTestDialog(mainFrame());
            var tabbedContent = dialog.createTabbedContent();

            var tab0 = dialog.new TrackingTab("Tab 0");
            var tab1 = dialog.new TrackingTab("Tab 1");
            dialog.addTab(tab0);
            dialog.addTab(tab1);
            dialog.setContentTab(tabbedContent);

            dialog.setVisible(true);

            assertThat(tab0.willShowCount)
                .as("initially-selected tab (index 0) gets tabWillShow on setVisible(true)")
                .isEqualTo(1);
            assertThat(tab1.willShowCount)
                .as("non-selected tab does not get tabWillShow on setVisible(true)")
                .isZero();
        }
    }

    // -- tabWillHide on setVisible(false) --

    @Test
    void testTabWillHideCalledForAllTabsOnHide() {
        try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
            var dialog = new TabbedTestDialog(mainFrame());
            var tabbedContent = dialog.createTabbedContent();

            var tab0 = dialog.new TrackingTab("Tab 0");
            var tab1 = dialog.new TrackingTab("Tab 1");
            dialog.addTab(tab0);
            dialog.addTab(tab1);
            dialog.setContentTab(tabbedContent);

            dialog.setVisible(true);
            dialog.setVisible(false);

            assertThat(tab0.willHideCount)
                .as("tab0 gets tabWillHide on setVisible(false)")
                .isEqualTo(1);

            // tab1 is not initially selected, so the initial setVisible(true) already
            // fires tabWillHide on it via selectTab(0); setVisible(false) fires it again.
            final var expectedTab1HideCount = 2;
            assertThat(tab1.willHideCount)
                .as("tab1 gets tabWillHide on initial show (not selected) and on setVisible(false)")
                .isEqualTo(expectedTab1HideCount);
        }
    }

    // -- showTab tab-selection requests --

    @Test
    void testSetVisibleWithNoTabRequestSelectsFirstTab() {
        try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
            var dialog = new TabbedTestDialog(mainFrame());
            var tabbedContent = dialog.createTabbedContent();

            var tab0 = dialog.new TrackingTab("Tab 0");
            var tab1 = dialog.new TrackingTab("Tab 1");
            dialog.addTab(tab0);
            dialog.addTab(tab1);
            dialog.setContentTab(tabbedContent);

            dialog.setVisible(true);

            assertThat(tab0.willShowCount)
                .as("tab0 (the first tab) is shown when no tab was requested")
                .isEqualTo(1);
            assertThat(tab0.willHideCount)
                .as("tab0 is not hidden on its own initial show")
                .isZero();
            assertThat(tab1.willShowCount)
                .as("tab1 is not shown when no tab was requested")
                .isZero();
            assertThat(tab1.willHideCount)
                .as("tab1 is hidden as the non-selected tab on initial show")
                .isEqualTo(1);
        }
    }

    @Test
    void testShowTabWithNonFirstTabOpensOnThatTab() {
        try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
            var dialog = new TabbedTestDialog(mainFrame());
            var tabbedContent = dialog.createTabbedContent();

            var tab0 = dialog.new TrackingTab("Tab 0");
            var tab1 = dialog.new TrackingTab("Tab 1");
            dialog.addTab(tab0);
            dialog.addTab(tab1);
            dialog.setContentTab(tabbedContent);

            dialog.showTab(tab1, null);

            assertThat(tab1.willShowCount)
                .as("the requested non-first tab is shown")
                .isEqualTo(1);
            assertThat(tab0.willShowCount)
                .as("the first tab is not shown when a later tab was requested")
                .isZero();
            assertThat(tab0.willHideCount)
                .as("the first tab is hidden as the non-selected tab")
                .isEqualTo(1);
        }
    }

    @Test
    void testShowTabRequestIsConsumedAfterASuccessfulShow() {
        try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
            var dialog = new TabbedTestDialog(mainFrame());
            var tabbedContent = dialog.createTabbedContent();

            var tab0 = dialog.new TrackingTab("Tab 0");
            var tab1 = dialog.new TrackingTab("Tab 1");
            dialog.addTab(tab0);
            dialog.addTab(tab1);
            dialog.setContentTab(tabbedContent);

            dialog.showTab(tab1, null);
            dialog.setVisible(false);

            // The earlier request must not survive into this unrelated later open.
            dialog.setVisible(true);

            assertThat(tab0.willShowCount)
                .as("a later plain setVisible(true) opens on tab 0 again, not the earlier request")
                .isEqualTo(1);
        }
    }

    @Test
    void testShowTabRequestIsConsumedWhenTheShowAborts() {
        try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
            var dialog = new CancellingTabbedTestDialog(mainFrame());
            var tabbedContent = dialog.createTabbedContent();

            var tab0 = dialog.new TrackingTab("Tab 0");
            var tab1 = dialog.new TrackingTab("Tab 1");
            dialog.addTab(tab0);
            dialog.addTab(tab1);
            dialog.setContentTab(tabbedContent);

            // getData() returns false, so this show aborts before any tab is selected.
            dialog.showTab(tab1, null);

            // The aborted show's request must not survive into this unrelated later open.
            dialog.setVisible(true);

            assertThat(tab0.willShowCount)
                .as("a later show must not inherit the aborted show's requested tab")
                .isEqualTo(1);
            assertThat(tab1.willShowCount)
                .as("the tab requested by the aborted show must never have been shown")
                .isZero();
        }
    }

    /**
     * The control a caller names for one particular open is the one actually asked to take
     * the caret.
     * <p>
     * Asserted against the control itself rather than against any bookkeeping the dialog
     * keeps: "the request was honored" and "the request was read and dropped" are the same
     * to a test that only watches internal state, so only the delivered effect can fail
     * when the feature breaks.
     */
    @Test
    void testShowTabFocusesTheControlTheCallerNamed() {
        try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
            var dialog = new TabbedTestDialog(mainFrame());
            var tabbedContent = dialog.createTabbedContent();

            var tab0 = dialog.new TrackingTab("Tab 0");
            dialog.addTab(tab0);
            dialog.setContentTab(tabbedContent);

            var field = spy(new JTextField());
            dialog.showTab(tab0, field);
            flushEventQueue();

            verify(field).requestFocusInWindow();
        }
    }

    /**
     * A show that aborts in {@code getData()} focuses nothing, and leaves nothing behind
     * for a later unrelated open to pick up.
     */
    @Test
    void testAbortedShowFocusesNothingAndLeavesNoRequestBehind() {
        try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
            var dialog = new CancellingTabbedTestDialog(mainFrame());
            var tabbedContent = dialog.createTabbedContent();

            var tab0 = dialog.new TrackingTab("Tab 0");
            dialog.addTab(tab0);
            dialog.setContentTab(tabbedContent);

            var field = spy(new JTextField());

            // getData() returns false, so this show aborts before anything is focused.
            dialog.showTab(tab0, field);
            flushEventQueue();

            verify(field, never()).requestFocusInWindow();

            // The next (successful) show must not inherit the aborted show's target.
            dialog.setVisible(true);
            flushEventQueue();

            verify(field, never()).requestFocusInWindow();
        }
    }

    /**
     * A tab that always leads with the same control says so by overriding
     * {@code getInitialFocus}, and the show that puts it on screen honors it without the
     * caller naming anything.
     */
    @Test
    void testShowFocusesTheShownTabsOwnLeadingControl() {
        try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
            var dialog = new TabbedTestDialog(mainFrame());
            var tabbedContent = dialog.createTabbedContent();

            var tab0 = dialog.new TrackingTab("Tab 0");
            tab0.initialFocus = spy(new JTextField());
            dialog.addTab(tab0);
            dialog.setContentTab(tabbedContent);

            dialog.setVisible(true);
            flushEventQueue();

            verify(tab0.initialFocus).requestFocusInWindow();
        }
    }

    /**
     * The other way a tab appears: the user switches to it in a dialog that is already open,
     * where the show path has long since run. {@code selectTab} honors it instead.
     */
    @Test
    void testSwitchingToATabWhileShowingFocusesItsLeadingControl() {
        try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> {
            configureMockDialog(d);
            when(d.isShowing()).thenReturn(true);
        })) {
            var dialog = new TabbedTestDialog(mainFrame());
            var tabbedContent = dialog.createTabbedContent();

            var tab0 = dialog.new TrackingTab("Tab 0");
            var tab1 = dialog.new TrackingTab("Tab 1");
            tab1.initialFocus = spy(new JTextField());
            dialog.addTab(tab0);
            dialog.addTab(tab1);
            dialog.setContentTab(tabbedContent);

            dialog.setVisible(true);

            // The window is up; switching to tab 1 runs the already-showing path.
            dialog.getTabList().setSelectedIndex(1);
            flushEventQueue();

            verify(tab1.initialFocus).requestFocusInWindow();
        }
    }

    /**
     * A caller's target is only ever meant for the tab it arrived with, so a tab that is not
     * on screen must not have its leading control focused instead.
     */
    @Test
    void testShowDoesNotFocusAHiddenTabsLeadingControl() {
        try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
            var dialog = new TabbedTestDialog(mainFrame());
            var tabbedContent = dialog.createTabbedContent();

            var tab0 = dialog.new TrackingTab("Tab 0");
            var tab1 = dialog.new TrackingTab("Tab 1");
            tab1.initialFocus = spy(new JTextField());
            dialog.addTab(tab0);
            dialog.addTab(tab1);
            dialog.setContentTab(tabbedContent);

            // Opens on tab 0, so tab 1's control is off screen.
            dialog.setVisible(true);
            flushEventQueue();

            verify(tab1.initialFocus, never()).requestFocusInWindow();
        }
    }

    /**
     * Runs everything already queued on the event thread, so a focus request the dialog
     * posted with {@code invokeLater} has actually happened by the time we assert.
     */
    private static void flushEventQueue() {
        try {
            SwingUtilities.invokeAndWait(() -> { });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while draining the event queue", e);
        } catch (InvocationTargetException e) {
            throw new AssertionError("draining the event queue failed", e);
        }
    }

    @Test
    void testShowTabWithUnregisteredTabFallsBackToFirstTab() {
        try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
            var dialog = new TabbedTestDialog(mainFrame());
            var tabbedContent = dialog.createTabbedContent();

            var tab0 = dialog.new TrackingTab("Tab 0");
            var tab1 = dialog.new TrackingTab("Tab 1");
            dialog.addTab(tab0);
            dialog.addTab(tab1);
            dialog.setContentTab(tabbedContent);

            // Never passed to addTab, so it is not in the tabs list consumeShowRequest searches.
            var strayTab = dialog.new TrackingTab("Never Registered");

            dialog.showTab(strayTab, null);

            assertThat(tab0.willShowCount)
                .as("an unregistered tab falls back to the first tab rather than leaving no card shown")
                .isEqualTo(1);
            assertThat(strayTab.willShowCount)
                .as("a tab never passed to addTab cannot itself be shown")
                .isZero();
        }
    }

    // -- getContentPaddingKey --

    @Test
    void testGetContentPaddingKeyReturnButtonsPaddingWhenHasButtons() {
        var dialog = new ButtonsDialog(mainFrame());
        assertThat(dialog.getContentPaddingKey()).isEqualTo(FlatLafKey.DIALOG_STD_BUTTONS_PADDING);
    }

    @Test
    void testGetContentPaddingKeyReturnsStdPaddingWhenNoButtons() {
        var dialog = new TestDialog(mainFrame());
        assertThat(dialog.getContentPaddingKey()).isEqualTo(FlatLafKey.DIALOG_STD_PADDING);
    }

    // -- addSeparator --

    @Test
    void testTitledSectionAddSeparatorOnYAxisAddsVerticalStrut() {
        var section = new BaseDialog.TitledSection("Section");
        BaseDialog.addSeparator(section);

        assertThat(section.getComponentCount()).isEqualTo(1);
        // A vertical strut has zero preferred width and positive preferred height
        var strut = section.getComponent(0);
        assertThat(strut.getPreferredSize().width).isZero();
        assertThat(strut.getPreferredSize().height).isPositive();
    }

    @Test
    void testTitledSectionAddSeparatorOnXAxisAddsHorizontalStrut() {
        var section = new BaseDialog.TitledSection("Section", BoxLayout.X_AXIS);
        BaseDialog.addSeparator(section);

        assertThat(section.getComponentCount()).isEqualTo(1);
        // A horizontal strut has positive preferred width and zero preferred height
        var strut = section.getComponent(0);
        assertThat(strut.getPreferredSize().width).isPositive();
        assertThat(strut.getPreferredSize().height).isZero();
    }

    // -- helpers --

    private void configureMockDialog(JDialog dialog) {
        BaseDialogTestHelper.configureMockDialog(dialog, DIALOG_POSITION);
    }

    private static class TestDialog extends BaseDialog {

        TestDialog(MainFrame mainFrame) {
            super(mainFrame, "Test Dialog", false);
        }
    }

    private static class ButtonsDialog extends BaseDialog {

        ButtonsDialog(MainFrame mainFrame) {
            super(mainFrame, "Buttons Dialog", false);
        }

        @Override
        protected boolean hasButtons() {
            return true;
        }
    }

    private static class TabbedTestDialog extends BaseDialog {

        TabbedTestDialog(MainFrame mainFrame) {
            super(mainFrame, "Tabbed Dialog", false);
        }

        /**
         * Places the given sidebar/card composite into the dialog's content panel so
         * that {@link #setVisible(boolean)} will treat it as a tabbed dialog.
         */
        void setContentTab(JComponent tabbedContent) {
            contentPanel.add(tabbedContent, BorderLayout.CENTER);
        }

        class TrackingTab extends Tab {

            int willShowCount = 0;
            int willHideCount = 0;

            /**
             * Returned from {@link #getInitialFocus()} when set, mirroring how a real tab
             * (e.g. the Playback tab's instrument list) names its own leading control.
             */
            @Nullable JComponent initialFocus = null;

            TrackingTab(String title) {
                super(title, FlatLafKey.DIALOG_STD_PADDING);
            }

            @Override
            protected @Nullable JComponent getInitialFocus() {
                return initialFocus;
            }

            @Override
            protected void initContents() {}

            @Override
            protected void tabWillShow() {
                willShowCount++;
            }

            @Override
            protected void tabWillHide() {
                willHideCount++;
            }
        }
    }

    /**
     * A tabbed dialog whose {@code getData()} returns false for exactly the first show and true
     * thereafter, for testing that an aborted show still consumes a pending
     * {@link BaseDialog#showTab} request — leaving the next (successful) show to select the
     * default tab rather than the aborted one's request.
     */
    private static class CancellingTabbedTestDialog extends TabbedTestDialog {

        private boolean cancelNextShow = true;

        CancellingTabbedTestDialog(MainFrame mainFrame) {
            super(mainFrame);
        }

        @Override
        protected boolean getData() {
            if (cancelNextShow) {
                cancelNextShow = false;
                return false;
            }

            return true;
        }
    }
}
