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

import java.awt.*;
import java.io.File;

import javax.swing.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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
    class WhenSuppressed {

        @Test
        void testShowInfoMessageDoesNotShowDialog() {
            try (var jopMock = mockStatic(JOptionPane.class)) {
                Dialogs.showInfoMessage(null, "Title", "Info message");

                jopMock.verify(
                    () -> JOptionPane.showMessageDialog(any(), any(), any(), anyInt()),
                    never()
                );
            }
        }

        @Test
        void testShowErrorMessageDoesNotShowDialog() {
            try (var jopMock = mockStatic(JOptionPane.class)) {
                Dialogs.showErrorMessage(null, "Title", "Error message");

                jopMock.verify(
                    () -> JOptionPane.showMessageDialog(any(), any(), any(), anyInt()),
                    never()
                );
            }
        }

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

    @Nested
    class WhenNotSuppressed {

        @BeforeEach
        void enableDialogs() {
            Dialogs.setSuppressDialogs(false);
        }

        @Test
        void testShowInfoMessageDelegatesToJOptionPane() {
            try (var jopMock = mockStatic(JOptionPane.class)) {
                Dialogs.showInfoMessage(null, "Info Title", "Info text");

                jopMock.verify(
                    () -> JOptionPane.showMessageDialog(
                        isNull(), eq("Info text"), eq("Info Title"),
                        eq(JOptionPane.INFORMATION_MESSAGE)
                    )
                );
            }
        }

        @Test
        void testShowErrorMessageDelegatesToJOptionPane() {
            var toolkit = mock(Toolkit.class);

            try (
                var tkMock = mockStatic(Toolkit.class);
                var jopMock = mockStatic(JOptionPane.class)
            ) {
                tkMock.when(Toolkit::getDefaultToolkit).thenReturn(toolkit);

                Dialogs.showErrorMessage(null, "Error Title", "Error text");

                verify(toolkit).beep();
                jopMock.verify(
                    () -> JOptionPane.showMessageDialog(
                        isNull(), eq("Error text"), eq("Error Title"),
                        eq(JOptionPane.ERROR_MESSAGE)
                    )
                );
            }
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
    class ConfirmFileOverwrite {

        @Test
        void testNonExistentFileReturnsTrue() {
            var file = new File("/nonexistent/path/file.txt");

            assertThat(Dialogs.confirmFileOverwrite(null, "Title", file)).isTrue();
        }

        @Test
        void testExistingFileReturnsTrueWhenSuppressed() {
            var file = mock(File.class);
            when(file.exists()).thenReturn(true);
            when(file.getName()).thenReturn("test.txt");

            assertThat(Dialogs.confirmFileOverwrite(null, "Title", file)).isTrue();
        }

        @Test
        void testExistingFileReturnsFalseWhenUserDeclinesAndNotSuppressed() {
            Dialogs.setSuppressDialogs(false);

            var file = mock(File.class);
            when(file.exists()).thenReturn(true);
            when(file.getName()).thenReturn("test.txt");

            try (var jopMock = mockStatic(JOptionPane.class)) {
                jopMock.when(
                    () -> JOptionPane.showConfirmDialog(
                        any(), any(), any(), anyInt(), anyInt()
                    )
                ).thenReturn(JOptionPane.NO_OPTION);

                assertThat(Dialogs.confirmFileOverwrite(null, "Title", file)).isFalse();
            }
        }
    }
}
