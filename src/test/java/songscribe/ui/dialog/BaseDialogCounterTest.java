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

import songscribe.UnitTest;
import songscribe.message.MessageCenter;
import songscribe.message.notification.DialogVisibilityDidChangeNotification;
import songscribe.ui.component.MainFrame;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class BaseDialogCounterTest extends UnitTest {

    private MockedStatic<MainFrame> mainFrameMock;

    @BeforeEach
    void setUp() {
        mainFrameMock = mockStatic(MainFrame.class);
        var mockFrame = mock(MainFrame.class);
        mainFrameMock.when(MainFrame::getInstance).thenReturn(mockFrame);
        BaseDialogTestHelper.configureMockFrame(mockFrame);
        BaseDialog.resetVisibleBlockingDialogCount();
    }

    @AfterEach
    void tearDown() {
        BaseDialog.resetVisibleBlockingDialogCount();
        mainFrameMock.close();
    }

    // -- isAnyBlockingDialogVisible: open/close state --

    @Test
    void testIsAnyBlockingDialogVisibleFalseAfterClose() {
        try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
            var dialog = new TestDialog();
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
    void testIsAnyBlockingDialogVisibleTrueAfterOpen() {
        try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
            var dialog = new TestDialog();
            dialog.setVisible(true);
            assertThat(BaseDialog.isAnyBlockingDialogVisible()).isTrue();
        }
    }

    // -- nested dialogs --

    @Test
    void testNestedDialogsCounterTracksAllLevels() {
        try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
            var dialogA = new TestDialog();
            var dialogB = new TestDialog();

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
    void testResetVisibleBlockingDialogCountResetsToZero() {
        try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
            var dialog = new TestDialog();
            dialog.setVisible(true);
            assertThat(BaseDialog.isAnyBlockingDialogVisible()).isTrue();

            BaseDialog.resetVisibleBlockingDialogCount();

            assertThat(BaseDialog.isAnyBlockingDialogVisible()).isFalse();
        }
    }

    // -- notification transitions --

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
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
                new TestDialog().setVisible(true);

                messageCenterMock.verify(() -> MessageCenter.post(
                    argThat(m -> m instanceof DialogVisibilityDidChangeNotification n && n.isVisible())
                ));
            }
        }

        @Test
        void testNotificationNotPostedOnSecondDialogOpen() {
            try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
                var dialogA = new TestDialog();
                var dialogB = new TestDialog();
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
                var dialog = new TestDialog();
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
                var dialogA = new TestDialog();
                var dialogB = new TestDialog();
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


    // -- helpers --

    private static final Point DEFAULT_LOCATION = new Point(100, 100);

    private void configureMockDialog(JDialog dialog) {
        BaseDialogTestHelper.configureMockDialog(dialog, DEFAULT_LOCATION);
    }

    private static class TestDialog extends BaseDialog {

        TestDialog() {
            super("Test Dialog", false);
        }
    }

    private static class TestInformationalDialog extends BaseDialog {

        TestInformationalDialog() {
            super("Info Dialog", false, DialogCategory.INFORMATIONAL);
        }
    }

    // -- informational dialogs do not affect counter --

    @Test
    void testInformationalDialogDoesNotIncrementCounter() {
        try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
            var dialog = new TestInformationalDialog();
            dialog.setVisible(true);
            assertThat(BaseDialog.isAnyBlockingDialogVisible()).isFalse();
        }
    }

    @Test
    void testInformationalDialogDoesNotDecrementCounter() {
        try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
            var dialog = new TestInformationalDialog();
            dialog.setVisible(true);
            dialog.setVisible(false);
            assertThat(BaseDialog.isAnyBlockingDialogVisible()).isFalse();
        }
    }

    // -- mixed informational and blocking dialogs --

    @Test
    void testMixedDialogsOnlyBlockingAffectsCounter() {
        try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d))) {
            var infoDialog = new TestInformationalDialog();
            var blockingDialog = new TestDialog();

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
                var dialog = new TestInformationalDialog();
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
