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
import org.mockito.MockedStatic;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DialogsTest extends UnitTest {

    @BeforeEach
    void setUp() {
        OptionDialogs.setSuppressDialogs(true);
    }

    @AfterEach
    void tearDown() {
        OptionDialogs.setSuppressDialogs(true);
    }

    @Nested
    class WhenNotSuppressed {

        @BeforeEach
        void enableDialogs() {
            OptionDialogs.setSuppressDialogs(false);
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

        private void configureMockDialog(JDialog mockDialog) {
            var mockRootPane = mock(JRootPane.class);
            when(mockDialog.getSize()).thenReturn(new Dimension(200, 100));
            when(mockDialog.getRootPane()).thenReturn(mockRootPane);
            when(mockDialog.getContentPane()).thenReturn(mock(Container.class));
            when(mockRootPane.getInputMap(anyInt())).thenReturn(mock(InputMap.class));
            when(mockRootPane.getActionMap()).thenReturn(mock(ActionMap.class));
        }

        @Test
        void testShowErrorMessageDelegatesToJOptionPane() {
            var toolkit = mock(Toolkit.class);

            try (
                var tkMock = mockStatic(Toolkit.class);
                var geMock = mockStatic(GraphicsEnvironment.class);
                var paneConstruction = mockConstruction(JOptionPane.class);
                var dialogConstruction = mockConstruction(JDialog.class, (dialog, ctx) -> configureMockDialog(dialog))
            ) {
                tkMock.when(Toolkit::getDefaultToolkit).thenReturn(toolkit);
                stubScreenBounds(geMock);

                OptionDialogs.showErrorMessage(null, "Error Title", "Error text");

                verify(toolkit).beep();
                assertThat(paneConstruction.constructed()).hasSize(1);
                verify(dialogConstruction.constructed().get(0)).setVisible(true);
            }
        }

        @Test
        void testShowInfoMessageDelegatesToJOptionPane() {
            try (
                var geMock = mockStatic(GraphicsEnvironment.class);
                var paneConstruction = mockConstruction(JOptionPane.class);
                var dialogConstruction = mockConstruction(JDialog.class, (dialog, ctx) -> configureMockDialog(dialog))
            ) {
                stubScreenBounds(geMock);

                OptionDialogs.showInfoMessage(null, "Info Title", "Info text");

                assertThat(paneConstruction.constructed()).hasSize(1);
                verify(dialogConstruction.constructed().get(0)).setVisible(true);
            }
        }

        @Test
        void testShowInputDialogReturnsUserInput() {
            try (
                var geMock = mockStatic(GraphicsEnvironment.class);
                var ignored = mockConstruction(JOptionPane.class, (pane, ctx) ->
                    when(pane.getInputValue()).thenReturn("user input"));
                var dialogConstruction = mockConstruction(JDialog.class, (dialog, ctx) -> configureMockDialog(dialog))
            ) {
                stubScreenBounds(geMock);

                var result = OptionDialogs.showInputDialog(null, "Input Title", "Enter:");

                assertThat(result).isEqualTo("user input");
            }
        }

        @Test
        void testShowInputDialogReturnsCancelAsNull() {
            try (
                var geMock = mockStatic(GraphicsEnvironment.class);
                var ignored = mockConstruction(JOptionPane.class, (pane, ctx) ->
                    when(pane.getInputValue()).thenReturn(JOptionPane.UNINITIALIZED_VALUE));
                var dialogConstruction = mockConstruction(JDialog.class, (dialog, ctx) -> configureMockDialog(dialog))
            ) {
                stubScreenBounds(geMock);

                var result = OptionDialogs.showInputDialog(null, "Input Title", "Enter:");

                assertThat(result).isNull();
            }
        }

        @Test
        void testShowConfirmDialogTranslatesClosedOptionToCancelForYesNoCancelOption() {
            try (
                var geMock = mockStatic(GraphicsEnvironment.class);
                var ignored = mockConstruction(JOptionPane.class);
                var dialogConstruction = mockConstruction(JDialog.class, (dialog, ctx) -> configureMockDialog(dialog))
            ) {
                stubScreenBounds(geMock);

                var result = OptionDialogs.showConfirmDialog(
                    null, "Title", "Save?",
                    JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE
                );

                assertThat(result).isEqualTo(JOptionPane.CANCEL_OPTION);
            }
        }

        @Test
        void testShowConfirmDialogTranslatesClosedOptionToNoForYesNoOption() {
            try (
                var geMock = mockStatic(GraphicsEnvironment.class);
                var ignored = mockConstruction(JOptionPane.class);
                var dialogConstruction = mockConstruction(JDialog.class, (dialog, ctx) -> configureMockDialog(dialog))
            ) {
                stubScreenBounds(geMock);

                var result = OptionDialogs.showConfirmDialog(
                    null, "Title", "Continue?",
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE
                );

                assertThat(result).isEqualTo(JOptionPane.NO_OPTION);
            }
        }

    }

    @Nested
    class WhenSuppressed {

        @Test
        void testShowConfirmDialogReturnsNoOptionByDefault() {
            var result = OptionDialogs.showConfirmDialog(
                null, "Title", "Confirm?",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE
            );

            assertThat(result).isEqualTo(JOptionPane.NO_OPTION);
        }

        @Test
        void testShowConfirmDialogReturnsSuppressedDefault() {
            var result = OptionDialogs.showConfirmDialog(
                null, "Title", "Confirm?",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                JOptionPane.YES_OPTION
            );

            assertThat(result).isEqualTo(JOptionPane.YES_OPTION);
        }

        @Test
        void testShowErrorMessageDoesNotShowDialog() {
            try (var construction = mockConstruction(JOptionPane.class)) {
                OptionDialogs.showErrorMessage(null, "Title", "Error message");

                assertThat(construction.constructed()).isEmpty();
            }
        }

        @Test
        void testShowInfoMessageDoesNotShowDialog() {
            try (var construction = mockConstruction(JOptionPane.class)) {
                OptionDialogs.showInfoMessage(null, "Title", "Info message");

                assertThat(construction.constructed()).isEmpty();
            }
        }

        @Test
        void testShowInputDialogReturnsNullByDefault() {
            var result = OptionDialogs.showInputDialog(null, "Title", "Enter value:");

            assertThat(result).isNull();
        }

        @Test
        void testShowInputDialogReturnsSuppressedDefault() {
            var result = OptionDialogs.showInputDialog(null, "Title", "Enter value:", "default");

            assertThat(result).isEqualTo("default");
        }

        @Test
        void testShowOptionDialogReturnsClosedOption() {
            var result = OptionDialogs.showOptionDialog(
                null, "Title", "Message",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, new String[]{"OK"}, "OK"
            );

            assertThat(result).isEqualTo(JOptionPane.CLOSED_OPTION);
        }
    }
}
