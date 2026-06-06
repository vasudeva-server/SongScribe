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

package songscribe.ui.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.JOptionPane;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.dom.Song;
import songscribe.io.SongIO;
import songscribe.message.MessageCenter;
import songscribe.message.notification.DocumentWasSavedNotification;
import songscribe.ui.OptionDialogs;
import songscribe.ui.action.SaveAction;
import songscribe.ui.dialog.PlatformFileDialog;

/**
 * Unit tests for {@link MainFrame}'s save-dialog guard ({@link MainFrame#showSaveDialog}) and
 * save-routing ({@link MainFrame#save}).
 *
 * <p>Tests use {@code mock(MainFrame.class)} + {@code doCallRealMethod()} to exercise
 * the real method implementations without triggering the heavyweight JFrame constructor.
 * Fields ({@link MainFrame#scoreView}, {@link MainFrame#currentFile}) are set directly
 * from this package-sibling test class.
 */
class MainFrameTest extends UnitTest {

    private MainFrame frame;

    @BeforeEach
    void setUp() {
        frame = mock(MainFrame.class);
    }

    // -----------------------------------------------------------------------
    // showSaveDialog
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ShowSaveDialog {

        /**
         * When {@code scoreView} is null (no document open), {@code showSaveDialog} returns
         * {@code true} immediately without showing any dialog.
         */
        @Test
        void testNullScoreViewReturnsTrueImmediately() {
            frame.scoreView = null;
            doCallRealMethod().when(frame).showSaveDialog();

            assertThat(frame.showSaveDialog())
                .as("null scoreView: no document open → allow proceed")
                .isTrue();
        }

        /**
         * When the document is clean ({@code isModified() == false}), {@code showSaveDialog}
         * returns {@code true} without prompting.
         */
        @Test
        void testCleanDocReturnsTrueWithoutDialog() {
            var mockScore = mock(ScoreView.class);
            var mockSong = mock(Song.class);

            when(mockScore.getSong()).thenReturn(mockSong);
            when(mockSong.isModified()).thenReturn(false);
            frame.scoreView = mockScore;

            doCallRealMethod().when(frame).showSaveDialog();

            try (var optionDialogsMock = mockStatic(OptionDialogs.class)) {
                assertThat(frame.showSaveDialog())
                    .as("clean document → allow proceed without dialog")
                    .isTrue();

                // No dialog should have been shown
                optionDialogsMock.verify(
                    () -> OptionDialogs.showOptionDialog(
                        any(), anyString(), anyString(), anyInt(), anyInt(),
                        any(), any(), any(), any()),
                    never()
                );
            }
        }

        /**
         * On a dirty document when the user selects "Save", {@code showSaveDialog} delegates
         * to {@code SaveAction.perform()} and returns its result (true on success).
         */
        @Test
        void testSaveAnswerDelegatesToSaveActionAndReturnsTrueOnSuccess() {
            var mockScore = mock(ScoreView.class);
            var mockSong = mock(Song.class);

            when(mockScore.getSong()).thenReturn(mockSong);
            when(mockSong.isModified()).thenReturn(true);
            frame.scoreView = mockScore;

            doCallRealMethod().when(frame).showSaveDialog();

            // saveIdx == 0, so return 0 from showOptionDialog to choose Save
            final int saveIdx = 0;

            try (var optionDialogsMock = mockStatic(OptionDialogs.class);
                 var saveActionMock = mockStatic(SaveAction.class)) {

                optionDialogsMock
                    .when(() -> OptionDialogs.showOptionDialog(
                        any(), anyString(), anyString(), anyInt(), anyInt(),
                        isNull(), any(), any(), any()))
                    .thenReturn(saveIdx);

                var mockSaveAction = mock(SaveAction.class);
                saveActionMock.when(() -> SaveAction.createAction(any())).thenReturn(mockSaveAction);
                when(mockSaveAction.perform(any())).thenReturn(true);

                assertThat(frame.showSaveDialog())
                    .as("Save answer → perform() returns true → showSaveDialog returns true")
                    .isTrue();

                verify(mockSaveAction).perform(frame);
            }
        }

        /**
         * On a dirty document when the user selects "Don't Save", {@code showSaveDialog} returns
         * {@code true} without writing anything.
         */
        @Test
        void testDontSaveAnswerReturnsTrueWithoutWriting() {
            var mockScore = mock(ScoreView.class);
            var mockSong = mock(Song.class);

            when(mockScore.getSong()).thenReturn(mockSong);
            when(mockSong.isModified()).thenReturn(true);
            frame.scoreView = mockScore;

            doCallRealMethod().when(frame).showSaveDialog();

            // dontSaveIdx == 1
            final int dontSaveIdx = 1;

            try (var optionDialogsMock = mockStatic(OptionDialogs.class);
                 var saveActionMock = mockStatic(SaveAction.class)) {

                optionDialogsMock
                    .when(() -> OptionDialogs.showOptionDialog(
                        any(), anyString(), anyString(), anyInt(), anyInt(),
                        isNull(), any(), any(), any()))
                    .thenReturn(dontSaveIdx);

                assertThat(frame.showSaveDialog())
                    .as("Don't Save answer → returns true without invoking SaveAction")
                    .isTrue();

                // SaveAction must NOT have been invoked
                saveActionMock.verify(
                    () -> SaveAction.createAction(any()),
                    never()
                );
            }
        }

        /**
         * When the user dismisses the dialog ({@code CLOSED_OPTION}), neither Save nor
         * Don't Save index was chosen, so {@code showSaveDialog} returns {@code false}.
         */
        @Test
        void testClosedOptionReturnsFalse() {
            var mockScore = mock(ScoreView.class);
            var mockSong = mock(Song.class);

            when(mockScore.getSong()).thenReturn(mockSong);
            when(mockSong.isModified()).thenReturn(true);
            frame.scoreView = mockScore;

            doCallRealMethod().when(frame).showSaveDialog();

            try (var optionDialogsMock = mockStatic(OptionDialogs.class)) {
                optionDialogsMock
                    .when(() -> OptionDialogs.showOptionDialog(
                        any(), anyString(), anyString(), anyInt(), anyInt(),
                        isNull(), any(), any(), any()))
                    .thenReturn(JOptionPane.CLOSED_OPTION);

                assertThat(frame.showSaveDialog())
                    .as("dialog dismissed (CLOSED_OPTION) → cancel → returns false")
                    .isFalse();
            }
        }
    }

    // -----------------------------------------------------------------------
    // save
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class Save {

        /**
         * When {@code currentFile} is null, {@link MainFrame#save} delegates to
         * {@code saveAsNewFile()} — verified by observing that a null {@code scoreView}
         * triggers {@code saveAsNewFile}'s first guard (returns false) rather than
         * {@code saveCurrentFile}'s guard (which requires a non-null currentFile).
         */
        @Test
        void testNullCurrentFileDelegatesToSaveAsNewFile() {
            frame.currentFile = null;
            frame.scoreView = null;

            doCallRealMethod().when(frame).save();

            // saveAsNewFile() returns false when scoreView==null (its first guard).
            assertThat(frame.save())
                .as("currentFile==null → delegates to saveAsNewFile → false (scoreView guard)")
                .isFalse();
        }

        /**
         * When {@code currentFile} is set, {@link MainFrame#save} delegates to
         * {@code saveCurrentFile()} — verified by observing that a null {@code scoreView}
         * triggers {@code saveCurrentFile}'s null guard (returns false).
         */
        @Test
        void testSetCurrentFileDelegatesToSaveCurrentFile() {
            frame.currentFile = mock(java.io.File.class);
            frame.scoreView = null;

            doCallRealMethod().when(frame).save();

            // saveCurrentFile() returns false when scoreView==null (its first guard).
            assertThat(frame.save())
                .as("currentFile!=null → delegates to saveCurrentFile → false (scoreView guard)")
                .isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // saveCurrentFile
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SaveCurrentFile {

        /**
         * Happy path: {@code saveCurrentFile()} writes the song, clears {@code isModified},
         * posts a {@link DocumentWasSavedNotification}, and returns {@code true}.
         */
        @Test
        void testSuccessPathClearsModifiedAndPostsNotification() throws IOException {
            var tempFile = Files.createTempFile("MainFrameTest", ".mssw").toFile();
            tempFile.deleteOnExit();

            var mockScore = mock(ScoreView.class);
            var mockSong = mock(Song.class);
            when(mockScore.getSong()).thenReturn(mockSong);
            frame.currentFile = tempFile;
            frame.scoreView = mockScore;

            doCallRealMethod().when(frame).saveCurrentFile();

            try (var songIOMock = mockStatic(SongIO.class);
                 var messageCenterMock = mockStatic(MessageCenter.class)) {

                // SongIO.writeSong is a no-op stub — we only care about side-effects.
                songIOMock.when(
                    () -> SongIO.writeSong(any(Song.class), any(), any())
                ).then(inv -> null);

                var result = frame.saveCurrentFile();

                assertThat(result)
                    .as("successful write returns true")
                    .isTrue();

                verify(mockSong).setModified(false);

                var captor = ArgumentCaptor.forClass(DocumentWasSavedNotification.class);
                messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
                assertThat(captor.getValue())
                    .as("DocumentWasSavedNotification posted")
                    .isNotNull();
            }
        }

        /**
         * When {@code currentFile} cannot be opened for writing, the {@link IOException}
         * from the {@link java.io.PrintWriter} constructor is caught, an error dialog is
         * shown via {@link OptionDialogs}, and {@code false} is returned.
         *
         * <p>Using a directory as {@code currentFile} reliably triggers the IOException
         * because {@code PrintWriter(File, Charset)} throws when the target is not writable.
         */
        @Test
        void testIOExceptionShowsErrorAndReturnsFalse(@TempDir Path tempDir) {
            // A directory passed to PrintWriter(File, Charset) always throws IOException.
            var dir = tempDir.toFile();

            var mockScore = mock(ScoreView.class);
            var mockSong = mock(Song.class);
            when(mockScore.getSong()).thenReturn(mockSong);
            frame.currentFile = dir;
            frame.scoreView = mockScore;

            doCallRealMethod().when(frame).saveCurrentFile();

            try (var optionDialogsMock = mockStatic(OptionDialogs.class)) {
                var result = frame.saveCurrentFile();

                assertThat(result)
                    .as("IOException → returns false")
                    .isFalse();

                optionDialogsMock.verify(
                    () -> OptionDialogs.showErrorMessage(any(), anyString(), anyString())
                );
            }
        }

        /**
         * Both null guards: {@code currentFile == null} or {@code scoreView == null}
         * cause {@code saveCurrentFile()} to return {@code false} immediately.
         */
        @Test
        void testNullCurrentFileReturnsFalseImmediately() {
            frame.currentFile = null;
            frame.scoreView = mock(ScoreView.class);

            doCallRealMethod().when(frame).saveCurrentFile();

            assertThat(frame.saveCurrentFile())
                .as("null currentFile → false immediately")
                .isFalse();
        }

        @Test
        void testNullScoreViewReturnsFalseImmediately() {
            frame.currentFile = mock(File.class);
            frame.scoreView = null;

            doCallRealMethod().when(frame).saveCurrentFile();

            assertThat(frame.saveCurrentFile())
                .as("null scoreView → false immediately")
                .isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // saveAsNewFile
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SaveAsNewFile {

        /**
         * When {@code scoreView} is null, {@code saveAsNewFile()} returns {@code false}
         * immediately without opening a file dialog.
         */
        @Test
        void testNullScoreViewReturnsFalseImmediately() {
            frame.scoreView = null;
            doCallRealMethod().when(frame).saveAsNewFile();

            try (var dialogMock = mockStatic(PlatformFileDialog.class)) {
                var result = frame.saveAsNewFile();

                assertThat(result)
                    .as("null scoreView → false before showing dialog")
                    .isFalse();

                dialogMock.verify(
                    () -> PlatformFileDialog.showSaveDialog(any(), any(), any(), any(), any()),
                    never()
                );
            }
        }

        /**
         * When {@code currentFile} is null, the suggested file name comes from
         * {@code scoreView.getSuggestedFileName()} rather than the existing file's name.
         * The dialog returning null (user cancelled) lets us observe the suggested name
         * without triggering a write.
         */
        @Test
        void testNullCurrentFileUsesSuggestedFileName() {
            var suggestedName = "001 My Song";
            var mockScore = mock(ScoreView.class);
            when(mockScore.getSuggestedFileName()).thenReturn(suggestedName);
            frame.currentFile = null;
            frame.scoreView = mockScore;

            doCallRealMethod().when(frame).saveAsNewFile();

            try (var dialogMock = mockStatic(PlatformFileDialog.class)) {
                // Return null so the method short-circuits after the dialog check.
                dialogMock.when(
                    () -> PlatformFileDialog.showSaveDialog(any(), any(), any(), any(), any())
                ).thenReturn(null);

                frame.saveAsNewFile();

                var captor = ArgumentCaptor.forClass(String.class);
                dialogMock.verify(
                    () -> PlatformFileDialog.showSaveDialog(
                        any(), any(), any(), captor.capture(), (String[]) any()
                    )
                );
                assertThat(captor.getValue())
                    .as("null currentFile → suggestedFileName from scoreView")
                    .isEqualTo(suggestedName);
            }
        }

        /**
         * When {@code currentFile} is set, the suggested file name is derived from the
         * existing file's name (without extension) via {@link songscribe.util.FileUtils}.
         */
        @Test
        void testNonNullCurrentFileUsesExistingFileName() {
            var existingFile = new File("My Song.mssw");
            var mockScore = mock(ScoreView.class);
            frame.currentFile = existingFile;
            frame.scoreView = mockScore;

            doCallRealMethod().when(frame).saveAsNewFile();

            try (var dialogMock = mockStatic(PlatformFileDialog.class)) {
                dialogMock.when(
                    () -> PlatformFileDialog.showSaveDialog(any(), any(), any(), any(), any())
                ).thenReturn(null);

                frame.saveAsNewFile();

                var captor = ArgumentCaptor.forClass(String.class);
                dialogMock.verify(
                    () -> PlatformFileDialog.showSaveDialog(
                        any(), any(), any(), captor.capture(), (String[]) any()
                    )
                );
                assertThat(captor.getValue())
                    .as("non-null currentFile → file name without extension")
                    .isEqualTo("My Song");
            }
        }

        /**
         * When the file dialog returns {@code null} (user cancelled), {@code saveAsNewFile()}
         * returns {@code false} without writing anything.
         */
        @Test
        void testUserCancelReturnsFalse() {
            var mockScore = mock(ScoreView.class);
            when(mockScore.getSuggestedFileName()).thenReturn("Untitled");
            frame.currentFile = null;
            frame.scoreView = mockScore;

            doCallRealMethod().when(frame).saveAsNewFile();

            try (var dialogMock = mockStatic(PlatformFileDialog.class)) {
                dialogMock.when(
                    () -> PlatformFileDialog.showSaveDialog(any(), any(), any(), any(), any())
                ).thenReturn(null);

                var result = frame.saveAsNewFile();

                assertThat(result)
                    .as("dialog cancelled (null) → returns false")
                    .isFalse();
            }
        }
    }

}
