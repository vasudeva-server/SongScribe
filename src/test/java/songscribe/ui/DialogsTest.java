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

package songscribe.ui;

import module java.desktop;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DialogsTest extends UnitTest {

    @BeforeEach
    void setUp() {
        Dialogs.setSuppressDialogs(true);
    }

    @AfterEach
    void tearDown() {
        Dialogs.setSuppressDialogs(true);
    }

    @Nested
    class WhenNotSuppressed {

        @BeforeEach
        void enableDialogs() {
            Dialogs.setSuppressDialogs(false);
        }

        private void stubScreenBounds(MockedStatic<GraphicsEnvironment> geMock) {
            var gc = mock(GraphicsConfiguration.class);
            when(gc.getBounds()).thenReturn(new Rectangle(0, 0, 1920, 1080));
            var device = mock(GraphicsDevice.class);
            when(device.getDefaultConfiguration()).thenReturn(gc);
            var env = mock(GraphicsEnvironment.class);
            when(env.getDefaultScreenDevice()).thenReturn(device);
            geMock.when(GraphicsEnvironment::getLocalGraphicsEnvironment).thenReturn(env);
            geMock.when(GraphicsEnvironment::isHeadless).thenReturn(false);
        }

        @Test
        void testShowConfirmDialogDelegatesToJOptionPane() {
            try (var jopMock = mockStatic(JOptionPane.class)) {
                jopMock.when(
                    () -> JOptionPane.showConfirmDialog(
                        any(), any(), any(), anyInt(), anyInt()
                    )
                ).thenReturn(JOptionPane.YES_OPTION);

                var result = Dialogs.showConfirmDialog(
                    null, "Confirm Title", "Confirm?",
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE
                );

                assertThat(result).isEqualTo(JOptionPane.YES_OPTION);
            }
        }

        @Test
        void testShowConfirmDialogTranslatesClosedOptionToCancelForYesNoCancelOption() {
            try (var jopMock = mockStatic(JOptionPane.class)) {
                jopMock.when(
                    () -> JOptionPane.showConfirmDialog(
                        any(), any(), any(), anyInt(), anyInt()
                    )
                ).thenReturn(JOptionPane.CLOSED_OPTION);

                var result = Dialogs.showConfirmDialog(
                    null, "Confirm Title", "Save?",
                    JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE
                );

                assertThat(result).isEqualTo(JOptionPane.CANCEL_OPTION);
            }
        }

        @Test
        void testShowConfirmDialogTranslatesClosedOptionToNoForYesNoOption() {
            try (var jopMock = mockStatic(JOptionPane.class)) {
                jopMock.when(
                    () -> JOptionPane.showConfirmDialog(
                        any(), any(), any(), anyInt(), anyInt()
                    )
                ).thenReturn(JOptionPane.CLOSED_OPTION);

                var result = Dialogs.showConfirmDialog(
                    null, "Confirm Title", "Continue?",
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE
                );

                assertThat(result).isEqualTo(JOptionPane.NO_OPTION);
            }
        }

        private JDialog mockDialogWithRootPane() {
            var mockDialog = mock(JDialog.class);
            var mockRootPane = mock(JRootPane.class);
            when(mockDialog.getSize()).thenReturn(new Dimension(200, 100));
            when(mockDialog.getRootPane()).thenReturn(mockRootPane);
            when(mockRootPane.getInputMap(anyInt())).thenReturn(mock(InputMap.class));
            when(mockRootPane.getActionMap()).thenReturn(mock(ActionMap.class));
            return mockDialog;
        }

        @Test
        void testShowErrorMessageDelegatesToJOptionPane() {
            var toolkit = mock(Toolkit.class);
            var mockDialog = mockDialogWithRootPane();

            try (
                var tkMock = mockStatic(Toolkit.class);
                var geMock = mockStatic(GraphicsEnvironment.class);
                var construction = mockConstruction(JOptionPane.class, (pane, context) ->
                    when(pane.createDialog(any(), anyString())).thenReturn(mockDialog))
            ) {
                tkMock.when(Toolkit::getDefaultToolkit).thenReturn(toolkit);
                stubScreenBounds(geMock);

                Dialogs.showErrorMessage(null, "Error Title", "Error text");

                verify(toolkit).beep();
                assertThat(construction.constructed()).hasSize(1);
                verify(mockDialog).setVisible(true);
            }
        }

        @Test
        void testShowInfoMessageDelegatesToJOptionPane() {
            var mockDialog = mockDialogWithRootPane();

            try (
                var geMock = mockStatic(GraphicsEnvironment.class);
                var construction = mockConstruction(JOptionPane.class, (pane, context) ->
                    when(pane.createDialog(any(), anyString())).thenReturn(mockDialog))
            ) {
                stubScreenBounds(geMock);

                Dialogs.showInfoMessage(null, "Info Title", "Info text");

                assertThat(construction.constructed()).hasSize(1);
                verify(mockDialog).setVisible(true);
            }
        }

        @Test
        void testShowInputDialogDelegatesToJOptionPane() {
            try (var jopMock = mockStatic(JOptionPane.class)) {
                jopMock.when(
                    () -> JOptionPane.showInputDialog(
                        any(), any(), any(), anyInt(), any(), any(), any()
                    )
                ).thenReturn("user input");

                var result = Dialogs.showInputDialog(null, "Input Title", "Enter:");

                assertThat(result).isEqualTo("user input");
            }
        }

        @Test
        void testShowOptionDialogDelegatesToJOptionPane() {
            var options = new String[]{"A", "B"};

            try (var jopMock = mockStatic(JOptionPane.class)) {
                jopMock.when(
                    () -> JOptionPane.showOptionDialog(
                        any(), any(), any(), anyInt(), anyInt(), any(), any(), any()
                    )
                ).thenReturn(1);

                var result = Dialogs.showOptionDialog(
                    null, "Option Title", "Pick one",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, options, options[0]
                );

                assertThat(result).isEqualTo(1);
            }
        }
    }

    @Nested
    class WhenSuppressed {

        @Test
        void testShowConfirmDialogReturnsNoOptionByDefault() {
            var result = Dialogs.showConfirmDialog(
                null, "Title", "Confirm?",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE
            );

            assertThat(result).isEqualTo(JOptionPane.NO_OPTION);
        }

        @Test
        void testShowConfirmDialogReturnsSuppressedDefault() {
            var result = Dialogs.showConfirmDialog(
                null, "Title", "Confirm?",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                JOptionPane.YES_OPTION
            );

            assertThat(result).isEqualTo(JOptionPane.YES_OPTION);
        }

        @Test
        void testShowErrorMessageDoesNotShowDialog() {
            try (var construction = mockConstruction(JOptionPane.class)) {
                Dialogs.showErrorMessage(null, "Title", "Error message");

                assertThat(construction.constructed()).isEmpty();
            }
        }

        @Test
        void testShowInfoMessageDoesNotShowDialog() {
            try (var construction = mockConstruction(JOptionPane.class)) {
                Dialogs.showInfoMessage(null, "Title", "Info message");

                assertThat(construction.constructed()).isEmpty();
            }
        }

        @Test
        void testShowInputDialogReturnsNullByDefault() {
            var result = Dialogs.showInputDialog(null, "Title", "Enter value:");

            assertThat(result).isNull();
        }

        @Test
        void testShowInputDialogReturnsSuppressedDefault() {
            var result = Dialogs.showInputDialog(null, "Title", "Enter value:", "default");

            assertThat(result).isEqualTo("default");
        }

        @Test
        void testShowOptionDialogReturnsClosedOption() {
            var result = Dialogs.showOptionDialog(
                null, "Title", "Message",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, new String[]{"OK"}, "OK"
            );

            assertThat(result).isEqualTo(JOptionPane.CLOSED_OPTION);
        }
    }
}
