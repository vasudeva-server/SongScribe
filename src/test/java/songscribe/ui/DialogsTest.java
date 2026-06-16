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

import songscribe.Strings;
import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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

    @SuppressWarnings("PackageVisibleInnerClass")
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

        private JDialog createMockDialog() {
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
            var mockDialog = createMockDialog();

            try (
                var tkMock = mockStatic(Toolkit.class);
                var geMock = mockStatic(GraphicsEnvironment.class);
                var paneConstruction = mockConstruction(JOptionPane.class, (pane, ctx) -> {
                    when(pane.createDialog(any(), anyString())).thenReturn(mockDialog);
                    when(pane.getComponents()).thenReturn(new Component[0]);
                })
            ) {
                tkMock.when(Toolkit::getDefaultToolkit).thenReturn(toolkit);
                stubScreenBounds(geMock);

                OptionDialogs.showErrorMessage(null, Strings.ALERT_TITLE_FILE_ERROR, Strings.ERROR_FILE_OPEN);

                verify(toolkit).beep();
                assertThat(paneConstruction.constructed()).hasSize(1);
                verify(mockDialog).setVisible(true);
            }
        }

        @Test
        void testShowInfoMessageDelegatesToJOptionPane() {
            var mockDialog = createMockDialog();

            try (
                var geMock = mockStatic(GraphicsEnvironment.class);
                var paneConstruction = mockConstruction(JOptionPane.class, (pane, ctx) -> {
                    when(pane.createDialog(any(), anyString())).thenReturn(mockDialog);
                    when(pane.getComponents()).thenReturn(new Component[0]);
                })
            ) {
                stubScreenBounds(geMock);

                OptionDialogs.showInfoMessage(null, Strings.ALERT_TITLE_INFORMATION, Strings.ALERT_CONVERSION_COMPLETE);

                assertThat(paneConstruction.constructed()).hasSize(1);
                verify(mockDialog).setVisible(true);
            }
        }

        @Test
        void testShowInputDialogReturnsUserInput() {
            var mockDialog = createMockDialog();

            try (
                var geMock = mockStatic(GraphicsEnvironment.class);
                var ignored = mockConstruction(JOptionPane.class, (pane, ctx) -> {
                    when(pane.createDialog(any(), anyString())).thenReturn(mockDialog);
                    when(pane.getComponents()).thenReturn(new Component[0]);
                    when(pane.getInputValue()).thenReturn("user input");
                })
            ) {
                stubScreenBounds(geMock);

                var result = OptionDialogs.showInputDialog(null, Strings.INPUT_TITLE_NUMBER_SONGS, Strings.INPUT_CONVERTER_ENTER_NUMBER);

                assertThat(result).isEqualTo("user input");
            }
        }

        @Test
        void testShowInputDialogReturnsCancelAsNull() {
            var mockDialog = createMockDialog();

            try (
                var geMock = mockStatic(GraphicsEnvironment.class);
                var ignored = mockConstruction(JOptionPane.class, (pane, ctx) -> {
                    when(pane.createDialog(any(), anyString())).thenReturn(mockDialog);
                    when(pane.getComponents()).thenReturn(new Component[0]);
                    when(pane.getInputValue()).thenReturn(JOptionPane.UNINITIALIZED_VALUE);
                })
            ) {
                stubScreenBounds(geMock);

                var result = OptionDialogs.showInputDialog(null, Strings.INPUT_TITLE_NUMBER_SONGS, Strings.INPUT_CONVERTER_ENTER_NUMBER);

                assertThat(result).isNull();
            }
        }

        @Test
        void testShowConfirmDialogReturnsYesOptionWhenPaneReturnsYesOption() {
            var mockDialog = createMockDialog();

            try (
                var geMock = mockStatic(GraphicsEnvironment.class);
                var ignored = mockConstruction(JOptionPane.class, (pane, ctx) -> {
                    when(pane.createDialog(any(), anyString())).thenReturn(mockDialog);
                    when(pane.getComponents()).thenReturn(new Component[0]);
                    when(pane.getValue()).thenReturn(JOptionPane.YES_OPTION);
                })
            ) {
                stubScreenBounds(geMock);

                var result = OptionDialogs.showConfirmDialog(
                    null, Strings.CONFIRM_TITLE_SAVE_CHANGES, Strings.CONFIRM_SAVE_MODIFIED,
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE
                );

                assertThat(result).isEqualTo(JOptionPane.YES_OPTION);
            }
        }

        @Test
        void testShowConfirmDialogTranslatesClosedOptionToCancelForYesNoCancelOption() {
            var mockDialog = createMockDialog();

            try (
                var geMock = mockStatic(GraphicsEnvironment.class);
                var ignored = mockConstruction(JOptionPane.class, (pane, ctx) -> {
                    when(pane.createDialog(any(), anyString())).thenReturn(mockDialog);
                    when(pane.getComponents()).thenReturn(new Component[0]);
                })
            ) {
                stubScreenBounds(geMock);

                var result = OptionDialogs.showConfirmDialog(
                    null, Strings.CONFIRM_TITLE_SAVE_CHANGES, Strings.CONFIRM_SAVE_MODIFIED,
                    JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE
                );

                assertThat(result).isEqualTo(JOptionPane.CANCEL_OPTION);
            }
        }

        @Test
        void testShowConfirmDialogTranslatesClosedOptionToNoForYesNoOption() {
            var mockDialog = createMockDialog();

            try (
                var geMock = mockStatic(GraphicsEnvironment.class);
                var ignored = mockConstruction(JOptionPane.class, (pane, ctx) -> {
                    when(pane.createDialog(any(), anyString())).thenReturn(mockDialog);
                    when(pane.getComponents()).thenReturn(new Component[0]);
                })
            ) {
                stubScreenBounds(geMock);

                var result = OptionDialogs.showConfirmDialog(
                    null, Strings.CONFIRM_TITLE_SAVE_CHANGES, Strings.CONFIRM_SAVE_MODIFIED,
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE
                );

                assertThat(result).isEqualTo(JOptionPane.NO_OPTION);
            }
        }

    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class WhenSuppressed {

        @Test
        void testShowConfirmDialogReturnsNoOptionByDefault() {
            var result = OptionDialogs.showConfirmDialog(
                null, Strings.CONFIRM_TITLE_SAVE_CHANGES, Strings.CONFIRM_SAVE_MODIFIED,
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE
            );

            assertThat(result).isEqualTo(JOptionPane.NO_OPTION);
        }

        @Test
        void testShowConfirmDialogReturnsSuppressedDefault() {
            var result = OptionDialogs.showConfirmDialog(
                null, Strings.CONFIRM_TITLE_SAVE_CHANGES, Strings.CONFIRM_SAVE_MODIFIED,
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                JOptionPane.YES_OPTION
            );

            assertThat(result).isEqualTo(JOptionPane.YES_OPTION);
        }

        @Test
        void testShowErrorMessageDoesNotShowDialog() {
            try (var construction = mockConstruction(JOptionPane.class)) {
                OptionDialogs.showErrorMessage(null, Strings.ALERT_TITLE_FILE_ERROR, Strings.ERROR_FILE_OPEN);

                assertThat(construction.constructed()).isEmpty();
            }
        }

        @Test
        void testShowInfoMessageDoesNotShowDialog() {
            try (var construction = mockConstruction(JOptionPane.class)) {
                OptionDialogs.showInfoMessage(null, Strings.ALERT_TITLE_INFORMATION, Strings.ALERT_CONVERSION_COMPLETE);

                assertThat(construction.constructed()).isEmpty();
            }
        }

        @Test
        void testShowInputDialogReturnsNullByDefault() {
            var result = OptionDialogs.showInputDialog(null, Strings.INPUT_TITLE_NUMBER_SONGS, Strings.INPUT_CONVERTER_ENTER_NUMBER);

            assertThat(result).isNull();
        }

        @Test
        void testShowInputDialogReturnsSuppressedDefault() {
            var result = OptionDialogs.showInputDialog(null, Strings.INPUT_TITLE_NUMBER_SONGS, Strings.INPUT_CONVERTER_ENTER_NUMBER, "default");

            assertThat(result).isEqualTo("default");
        }

        @Test
        void testShowOptionDialogReturnsClosedOption() {
            var result = OptionDialogs.showOptionDialog(
                null,
                Strings.CONFIRM_TITLE_SAVE_CHANGES,
                Strings.CONFIRM_SAVE_MODIFIED,
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, new String[]{"OK"}, "OK"
            );

            assertThat(result).isEqualTo(JOptionPane.CLOSED_OPTION);
        }
    }
}
