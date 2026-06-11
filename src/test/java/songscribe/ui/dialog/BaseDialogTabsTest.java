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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.MainFrameMockTest;
import songscribe.dom.Song;
import songscribe.message.MessageCenter;
import songscribe.message.notification.PrefsDidChangeNotification;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.ui.FlatLafKey;
import songscribe.ui.component.ScoreView;
import songscribe.util.UIUtils;

import org.jspecify.annotations.Nullable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
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
                var dialog = new TestDialog();
                dialog.setVisible(true);
                dialog.setVisible(false);
            }

            // Post the notification with DIALOG_GEOMETRY key — must clear the saved geometry
            MessageCenter.post(new PrefsDidChangeNotification(PrefsKey.DIALOG_GEOMETRY));

            // Reset invocation history so we only count calls from the next open
            uiUtilsMock.clearInvocations();

            // Reopen — saved geometry is gone, so positionDialog must be called
            try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
                new TestDialog().setVisible(true);
                uiUtilsMock.verify(() -> UIUtils.positionDialog(any(), any()));
            }
        }

        @Test
        void testAllKeyClearsSavedGeometry() {
            // Establish saved geometry by opening and closing a dialog
            try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
                var dialog = new TestDialog();
                dialog.setVisible(true);
                dialog.setVisible(false);
            }

            // Post with ALL key — must also clear saved geometry
            MessageCenter.post(new PrefsDidChangeNotification(PrefsKey.ALL));

            // Reset invocation history so we only count calls from the next open
            uiUtilsMock.clearInvocations();

            // Reopen — no saved geometry, so positionDialog must be called
            try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
                new TestDialog().setVisible(true);
                uiUtilsMock.verify(() -> UIUtils.positionDialog(any(), any()));
            }
        }
    }

    // -- createTabbedPane --

    @Test
    void testCreateTabbedPaneFirstCallRegistersTopLevelPane() {
        var dialog = new TabbedTestDialog();
        var firstPane = dialog.createTabbedPane();

        // The first call must assign the dialog's top-level tabbedPane field
        assertThat(dialog.getTabbedPane()).isSameAs(firstPane);
    }

    @Test
    void testCreateTabbedPaneSecondCallDoesNotOverwriteFirstPane() {
        var dialog = new TabbedTestDialog();
        var firstPane = dialog.createTabbedPane();
        var secondPane = dialog.createTabbedPane();

        // The second call must return a new pane but NOT overwrite the registered field
        assertThat(secondPane).isNotSameAs(firstPane);
        assertThat(dialog.getTabbedPane()).isSameAs(firstPane);
    }

    // -- tab lifecycle via ChangeListener --

    @Test
    void testTabWillShowAndTabWillHideFiredOnTabSwitch() {
        var dialog = new TabbedTestDialog();
        var tabbedPane = dialog.createTabbedPane();

        var tab0 = dialog.new TrackingTab();
        var tab1 = dialog.new TrackingTab();
        dialog.addTab(tabbedPane, "Tab 0", tab0);
        dialog.addTab(tabbedPane, "Tab 1", tab1);

        // Switch from tab 0 to tab 1 — fires the ChangeListener
        tabbedPane.setSelectedIndex(1);

        assertThat(tab1.willShowCount).as("tab1.tabWillShow called on switch to tab1").isEqualTo(1);
        assertThat(tab0.willHideCount).as("tab0.tabWillHide called on switch away").isEqualTo(1);
        assertThat(tab0.willShowCount).as("tab0.tabWillShow not called when switching away").isZero();
        assertThat(tab1.willHideCount).as("tab1.tabWillHide not called when switching to it").isZero();
    }

    // -- tabWillShow on initial setVisible(true) --

    @Test
    void testTabWillShowFiredForInitiallySelectedTabOnShow() {
        try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
            var dialog = new TabbedTestDialog();
            var tabbedPane = dialog.createTabbedPane();

            var tab0 = dialog.new TrackingTab();
            var tab1 = dialog.new TrackingTab();
            dialog.addTab(tabbedPane, "Tab 0", tab0);
            dialog.addTab(tabbedPane, "Tab 1", tab1);
            dialog.setContentTab(tabbedPane);

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
            var dialog = new TabbedTestDialog();
            var tabbedPane = dialog.createTabbedPane();

            var tab0 = dialog.new TrackingTab();
            var tab1 = dialog.new TrackingTab();
            dialog.addTab(tabbedPane, "Tab 0", tab0);
            dialog.addTab(tabbedPane, "Tab 1", tab1);
            dialog.setContentTab(tabbedPane);

            dialog.setVisible(true);
            dialog.setVisible(false);

            assertThat(tab0.willHideCount)
                .as("tab0 gets tabWillHide on setVisible(false)")
                .isEqualTo(1);
            assertThat(tab1.willHideCount)
                .as("tab1 gets tabWillHide on setVisible(false)")
                .isEqualTo(1);
        }
    }

    // -- getContentPaddingKey --

    @Test
    void testGetContentPaddingKeyReturnButtonsPaddingWhenHasButtons() {
        var dialog = new ButtonsDialog();
        assertThat(dialog.getContentPaddingKey()).isEqualTo(FlatLafKey.DIALOG_STD_BUTTONS_PADDING);
    }

    @Test
    void testGetContentPaddingKeyReturnsStdPaddingWhenNoButtons() {
        var dialog = new TestDialog();
        assertThat(dialog.getContentPaddingKey()).isEqualTo(FlatLafKey.DIALOG_STD_PADDING);
    }

    // -- getScoreView / requireScoreView / getSong --

    @Test
    void testGetScoreViewReturnsNullWhenMainFrameHasNoScoreView() {
        when(mainFrame().getScoreView()).thenReturn(null);
        var dialog = new AccessorDialog();
        assertThat(dialog.scoreView()).isNull();
    }

    @Test
    void testRequireScoreViewPropagatesExceptionFromMainFrame() {
        when(mainFrame().requireScoreView()).thenThrow(new RuntimeException("scoreView not initialized"));
        var dialog = new AccessorDialog();
        assertThatThrownBy(dialog::scoreViewRequired)
            .isInstanceOf(RuntimeException.class)
            .hasMessage("scoreView not initialized");
    }

    @Test
    void testGetSongDelegatesToRequireScoreViewGetSong() {
        var mockSong = mock(Song.class);
        var mockScoreView = mock(ScoreView.class);
        when(mainFrame().requireScoreView()).thenReturn(mockScoreView);
        when(mockScoreView.getSong()).thenReturn(mockSong);
        var dialog = new AccessorDialog();
        assertThat(dialog.song()).isSameAs(mockSong);
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

        TestDialog() {
            super("Test Dialog", false);
        }
    }

    private static class ButtonsDialog extends BaseDialog {

        ButtonsDialog() {
            super("Buttons Dialog", false);
        }

        @Override
        protected boolean hasButtons() {
            return true;
        }
    }

    private static class TabbedTestDialog extends BaseDialog {

        TabbedTestDialog() {
            super("Tabbed Dialog", false);
        }

        /**
         * Places the given tabbed pane into the dialog's content panel so that
         * {@link #setVisible(boolean)} will treat it as a tabbed dialog.
         */
        void setContentTab(JTabbedPane pane) {
            contentPanel.add(pane, BorderLayout.CENTER);
        }

        class TrackingTab extends Tab {

            int willShowCount = 0;
            int willHideCount = 0;

            TrackingTab() {
                super(FlatLafKey.DIALOG_STD_PADDING);
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
     * Subclass that widens access to protected score-view accessors for testing.
     */
    private static class AccessorDialog extends BaseDialog {

        AccessorDialog() {
            super("Accessor Dialog", false);
        }

        @Nullable ScoreView scoreView() {
            return getScoreView();
        }

        ScoreView scoreViewRequired() {
            return requireScoreView();
        }

        Song song() {
            return getSong();
        }
    }
}
