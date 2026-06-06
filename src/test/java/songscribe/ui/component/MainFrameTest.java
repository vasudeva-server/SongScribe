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
import static org.mockito.ArgumentMatchers.anyBoolean;
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

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.Song;
import songscribe.io.SongIO;
import songscribe.message.MessageCenter;
import songscribe.message.command.NewFileCommand;
import songscribe.message.notification.DocumentWasSavedNotification;
import songscribe.prefs.RecentDocumentsManager;
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

        /**
         * On a successful save, {@code saveAsNewFile()} adds the absolute path of the
         * newly saved file to {@link RecentDocumentsManager}.
         */
        @Test
        void testSuccessfulSaveAddsPathToRecentDocumentsManager() {
            var saveFile = new File("SaveAsTest.mssw");

            var mockScore = mock(ScoreView.class);
            when(mockScore.getSuggestedFileName()).thenReturn("My Song");
            frame.currentFile = null;
            frame.scoreView = mockScore;

            doCallRealMethod().when(frame).saveAsNewFile();
            // setCurrentFile is called after the dialog; let it be a stub (does nothing by default).
            // saveCurrentFile is also called internally; stub it to return true so we can focus on
            // the RecentDocumentsManager side-effect rather than re-testing the save path itself.
            when(frame.saveCurrentFile()).thenReturn(true);

            try (var dialogMock = mockStatic(PlatformFileDialog.class);
                 var recentDocsMock = mockStatic(RecentDocumentsManager.class)) {

                dialogMock.when(
                    () -> PlatformFileDialog.showSaveDialog(any(), any(), any(), any(), any())
                ).thenReturn(saveFile);

                var result = frame.saveAsNewFile();

                assertThat(result)
                    .as("successful save returns true")
                    .isTrue();

                var expectedPath = saveFile.toPath().toAbsolutePath();
                recentDocsMock.verify(
                    () -> RecentDocumentsManager.add(expectedPath)
                );
            }
        }
    }

    // -----------------------------------------------------------------------
    // handleNewFile
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class HandleNewFile {

        /**
         * When {@code showSaveDialog()} returns {@code false} (user cancelled), {@code handleNewFile}
         * aborts without replacing the song — no call to {@code scoreView.setSong()}.
         */
        @Test
        void testAbortWhenSaveDialogReturnsFalse() {
            var mockScore = mock(ScoreView.class);
            frame.scoreView = mockScore;

            // showSaveDialog returns false — abort.
            when(frame.showSaveDialog()).thenReturn(false);
            doCallRealMethod().when(frame).handleNewFile(any());

            frame.handleNewFile(new NewFileCommand());

            verify(mockScore, never()).setSong(any());
        }

        /**
         * When {@code showSaveDialog()} returns {@code true}, {@code handleNewFile} resets
         * {@code currentFile} to null and installs a fresh {@link Song} into the score view.
         */
        @Test
        void testConfirmedSaveResetsCurrentFileAndInstallsFreshSong() {
            var mockScore = mock(ScoreView.class);
            frame.currentFile = mock(File.class);
            frame.scoreView = mockScore;

            when(frame.showSaveDialog()).thenReturn(true);
            doCallRealMethod().when(frame).handleNewFile(any());

            frame.handleNewFile(new NewFileCommand());

            // setCurrentFile(null) should have been called to clear the current file.
            verify(frame).setCurrentFile(null);
            // A fresh Song instance should be installed into the score view.
            verify(mockScore).setSong(any(Song.class));
        }
    }

    // -----------------------------------------------------------------------
    // handleOpenFile(File)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class HandleOpenFile {

        /**
         * When {@code showSaveDialog()} returns {@code false}, {@code handleOpenFile(File)} aborts
         * without calling {@code scoreView.openFile()}.
         */
        @Test
        void testAbortWhenSaveDialogReturnsFalse() {
            var mockScore = mock(ScoreView.class);
            frame.scoreView = mockScore;

            when(frame.showSaveDialog()).thenReturn(false);
            doCallRealMethod().when(frame).handleOpenFile(any(File.class));

            frame.handleOpenFile(new File("some-song.mssw"));

            verify(mockScore, never()).openFile(any(), anyBoolean());
        }

        /**
         * When {@code scoreView} is null (even after save dialog passes), {@code handleOpenFile(File)}
         * aborts without attempting to open the file.
         */
        @Test
        void testAbortWhenScoreViewIsNull() {
            frame.scoreView = null;

            when(frame.showSaveDialog()).thenReturn(true);
            doCallRealMethod().when(frame).handleOpenFile(any(File.class));

            // Should not throw; just returns silently.
            try (var recentDocsMock = mockStatic(RecentDocumentsManager.class)) {
                frame.handleOpenFile(new File("some-song.mssw"));

                recentDocsMock.verify(
                    () -> RecentDocumentsManager.add(any()),
                    never()
                );
            }
        }

        /**
         * On a successful open, the file's absolute path is added to {@link RecentDocumentsManager}.
         */
        @Test
        void testSuccessAddsToRecentDocumentsManager() {
            var openFile = new File("some-song.mssw");
            var mockScore = mock(ScoreView.class);
            frame.scoreView = mockScore;

            when(frame.showSaveDialog()).thenReturn(true);
            when(mockScore.openFile(any(), anyBoolean())).thenReturn(true);
            doCallRealMethod().when(frame).handleOpenFile(any(File.class));

            try (var recentDocsMock = mockStatic(RecentDocumentsManager.class)) {
                frame.handleOpenFile(openFile);

                var expectedPath = openFile.toPath().toAbsolutePath();
                recentDocsMock.verify(() -> RecentDocumentsManager.add(expectedPath));
                recentDocsMock.verify(
                    () -> RecentDocumentsManager.remove(any()),
                    never()
                );
            }
        }

        /**
         * On a failed open, the file's absolute path is removed from {@link RecentDocumentsManager}.
         */
        @Test
        void testFailureRemovesFromRecentDocumentsManager() {
            var openFile = new File("some-song.mssw");
            var mockScore = mock(ScoreView.class);
            frame.scoreView = mockScore;

            when(frame.showSaveDialog()).thenReturn(true);
            when(mockScore.openFile(any(), anyBoolean())).thenReturn(false);
            doCallRealMethod().when(frame).handleOpenFile(any(File.class));

            try (var recentDocsMock = mockStatic(RecentDocumentsManager.class)) {
                frame.handleOpenFile(openFile);

                var expectedPath = openFile.toPath().toAbsolutePath();
                recentDocsMock.verify(() -> RecentDocumentsManager.remove(expectedPath));
                recentDocsMock.verify(
                    () -> RecentDocumentsManager.add(any()),
                    never()
                );
            }
        }
    }

    // -----------------------------------------------------------------------
    // getDisplayName
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GetDisplayName {

        /**
         * When {@code currentFile} is null, {@code getDisplayName()} returns the localized
         * "Untitled" string.
         */
        @Test
        void testNullCurrentFileReturnsUntitled() {
            frame.currentFile = null;
            doCallRealMethod().when(frame).getDisplayName();

            assertThat(frame.getDisplayName())
                .as("null currentFile → localized Untitled")
                .isEqualTo(Strings.get(Strings.DOCUMENT_UNTITLED));
        }

        /**
         * When {@code currentFile} is set, {@code getDisplayName()} returns the file name
         * stripped of its extension.
         */
        @Test
        void testNonNullCurrentFileReturnsNameWithoutExtension() {
            frame.currentFile = new File("My Song.mssw");
            doCallRealMethod().when(frame).getDisplayName();

            assertThat(frame.getDisplayName())
                .as("file with extension → name without extension")
                .isEqualTo("My Song");
        }
    }

}
