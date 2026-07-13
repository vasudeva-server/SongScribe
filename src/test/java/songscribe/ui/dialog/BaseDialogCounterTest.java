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

package songscribe.ui.dialog;

import module java.desktop;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.MainFrameMockTest;
import songscribe.RequiresDisplay;
import songscribe.message.MessageCenter;
import songscribe.ui.component.MainFrame;
import songscribe.message.notification.DialogVisibilityDidChangeNotification;
import songscribe.ui.FlatLafKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

class BaseDialogCounterTest extends MainFrameMockTest {

    @BeforeEach
    void setUp() {
        BaseDialogTestHelper.configureMockFrame(mainFrame());
        BaseDialog.resetVisibleBlockingDialogCount();
    }

    @AfterEach
    void tearDown() {
        BaseDialog.resetVisibleBlockingDialogCount();
    }

    // -- isAnyBlockingDialogVisible: open/close state --

    @Test
    void testIsAnyBlockingDialogVisibleFalseAfterClose() {
        try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
            var dialog = new TestDialog(mainFrame());
            dialog.setVisible(true);
            dialog.setVisible(false);
            assertThat(BaseDialog.isAnyBlockingDialogVisible()).isFalse();
        }
    }

    @Test
    void testIsAnyBlockingDialogVisibleFalseInitially() {
        assertThat(BaseDialog.isAnyBlockingDialogVisible()).isFalse();
    }

    @Test
    @RequiresDisplay
    void testIsAnyBlockingDialogVisibleTrueAfterOpen() {
        try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
            var dialog = new TestDialog(mainFrame());
            dialog.setVisible(true);
            assertThat(BaseDialog.isAnyBlockingDialogVisible()).isTrue();
        }
    }

    // -- nested dialogs --

    @Test
    @RequiresDisplay
    void testNestedDialogsCounterTracksAllLevels() {
        try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
            var dialogA = new TestDialog(mainFrame());
            var dialogB = new TestDialog(mainFrame());

            dialogA.setVisible(true);
            assertThat(BaseDialog.isAnyBlockingDialogVisible()).isTrue();

            dialogB.setVisible(true);
            assertThat(BaseDialog.isAnyBlockingDialogVisible()).isTrue();

            dialogB.setVisible(false);
            assertThat(BaseDialog.isAnyBlockingDialogVisible()).isTrue();

            dialogA.setVisible(false);
            assertThat(BaseDialog.isAnyBlockingDialogVisible()).isFalse();
        }
    }

    // -- resetVisibleBlockingDialogCount --

    @Test
    @RequiresDisplay
    void testResetVisibleBlockingDialogCountResetsToZero() {
        try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
            var dialog = new TestDialog(mainFrame());
            dialog.setVisible(true);
            assertThat(BaseDialog.isAnyBlockingDialogVisible()).isTrue();

            BaseDialog.resetVisibleBlockingDialogCount();

            assertThat(BaseDialog.isAnyBlockingDialogVisible()).isFalse();
        }
    }

    // -- notification transitions --

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    @RequiresDisplay
    class NotificationTransitionTests {

        private MockedStatic<MessageCenter> messageCenterMock;

        @BeforeEach
        void setUpMessageCenterMock() {
            messageCenterMock = mockStatic(MessageCenter.class);
        }

        @AfterEach
        void tearDownMessageCenterMock() {
            messageCenterMock.close();
        }

        @Test
        void testNotificationPostedOnFirstDialogOpen() {
            try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
                new TestDialog(mainFrame()).setVisible(true);

                messageCenterMock.verify(() -> MessageCenter.post(
                    argThat(m -> m instanceof DialogVisibilityDidChangeNotification n && n.isVisible())
                ));
            }
        }

        @Test
        void testNotificationNotPostedOnSecondDialogOpen() {
            try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
                var dialogA = new TestDialog(mainFrame());
                var dialogB = new TestDialog(mainFrame());
                dialogA.setVisible(true);
                dialogB.setVisible(true);

                messageCenterMock.verify(
                    () -> MessageCenter.post(argThat(m -> m instanceof DialogVisibilityDidChangeNotification)),
                    times(1)
                );
            }
        }

        @Test
        void testNotificationPostedWhenLastDialogCloses() {
            try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
                var dialog = new TestDialog(mainFrame());
                dialog.setVisible(true);
                dialog.setVisible(false);

                messageCenterMock.verify(() -> MessageCenter.post(
                    argThat(m -> m instanceof DialogVisibilityDidChangeNotification n && !n.isVisible())
                ));
            }
        }

        @Test
        void testNotificationNotPostedWhenNestedDialogCloses() {
            try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
                var dialogA = new TestDialog(mainFrame());
                var dialogB = new TestDialog(mainFrame());
                dialogA.setVisible(true);
                dialogB.setVisible(true);
                dialogB.setVisible(false);

                messageCenterMock.verify(
                    () -> MessageCenter.post(
                        argThat(m -> m instanceof DialogVisibilityDidChangeNotification n && !n.isVisible())
                    ),
                    never()
                );
            }
        }
    }


    // -- exclusive category increments counter --

    @Test
    @RequiresDisplay
    void testExclusiveDialogIncrementsCounter() {
        try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
            var dialog = new TestExclusiveDialog(mainFrame());
            dialog.setVisible(true);
            assertThat(BaseDialog.isAnyBlockingDialogVisible()).isTrue();
        }
    }

    @Test
    void testExclusiveDialogDecrementsCounterOnClose() {
        try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
            var dialog = new TestExclusiveDialog(mainFrame());
            dialog.setVisible(true);
            dialog.setVisible(false);
            assertThat(BaseDialog.isAnyBlockingDialogVisible()).isFalse();
        }
    }

    // -- getData() returning false cancels show --

    @Test
    void testGetDataFalseDialogNotShownAndCounterNotIncremented() {
        try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
            var dialog = new CancellingDialog(mainFrame());
            dialog.setVisible(true);
            assertThat(BaseDialog.isAnyBlockingDialogVisible()).isFalse();
        }
    }

    // -- tab getData() iteration short-circuits on first false --

    @Test
    void testGetDataTabIterationShortCircuitsOnFirstFalse() {
        try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
            var dialog = new TwoTabDialog(mainFrame());
            var failingTab = dialog.new FailingTab();
            var secondTab = dialog.new SecondTab();
            dialog.registerTab(failingTab);
            dialog.registerTab(secondTab);

            dialog.setVisible(true);

            assertThat(dialog.wasGetDataCalledOnTab(0)).isTrue();
            assertThat(dialog.wasGetDataCalledOnTab(1)).isFalse();
        }
    }

    // -- helpers --

    private static final Point DEFAULT_LOCATION = new Point(100, 100);

    private void configureMockDialog(JDialog dialog) {
        BaseDialogTestHelper.configureMockDialog(dialog, DEFAULT_LOCATION);
    }

    private static class TestDialog extends BaseDialog {

        TestDialog(MainFrame mainFrame) {
            super(mainFrame, "Test Dialog", false);
        }
    }

    private static class TestInformationalDialog extends BaseDialog {

        TestInformationalDialog(MainFrame mainFrame) {
            super(mainFrame, "Info Dialog", false, DialogCategory.INFORMATIONAL);
        }
    }

    private static class TestExclusiveDialog extends BaseDialog {

        TestExclusiveDialog(MainFrame mainFrame) {
            super(mainFrame, "Exclusive Dialog", false, DialogCategory.EXCLUSIVE);
        }
    }

    private static class CancellingDialog extends BaseDialog {

        CancellingDialog(MainFrame mainFrame) {
            super(mainFrame, "Cancelling Dialog", false);
        }

        @Override
        protected boolean getData() {
            return false;
        }
    }

    private static class TwoTabDialog extends BaseDialog {

        private static final int TAB_COUNT = 2;

        private final boolean[] getDataCalled = new boolean[TAB_COUNT];

        TwoTabDialog(MainFrame mainFrame) {
            super(mainFrame, "Two Tab Dialog", false);
        }

        boolean wasGetDataCalledOnTab(int index) {
            return getDataCalled[index];
        }

        class FailingTab extends Tab {

            FailingTab() {
                super(FlatLafKey.DIALOG_STD_PADDING);
            }

            @Override
            protected void initContents() {}

            @Override
            protected boolean getData() {
                getDataCalled[0] = true;
                return false;
            }
        }

        class SecondTab extends Tab {

            SecondTab() {
                super(FlatLafKey.DIALOG_STD_PADDING);
            }

            @Override
            protected void initContents() {}

            @Override
            protected boolean getData() {
                getDataCalled[1] = true;
                return true;
            }
        }
    }

    // -- informational dialogs do not affect counter --

    @Test
    @RequiresDisplay
    void testInformationalDialogDoesNotIncrementCounter() {
        try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
            var dialog = new TestInformationalDialog(mainFrame());
            dialog.setVisible(true);
            assertThat(BaseDialog.isAnyBlockingDialogVisible()).isFalse();
        }
    }

    @Test
    void testInformationalDialogDoesNotDecrementCounter() {
        try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
            var dialog = new TestInformationalDialog(mainFrame());
            dialog.setVisible(true);
            dialog.setVisible(false);
            assertThat(BaseDialog.isAnyBlockingDialogVisible()).isFalse();
        }
    }

    // -- mixed informational and blocking dialogs --

    @Test
    @RequiresDisplay
    void testMixedDialogsOnlyBlockingAffectsCounter() {
        try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
            var infoDialog = new TestInformationalDialog(mainFrame());
            var blockingDialog = new TestDialog(mainFrame());

            infoDialog.setVisible(true);
            assertThat(BaseDialog.isAnyBlockingDialogVisible()).isFalse();

            blockingDialog.setVisible(true);
            assertThat(BaseDialog.isAnyBlockingDialogVisible()).isTrue();

            infoDialog.setVisible(false);
            assertThat(BaseDialog.isAnyBlockingDialogVisible()).isTrue();

            blockingDialog.setVisible(false);
            assertThat(BaseDialog.isAnyBlockingDialogVisible()).isFalse();
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    @RequiresDisplay
    class InformationalNotificationTests {

        private MockedStatic<MessageCenter> messageCenterMock;

        @BeforeEach
        void setUpMessageCenterMock() {
            messageCenterMock = mockStatic(MessageCenter.class);
        }

        @AfterEach
        void tearDownMessageCenterMock() {
            messageCenterMock.close();
        }

        @Test
        void testInformationalDialogDoesNotPostNotification() {
            try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
                var dialog = new TestInformationalDialog(mainFrame());
                dialog.setVisible(true);
                dialog.setVisible(false);

                messageCenterMock.verify(
                    () -> MessageCenter.post(
                        argThat(m -> m instanceof DialogVisibilityDidChangeNotification)
                    ),
                    never()
                );
            }
        }
    }
}
