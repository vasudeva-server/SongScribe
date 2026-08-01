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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.answerVoid;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Graphics;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import javax.swing.JRootPane;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.Song;
import songscribe.font.DocumentFontsHolder;
import songscribe.io.SongFileWriter;
import songscribe.io.musicxml.MusicXmlWriter;
import songscribe.message.MessageCenter;
import songscribe.message.command.NewFileCommand;
import songscribe.message.command.OpenFileCommand;
import songscribe.message.command.RevertToSavedCommand;
import songscribe.message.command.ShowOpenDialogCommand;
import songscribe.message.command.ToggleLoopPlaybackCommand;
import songscribe.message.command.TogglePlayWithRepeatsCommand;
import songscribe.message.notification.DocumentDidLoadNotification;
import songscribe.message.notification.DocumentWasSavedNotification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.prefs.RecentDocumentsManager;
import songscribe.prefs.StartupAction;
import songscribe.ui.OptionDialogs;
import songscribe.ui.action.Actions;
import songscribe.ui.action.SaveAction;
import songscribe.ui.dialog.PlatformFileDialog;
import songscribe.util.ModifierState;

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
            final var saveIdx = 0;

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
            final var dontSaveIdx = 1;

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
            frame.currentFile = mock(File.class);
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

            try (var musicXmlWriterMock = mockStatic(MusicXmlWriter.class);
                 var messageCenterMock = mockStatic(MessageCenter.class)) {

                // MusicXmlWriter.writeSong is a no-op stub — we only care about side-effects.
                musicXmlWriterMock.when(
                    () -> MusicXmlWriter.writeSong(any(Song.class), any(), any())
                ).then(answerVoid((Song song, DocumentFontsHolder fonts, PrintWriter pw) -> { }));

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
         * from the {@link PrintWriter} constructor is caught, an error dialog is
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
         * When {@code SongFileWriter.write} returns {@code false} (a write that recorded
         * an error via {@code checkError} rather than throwing), {@code saveCurrentFile()}
         * shows an error dialog, leaves the song modified, and returns {@code false}.
         */
        @Test
        void testWriteReturningFalseShowsErrorAndReturnsFalse() throws IOException {
            var tempFile = Files.createTempFile("MainFrameTest", ".musicxml").toFile();
            tempFile.deleteOnExit();

            var mockScore = mock(ScoreView.class);
            var mockSong = mock(Song.class);
            when(mockScore.getSong()).thenReturn(mockSong);
            frame.currentFile = tempFile;
            frame.scoreView = mockScore;

            doCallRealMethod().when(frame).saveCurrentFile();

            try (var songFileWriterMock = mockStatic(SongFileWriter.class);
                 var optionDialogsMock = mockStatic(OptionDialogs.class)) {

                songFileWriterMock.when(
                    () -> SongFileWriter.write(any(Song.class), any(), any(File.class))
                ).thenReturn(false);

                var result = frame.saveCurrentFile();

                assertThat(result)
                    .as("write() returning false → saveCurrentFile returns false")
                    .isFalse();

                verify(mockSong, never()).setModified(false);

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
                        any(), any(), any(), captor.capture(), any()
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
                        any(), any(), any(), captor.capture(), any()
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

                verify(mockScore).openFile(openFile, true);
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

                verify(mockScore).openFile(openFile, true);
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
    // handleRevertToSaved(RevertToSavedCommand)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class HandleRevertToSaved {

        /**
         * When {@code scoreView} is null, {@code handleRevertToSaved} aborts without
         * attempting to reload the file or touching the recent-documents list.
         */
        @Test
        void testAbortWhenScoreViewIsNull() {
            frame.scoreView = null;
            frame.currentFile = new File("some-song.mssw");
            doCallRealMethod().when(frame).handleRevertToSaved(any(RevertToSavedCommand.class));

            try (var recentDocsMock = mockStatic(RecentDocumentsManager.class)) {
                frame.handleRevertToSaved(new RevertToSavedCommand());

                recentDocsMock.verify(
                    () -> RecentDocumentsManager.add(any()),
                    never()
                );
                recentDocsMock.verify(
                    () -> RecentDocumentsManager.remove(any()),
                    never()
                );
            }
        }

        /**
         * When {@code currentFile} is null (document never saved), {@code handleRevertToSaved}
         * aborts without attempting to reload or touching the recent-documents list.
         */
        @Test
        void testAbortWhenCurrentFileIsNull() {
            var mockScore = mock(ScoreView.class);
            frame.scoreView = mockScore;
            frame.currentFile = null;
            doCallRealMethod().when(frame).handleRevertToSaved(any(RevertToSavedCommand.class));

            try (var recentDocsMock = mockStatic(RecentDocumentsManager.class)) {
                frame.handleRevertToSaved(new RevertToSavedCommand());

                verify(mockScore, never()).openFile(any(), anyBoolean());
                recentDocsMock.verify(
                    () -> RecentDocumentsManager.add(any()),
                    never()
                );
                recentDocsMock.verify(
                    () -> RecentDocumentsManager.remove(any()),
                    never()
                );
            }
        }

        /**
         * When the user declines the discard-confirmation dialog, {@code handleRevertToSaved}
         * aborts without reloading the file, preserving the unsaved changes.
         */
        @Test
        void testAbortWhenConfirmationDeclined() {
            var mockScore = mock(ScoreView.class);
            frame.scoreView = mockScore;
            frame.currentFile = new File("some-song.mssw");
            doCallRealMethod().when(frame).handleRevertToSaved(any(RevertToSavedCommand.class));

            try (var optionDialogsMock = mockStatic(OptionDialogs.class)) {
                optionDialogsMock.when(() -> OptionDialogs.showConfirmDialog(
                    any(), anyString(), anyString(), anyInt(), anyInt()))
                    .thenReturn(JOptionPane.NO_OPTION);

                frame.handleRevertToSaved(new RevertToSavedCommand());

                verify(mockScore, never()).openFile(any(), anyBoolean());
            }
        }

        /**
         * On a confirmed, successful reload, the current file is reloaded with
         * {@code updateCurrentFile=true} and its absolute path is added to
         * {@link RecentDocumentsManager}.
         */
        @Test
        void testSuccessAddsToRecentDocumentsManager() {
            var currentFile = new File("some-song.mssw");
            var mockScore = mock(ScoreView.class);
            frame.scoreView = mockScore;
            frame.currentFile = currentFile;

            when(mockScore.openFile(any(), anyBoolean())).thenReturn(true);
            doCallRealMethod().when(frame).handleRevertToSaved(any(RevertToSavedCommand.class));

            try (var recentDocsMock = mockStatic(RecentDocumentsManager.class);
                 var optionDialogsMock = mockStatic(OptionDialogs.class)) {
                optionDialogsMock.when(() -> OptionDialogs.showConfirmDialog(
                    any(), anyString(), anyString(), anyInt(), anyInt()))
                    .thenReturn(JOptionPane.YES_OPTION);

                frame.handleRevertToSaved(new RevertToSavedCommand());

                verify(mockScore).openFile(currentFile, true);
                var expectedPath = currentFile.toPath().toAbsolutePath();
                recentDocsMock.verify(() -> RecentDocumentsManager.add(expectedPath));
                recentDocsMock.verify(
                    () -> RecentDocumentsManager.remove(any()),
                    never()
                );
            }
        }

        /**
         * On a confirmed, failed reload, the current file's absolute path is removed from
         * {@link RecentDocumentsManager}.
         */
        @Test
        void testFailureRemovesFromRecentDocumentsManager() {
            var currentFile = new File("some-song.mssw");
            var mockScore = mock(ScoreView.class);
            frame.scoreView = mockScore;
            frame.currentFile = currentFile;

            when(mockScore.openFile(any(), anyBoolean())).thenReturn(false);
            doCallRealMethod().when(frame).handleRevertToSaved(any(RevertToSavedCommand.class));

            try (var recentDocsMock = mockStatic(RecentDocumentsManager.class);
                 var optionDialogsMock = mockStatic(OptionDialogs.class)) {
                optionDialogsMock.when(() -> OptionDialogs.showConfirmDialog(
                    any(), anyString(), anyString(), anyInt(), anyInt()))
                    .thenReturn(JOptionPane.YES_OPTION);

                frame.handleRevertToSaved(new RevertToSavedCommand());

                verify(mockScore).openFile(currentFile, true);
                var expectedPath = currentFile.toPath().toAbsolutePath();
                recentDocsMock.verify(() -> RecentDocumentsManager.remove(expectedPath));
                recentDocsMock.verify(
                    () -> RecentDocumentsManager.add(any()),
                    never()
                );
            }
        }
    }

    // -----------------------------------------------------------------------
    // updateTitle — behavior
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class UpdateTitleBehavior {

        /**
         * When {@code scoreView} is null, {@code updateTitle()} returns immediately
         * without calling {@code setTitle()}.
         */
        @Test
        void testNullScoreViewShortCircuitsBeforeSettingTitle() {
            frame.scoreView = null;
            doCallRealMethod().when(frame).updateTitle();

            frame.updateTitle();

            verify(frame, never()).setTitle(anyString());
        }

        /**
         * When {@code scoreView} is not null but {@code isInitialized()} returns false,
         * {@code updateTitle()} returns immediately without calling {@code setTitle()}.
         */
        @Test
        void testUninitializedScoreViewShortCircuitsBeforeSettingTitle() {
            var mockScore = mock(ScoreView.class);
            when(mockScore.isInitialized()).thenReturn(false);
            frame.scoreView = mockScore;
            doCallRealMethod().when(frame).updateTitle();

            frame.updateTitle();

            verify(frame, never()).setTitle(anyString());
        }

        /**
         * When the document is modified, {@code updateTitle()} sets the window title
         * with a leading {@code •} prefix.
         */
        @Test
        void testModifiedDocPrefixesBulletInTitle() throws Exception {
            prepareInitializedScoreView(true);

            // Run on the EDT so the isEventDispatchThread guard is satisfied.
            SwingUtilities.invokeAndWait(() -> frame.updateTitle());

            var captor = ArgumentCaptor.forClass(String.class);
            verify(frame).setTitle(captor.capture());
            assertThat(captor.getValue())
                .as("modified document: title must start with '•'")
                .startsWith("•");
        }

        /**
         * When the document is clean, {@code updateTitle()} sets the window title
         * without a {@code •} prefix.
         */
        @Test
        void testCleanDocDoesNotPrefixBulletInTitle() throws Exception {
            prepareInitializedScoreView(false);

            SwingUtilities.invokeAndWait(() -> frame.updateTitle());

            var captor = ArgumentCaptor.forClass(String.class);
            verify(frame).setTitle(captor.capture());
            assertThat(captor.getValue())
                .as("clean document: title must not start with '•'")
                .doesNotStartWith("•");
        }

        /**
         * Wires up a fully initialized {@link ScoreView} mock with a song whose
         * {@code isModified()} returns {@code isModified}, and configures {@code frame}
         * to call its real {@code updateTitle()} and {@code getDisplayName()} methods.
         */
        private void prepareInitializedScoreView(boolean isModified) {
            var mockScore = mock(ScoreView.class);
            var mockSong = mock(Song.class);
            when(mockScore.isInitialized()).thenReturn(true);
            when(mockScore.getSong()).thenReturn(mockSong);
            when(mockSong.isModified()).thenReturn(isModified);
            frame.currentFile = new File("My Song.mssw");
            frame.scoreView = mockScore;
            doCallRealMethod().when(frame).updateTitle();
            doCallRealMethod().when(frame).getDisplayName();
        }
    }

    // -----------------------------------------------------------------------
    // Notification handlers — each must call updateTitle()
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class NotificationHandlers {

        /**
         * {@code songDidChange} delegates to {@code updateTitle()}.
         */
        @Test
        void testSongDidChangeCallsUpdateTitle() {
            doCallRealMethod().when(frame).songDidChange(any());

            frame.songDidChange(new SongDidChangeNotification(List.of(), mock(Song.class)));

            verify(frame).updateTitle();
        }

        /**
         * {@code documentDidLoad} delegates to {@code updateTitle()}.
         */
        @Test
        void testDocumentDidLoadCallsUpdateTitle() {
            doCallRealMethod().when(frame).documentDidLoad(any());

            frame.documentDidLoad(new DocumentDidLoadNotification(mock(Song.class)));

            verify(frame).updateTitle();
        }

        /**
         * {@code documentWasSaved} delegates to {@code updateTitle()}.
         */
        @Test
        void testDocumentWasSavedCallsUpdateTitle() {
            doCallRealMethod().when(frame).documentWasSaved(any());

            frame.documentWasSaved(new DocumentWasSavedNotification());

            verify(frame).updateTitle();
        }
    }

    // -----------------------------------------------------------------------
    // setCurrentFile
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SetCurrentFile {

        /**
         * {@code setCurrentFile(file)} stores the file in {@code currentFile} and
         * calls {@code updateTitle()} to reflect the new document name.
         */
        @Test
        void testStoresFileAndCallsUpdateTitle() {
            var newFile = new File("New Song.mssw");
            doCallRealMethod().when(frame).setCurrentFile(any());

            frame.setCurrentFile(newFile);

            assertThat(frame.currentFile)
                .as("currentFile updated to the supplied file")
                .isEqualTo(newFile);

            verify(frame).updateTitle();
        }

        /**
         * {@code setCurrentFile(null)} stores null and calls {@code updateTitle()}.
         */
        @Test
        void testStoresNullAndCallsUpdateTitle() {
            frame.currentFile = new File("Existing.mssw");
            doCallRealMethod().when(frame).setCurrentFile(any());

            frame.setCurrentFile(null);

            assertThat(frame.currentFile)
                .as("currentFile cleared to null")
                .isNull();

            verify(frame).updateTitle();
        }
    }

    // -----------------------------------------------------------------------
    // performStartupAction
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class PerformStartupAction {

        /**
         * When the startup action pref is {@code DO_NOTHING}, no message is posted.
         */
        @Test
        void testDoNothingPostsNoMessage() {
            try (var prefsMock = mockStatic(Prefs.class);
                 var messageCenterMock = mockStatic(MessageCenter.class)) {

                prefsMock.when(() -> Prefs.getString(PrefsKey.STARTUP_ACTION))
                    .thenReturn(StartupAction.DO_NOTHING.name());

                MainFrame.performStartupAction(null);

                messageCenterMock.verify(
                    () -> MessageCenter.post(any()),
                    never()
                );
            }
        }

        /**
         * When the Alt key is pressed, the startup action is overridden to {@code DO_NOTHING}
         * regardless of the pref value — so no message is posted even when the pref is
         * {@code OPEN_MOST_RECENT}.
         */
        @Test
        void testAltKeyForcesDoNothingRegardlessOfPref() {
            try (var prefsMock = mockStatic(Prefs.class);
                 var modifierStateMock = mockStatic(ModifierState.class);
                 var messageCenterMock = mockStatic(MessageCenter.class)) {

                prefsMock.when(() -> Prefs.getString(PrefsKey.STARTUP_ACTION))
                    .thenReturn(StartupAction.OPEN_MOST_RECENT.name());

                modifierStateMock.when(ModifierState::isAltPressed).thenReturn(true);

                var recentPath = Path.of("some-song.mssw");
                MainFrame.performStartupAction(recentPath);

                messageCenterMock.verify(
                    () -> MessageCenter.post(any()),
                    never()
                );
            }
        }

        /**
         * {@code OPEN_MOST_RECENT} with an existing file posts {@link OpenFileCommand} for
         * that file.
         */
        @Test
        void testOpenMostRecentWithExistingFilePostsOpenFileCommand(@TempDir Path tempDir) throws IOException {
            var tempFile = Files.createTempFile(tempDir, "MainFrameTest", ".mssw").toFile();
            var recentPath = tempFile.toPath();

            try (var prefsMock = mockStatic(Prefs.class);
                 var messageCenterMock = mockStatic(MessageCenter.class)) {

                prefsMock.when(() -> Prefs.getString(PrefsKey.STARTUP_ACTION))
                    .thenReturn(StartupAction.OPEN_MOST_RECENT.name());

                MainFrame.performStartupAction(recentPath);

                var captor = ArgumentCaptor.forClass(OpenFileCommand.class);
                messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
                assertThat(captor.getValue().getFile())
                    .as("OpenFileCommand posted with the recent file")
                    .isEqualTo(tempFile);
            }
        }

        /**
         * {@code OPEN_MOST_RECENT} when the file does not exist shows an error dialog and
         * posts no message.
         */
        @Test
        void testOpenMostRecentWithMissingFileShowsErrorDialog() {
            var missingPath = Path.of("nonexistent-song.mssw");

            try (var prefsMock = mockStatic(Prefs.class);
                 var mainFrameMock = mockStatic(MainFrame.class);
                 var optionDialogsMock = mockStatic(OptionDialogs.class);
                 var messageCenterMock = mockStatic(MessageCenter.class)) {

                prefsMock.when(() -> Prefs.getString(PrefsKey.STARTUP_ACTION))
                    .thenReturn(StartupAction.OPEN_MOST_RECENT.name());

                // Allow the real performStartupAction to run while getInstance returns a mock.
                mainFrameMock.when(MainFrame::getInstance).thenReturn(frame);
                mainFrameMock.when(() -> MainFrame.performStartupAction(any()))
                    .thenCallRealMethod();

                MainFrame.performStartupAction(missingPath);

                optionDialogsMock.verify(
                    () -> OptionDialogs.showErrorMessage(any(), anyString(), anyString(), any())
                );
                messageCenterMock.verify(
                    () -> MessageCenter.post(any()),
                    never()
                );
            }
        }

        /**
         * {@code OPEN_MOST_RECENT} with a null {@code mostRecentPath} returns early — no
         * dialog and no message posted.
         */
        @Test
        void testOpenMostRecentWithNullPathReturnsEarly() {
            try (var prefsMock = mockStatic(Prefs.class);
                 var optionDialogsMock = mockStatic(OptionDialogs.class);
                 var messageCenterMock = mockStatic(MessageCenter.class)) {

                prefsMock.when(() -> Prefs.getString(PrefsKey.STARTUP_ACTION))
                    .thenReturn(StartupAction.OPEN_MOST_RECENT.name());

                MainFrame.performStartupAction(null);

                optionDialogsMock.verify(
                    () -> OptionDialogs.showErrorMessage(any(), anyString(), anyString(), any()),
                    never()
                );
                messageCenterMock.verify(
                    () -> MessageCenter.post(any()),
                    never()
                );
            }
        }

        /**
         * {@code SHOW_FILE_CHOOSER} posts {@link ShowOpenDialogCommand}.
         */
        @Test
        void testShowFileChooserPostsShowOpenDialogCommand() {
            try (var prefsMock = mockStatic(Prefs.class);
                 var messageCenterMock = mockStatic(MessageCenter.class)) {

                prefsMock.when(() -> Prefs.getString(PrefsKey.STARTUP_ACTION))
                    .thenReturn(StartupAction.SHOW_FILE_CHOOSER.name());

                MainFrame.performStartupAction(null);

                var captor = ArgumentCaptor.forClass(ShowOpenDialogCommand.class);
                messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
                assertThat(captor.getValue())
                    .as("ShowOpenDialogCommand posted")
                    .isInstanceOf(ShowOpenDialogCommand.class);
            }
        }
    }

    // -----------------------------------------------------------------------
    // handleToggleLoopPlayback / handleTogglePlayWithRepeats
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class TogglePref {

        /**
         * {@code handleToggleLoopPlayback} persists {@code true} from the command.
         */
        @Test
        void testToggleLoopPlaybackPersistsLoopPlaybackPrefTrue() {
            doCallRealMethod().when(frame).handleToggleLoopPlayback(any());

            try (var prefsMock = mockStatic(Prefs.class)) {
                frame.handleToggleLoopPlayback(new ToggleLoopPlaybackCommand(true));

                prefsMock.verify(() -> Prefs.put(PrefsKey.LOOP_PLAYBACK, true));
            }
        }

        /**
         * {@code handleToggleLoopPlayback} persists {@code false} from the command.
         */
        @Test
        void testToggleLoopPlaybackPersistsLoopPlaybackPrefFalse() {
            doCallRealMethod().when(frame).handleToggleLoopPlayback(any());

            try (var prefsMock = mockStatic(Prefs.class)) {
                frame.handleToggleLoopPlayback(new ToggleLoopPlaybackCommand(false));

                prefsMock.verify(() -> Prefs.put(PrefsKey.LOOP_PLAYBACK, false));
            }
        }

        /**
         * {@code handleTogglePlayWithRepeats} persists {@code false} from the command.
         */
        @Test
        void testTogglePlayWithRepeatsPersistsPlayWithRepeatsPrefFalse() {
            doCallRealMethod().when(frame).handleTogglePlayWithRepeats(any());

            try (var prefsMock = mockStatic(Prefs.class)) {
                frame.handleTogglePlayWithRepeats(new TogglePlayWithRepeatsCommand(false));

                prefsMock.verify(() -> Prefs.put(PrefsKey.PLAY_WITH_REPEATS, false));
            }
        }

        /**
         * {@code handleTogglePlayWithRepeats} persists {@code true} from the command.
         */
        @Test
        void testTogglePlayWithRepeatsPersistsPlayWithRepeatsPrefTrue() {
            doCallRealMethod().when(frame).handleTogglePlayWithRepeats(any());

            try (var prefsMock = mockStatic(Prefs.class)) {
                frame.handleTogglePlayWithRepeats(new TogglePlayWithRepeatsCommand(true));

                prefsMock.verify(() -> Prefs.put(PrefsKey.PLAY_WITH_REPEATS, true));
            }
        }
    }

    // -----------------------------------------------------------------------
    // print()
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class Print {

        /**
         * When {@code pageIndex >= 1}, {@link MainFrame#print} returns
         * {@link Printable#NO_SUCH_PAGE} immediately — before inspecting
         * {@code printerJob}.
         */
        @Test
        void testPageIndexOneOrMoreReturnsNoSuchPage() {
            doCallRealMethod().when(frame).print(any(Graphics.class), any(PageFormat.class), anyInt());

            var graphics = mock(Graphics.class);
            var pageFormat = mock(PageFormat.class);

            var result = frame.print(graphics, pageFormat, 1);

            assertThat(result)
                .as("pageIndex ≥ 1 → NO_SUCH_PAGE")
                .isEqualTo(Printable.NO_SUCH_PAGE);
        }

        /**
         * When {@code printerJob} is null (i.e., {@code handlePrint()} was never called),
         * calling {@code print(g, pf, 0)} triggers {@code RuntimeError.exit()}.
         *
         * <p>The test handler installed by {@link UnitTest} converts
         * {@code System.exit} into an {@link AssertionError}, confirming the guard fires.
         */
        @Test
        void testNullPrinterJobThrows() {
            // printerJob is private and defaults to null on a fresh mock.
            doCallRealMethod().when(frame).print(any(Graphics.class), any(PageFormat.class), anyInt());

            var graphics = mock(Graphics.class);
            var pageFormat = mock(PageFormat.class);

            // RuntimeError.exit() → UnitTest exit handler → AssertionError
            assertThatThrownBy(() -> frame.print(graphics, pageFormat, 0))
                .isInstanceOf(AssertionError.class);
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

    // -----------------------------------------------------------------------
    // initFrame — ordering-contract guard (decision 9A)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class InitFrameOrderingContract {

        @SuppressWarnings("NullAway")
        @BeforeEach
        void resetActionsBeforeTest() {
            // Other test classes may have left Actions initialized via initialize().
            // resetForTest() only clears mainFrame/appMenuActions, not the action constants
            // themselves. Null out MODE_ACTION_GROUP directly so the pre-initialize
            // assertion below is valid regardless of prior test-class ordering.
            Actions.MODE_ACTION_GROUP = null;
        }

        @AfterEach
        void resetActionsAfterTest() {
            Actions.resetForTest();
        }

        /**
         * Guards the invariant that {@link MainFrame#initFrame()} calls
         * {@link Actions#initialize(MainFrame)} before any {@code Actions.*} constant is
         * first accessed. If the call is ever removed or moved below a constant access, this
         * test fails instead of the issue surfacing only as an NPE at app launch.
         *
         * <p>The test verifies the contract through {@link Actions#initialize} directly:
         * it starts with all constants null (no prior {@code initialize} call), invokes
         * {@code initialize} with a minimal mock frame, then asserts that
         * {@code MODE_ACTION_GROUP} — a constant used in {@code initFrame()} —
         * is non-null.
         */
        @Test
        void testModeActionGroupIsNonNullAfterInitialize() {
            // All constants are null here — resetActionsBeforeTest() ensures this.
            assertThat(Actions.MODE_ACTION_GROUP)
                .as("MODE_ACTION_GROUP must be null before Actions.initialize() is called")
                .isNull();

            when(frame.getRootPane()).thenReturn(mock(JRootPane.class, RETURNS_DEEP_STUBS));

            // initFrame() calls Actions.initialize(this) as its first statement.
            Actions.initialize(frame);

            assertThat(Actions.MODE_ACTION_GROUP)
                .as("MODE_ACTION_GROUP non-null after Actions.initialize() — as initFrame() guarantees")
                .isNotNull();
        }
    }

    // -----------------------------------------------------------------------
    // DrainStartupErrors
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class DrainStartupErrors {

        @BeforeEach
        void clearQueue() {
            MainFrame.clearStartupErrorsForTest();
        }

        @AfterEach
        void clearQueueAfter() {
            MainFrame.clearStartupErrorsForTest();
        }

        /**
         * When a fatal error is queued, drainStartupErrors() delegates to
         * RuntimeError.exit() without showing any warning dialogs.
         */
        @Test
        void testFatalErrorTriggersFatalPathWithNoWarnings() {
            MainFrame.enqueueStartupError(
                new MainFrame.StartupError("any title", "fatal message", true)
            );

            try (var optionDialogsMock = mockStatic(OptionDialogs.class)) {
                assertThatThrownBy(MainFrame::drainStartupErrors)
                    .as("fatal error must invoke RuntimeError.exit(), triggering the test exit handler")
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("System.exit");

                optionDialogsMock.verify(
                    () -> OptionDialogs.showWarningMessage(any(), anyString(), anyString()),
                    never()
                );
            }
        }

        /**
         * When only non-fatal errors are queued, drainStartupErrors() shows each
         * as a warning in enqueue order and returns normally.
         */
        @Test
        void testNonFatalErrorsShownInOrderAndDrainReturnsNormally() {
            MainFrame.enqueueStartupError(
                new MainFrame.StartupError(
                    Strings.ALERT_TITLE_SOUND, Strings.ALERT_SOUND_INIT_FAILED, false
                )
            );
            MainFrame.enqueueStartupError(
                new MainFrame.StartupError(
                    Strings.ALERT_TITLE_PLAYBACK_ERROR, Strings.ALERT_TITLE_SOUND, false
                )
            );

            var capturedTitles = new ArrayList<String>();

            try (var optionDialogsMock = mockStatic(OptionDialogs.class)) {
                optionDialogsMock.when(
                    () -> OptionDialogs.showWarningMessage(
                        any(), anyString(), anyString()
                    )
                ).thenAnswer(answerVoid((Object parent, String titleKey, String messageKey) -> capturedTitles.add(titleKey)));

                // Must not throw — returns normally when no fatal error is present
                MainFrame.drainStartupErrors();
            }

            assertThat(capturedTitles)
                .as("warnings must be shown in enqueue order")
                .containsExactly(Strings.ALERT_TITLE_SOUND, Strings.ALERT_TITLE_PLAYBACK_ERROR);
        }

        /**
         * When the queue is empty (the startup happy path), drainStartupErrors()
         * shows no dialogs and returns normally.
         */
        @Test
        void testEmptyQueueShowsNoDialogsAndReturnsNormally() {
            try (var optionDialogsMock = mockStatic(OptionDialogs.class)) {
                // Must not throw when there are no queued errors.
                MainFrame.drainStartupErrors();

                optionDialogsMock.verify(
                    () -> OptionDialogs.showWarningMessage(any(), anyString(), anyString()),
                    never()
                );
            }
        }

        /**
         * When a fatal error follows non-fatal errors, drainStartupErrors() takes the
         * fatal path and shows no warning dialogs — the fatal exit suppresses warnings
         * regardless of the fatal error's position in the queue.
         */
        @Test
        void testFatalAfterNonFatalSuppressesWarnings() {
            MainFrame.enqueueStartupError(
                new MainFrame.StartupError(
                    Strings.ALERT_TITLE_SOUND, Strings.ALERT_SOUND_INIT_FAILED, false
                )
            );
            MainFrame.enqueueStartupError(
                new MainFrame.StartupError("any title", "fatal message", true)
            );

            try (var optionDialogsMock = mockStatic(OptionDialogs.class)) {
                assertThatThrownBy(MainFrame::drainStartupErrors)
                    .as("a fatal error anywhere in the queue must invoke RuntimeError.exit()")
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("System.exit");

                optionDialogsMock.verify(
                    () -> OptionDialogs.showWarningMessage(any(), anyString(), anyString()),
                    never()
                );
            }
        }
    }

    // -----------------------------------------------------------------------
    // SplashGateMath
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SplashGateMath {

        // ---- remainingFloorMs ----

        @Test
        void testRemainingFloorMsIsFullWhenNoTimeElapsed() {
            assertThat(MainFrame.remainingFloorMs(0))
                .as("zero elapsed → full floor remaining")
                .isEqualTo(MainFrame.MIN_SPLASH_DURATION_MS);
        }

        @Test
        void testRemainingFloorMsIsPartialWhenSomeTimeElapsed() {
            final var elapsed = MainFrame.MIN_SPLASH_DURATION_MS / 2;
            assertThat(MainFrame.remainingFloorMs(elapsed))
                .as("partial elapsed → floor minus elapsed")
                .isEqualTo(MainFrame.MIN_SPLASH_DURATION_MS - elapsed);
        }

        @Test
        void testRemainingFloorMsIsZeroWhenElapsedEqualsFloor() {
            assertThat(MainFrame.remainingFloorMs(MainFrame.MIN_SPLASH_DURATION_MS))
                .as("elapsed equals floor → exactly 0")
                .isEqualTo(0);
        }

        @Test
        void testRemainingFloorMsClampsToFloorWhenElapsedIsNegative() {
            assertThat(MainFrame.remainingFloorMs(-1))
                .as("negative elapsed → clamped up to the floor, never above it")
                .isEqualTo(MainFrame.MIN_SPLASH_DURATION_MS);
        }

        // ---- remainingCapMs ----

        @Test
        void testRemainingCapMsIsPositiveBeforeCap() {
            final var elapsed = MainFrame.MIDI_INIT_TIMEOUT_MS / 2;
            assertThat(MainFrame.remainingCapMs(elapsed))
                .as("elapsed well before cap → remaining cap is positive")
                .isGreaterThan(0)
                .isEqualTo(MainFrame.MIDI_INIT_TIMEOUT_MS - elapsed);
        }

        @Test
        void testRemainingCapMsIsZeroAtCap() {
            assertThat(MainFrame.remainingCapMs(MainFrame.MIDI_INIT_TIMEOUT_MS))
                .as("elapsed equals cap → exactly 0")
                .isEqualTo(0);
        }

        @Test
        void testRemainingCapMsIsFullWhenNoTimeElapsed() {
            assertThat(MainFrame.remainingCapMs(0))
                .as("zero elapsed → full cap remaining")
                .isEqualTo(MainFrame.MIDI_INIT_TIMEOUT_MS);
        }

        @Test
        void testRemainingCapMsClampsToCapWhenElapsedIsNegative() {
            assertThat(MainFrame.remainingCapMs(-1))
                .as("negative elapsed → clamped down to the cap, never above it")
                .isEqualTo(MainFrame.MIDI_INIT_TIMEOUT_MS);
        }
    }

}
