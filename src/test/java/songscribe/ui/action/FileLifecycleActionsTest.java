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

package songscribe.ui.action;

import module java.desktop;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.formdev.flatlaf.util.SystemInfo;

import songscribe.MainFrameMockTest;
import songscribe.dom.Song;
import songscribe.message.MessageCenter;
import songscribe.message.command.NewFileCommand;
import songscribe.message.command.OpenFileCommand;
import songscribe.message.command.PrintCommand;
import songscribe.message.command.RevertToSavedCommand;
import songscribe.message.command.SaveAsCommand;
import songscribe.message.command.SaveCommand;
import songscribe.message.command.ShowOpenDialogCommand;
import songscribe.message.notification.DocumentWasSavedNotification;
import songscribe.prefs.RecentDocumentsManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/**
 * Tests for file/lifecycle actions (section 5E).
 */
class FileLifecycleActionsTest extends MainFrameMockTest {

    // -------------------------------------------------------------------------
    // NewAction — rows 1 & 2
    // -------------------------------------------------------------------------

    @Nested
    class NewActionTests {

        // Row 2: constructor sets DISABLE_WHEN_PLAYING flag

        @Test
        void testConstructorSetsDisableWhenPlayingFlag() {
            var action = NewAction.createAction(mainFrame());
            assertThat(action.hasFlag(UIAction.Flag.DISABLE_WHEN_PLAYING)).isTrue();
        }

        // Row 1: actionPerformed posts NewFileCommand on the message bus

        @Test
        void testActionPerformedPostsNewFileCommand() {
            try (var messageCenterMock = mockStatic(MessageCenter.class)) {
                var action = NewAction.createAction(mainFrame());
                action.actionPerformed(
                    new ActionEvent(action, ActionEvent.ACTION_PERFORMED, "new-document"));

                messageCenterMock.verify(
                    () -> MessageCenter.post(any(NewFileCommand.class)));
            }
        }
    }

    // -------------------------------------------------------------------------
    // OpenAction — rows 3 & 4
    // -------------------------------------------------------------------------

    @Nested
    class OpenActionTests {

        // Row 4: constructor sets DISABLE_WHEN_PLAYING and OPENS_DIALOG flags

        @Test
        void testConstructorSetsBothFlags() {
            var action = OpenAction.createAction(mainFrame());
            assertAll(
                () -> assertThat(action.hasFlag(UIAction.Flag.DISABLE_WHEN_PLAYING)).isTrue(),
                () -> assertThat(action.hasFlag(UIAction.Flag.OPENS_DIALOG)).isTrue()
            );
        }

        // Row 3: actionPerformed posts ShowOpenDialogCommand on the message bus

        @Test
        void testActionPerformedPostsShowOpenDialogCommand() {
            try (var messageCenterMock = mockStatic(MessageCenter.class)) {
                var action = OpenAction.createAction(mainFrame());
                action.actionPerformed(
                    new ActionEvent(action, ActionEvent.ACTION_PERFORMED, "open-document"));

                messageCenterMock.verify(
                    () -> MessageCenter.post(any(ShowOpenDialogCommand.class)));
            }
        }
    }

    // -------------------------------------------------------------------------
    // OpenRecentAction — rows 5, 6 & 7
    // -------------------------------------------------------------------------

    @Nested
    class OpenRecentActionTests {

        private MockedStatic<MessageCenter> messageCenterMock;
        private MockedStatic<RecentDocumentsManager> recentManagerMock;

        @BeforeEach
        void setUp() {
            messageCenterMock = mockStatic(MessageCenter.class);
            recentManagerMock = mockStatic(RecentDocumentsManager.class);
        }

        @AfterEach
        void tearDown() {
            recentManagerMock.close();
            messageCenterMock.close();
        }

        // Row 7: constructor sets DISABLE_WHEN_PLAYING and DISABLE_IN_GRACE_MODE flags

        @Test
        void testConstructorSetsBothFlags() {
            var action = new OpenRecentAction(mainFrame(), "doc.mssw", Path.of("/nonexistent"));
            assertAll(
                () -> assertThat(action.hasFlag(UIAction.Flag.DISABLE_WHEN_PLAYING)).isTrue(),
                () -> assertThat(action.hasFlag(UIAction.Flag.DISABLE_IN_GRACE_MODE)).isTrue()
            );
        }

        // Row 5: actionPerformed — path exists → posts OpenFileCommand with the correct file

        @Test
        void testActionPerformedPathExistsPostsOpenFileCommandWithMatchingPath() throws Exception {
            var tempFile = Files.createTempFile("songscribe-test", ".mssw");

            try {
                var action = new OpenRecentAction(mainFrame(), "doc.mssw", tempFile);
                action.actionPerformed(
                    new ActionEvent(action, ActionEvent.ACTION_PERFORMED, "doc.mssw"));

                var captor = ArgumentCaptor.forClass(OpenFileCommand.class);
                messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
                assertThat(captor.getValue().getFile().toPath().normalize())
                    .isEqualTo(tempFile.normalize());
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }

        // Row 6: actionPerformed — path does not exist → calls remove, does NOT post OpenFileCommand

        @Test
        void testActionPerformedPathMissingCallsRemoveAndDoesNotPostOpenFileCommand() {
            var missingPath = Path.of("/nonexistent/missing-file.mssw");
            var action = new OpenRecentAction(mainFrame(), "missing-file.mssw", missingPath);
            action.actionPerformed(
                new ActionEvent(action, ActionEvent.ACTION_PERFORMED, "missing-file.mssw"));

            recentManagerMock.verify(() -> RecentDocumentsManager.remove(missingPath.normalize()));
            messageCenterMock.verify(
                () -> MessageCenter.post(any(OpenFileCommand.class)),
                never());
        }
    }

    // -------------------------------------------------------------------------
    // ClearRecentsAction — rows 12 & 13
    // -------------------------------------------------------------------------

    @Nested
    class ClearRecentsActionTests {

        // Row 13: constructor sets DISABLE_WHEN_PLAYING and DISABLE_IN_GRACE_MODE flags

        @Test
        void testConstructorSetsBothFlags() {
            var action = ClearRecentsAction.createAction(mainFrame());
            assertAll(
                () -> assertThat(action.hasFlag(UIAction.Flag.DISABLE_WHEN_PLAYING)).isTrue(),
                () -> assertThat(action.hasFlag(UIAction.Flag.DISABLE_IN_GRACE_MODE)).isTrue()
            );
        }

        // Row 12: actionPerformed calls RecentDocumentsManager.clear()

        @Test
        void testActionPerformedCallsClear() {
            try (var recentManagerMock = mockStatic(RecentDocumentsManager.class)) {
                var action = ClearRecentsAction.createAction(mainFrame());
                action.actionPerformed(
                    new ActionEvent(action, ActionEvent.ACTION_PERFORMED, "clear-recents"));

                recentManagerMock.verify(RecentDocumentsManager::clear);
            }
        }
    }

    // -------------------------------------------------------------------------
    // SaveAction — rows 14, 15 & 16
    // -------------------------------------------------------------------------

    @Nested
    class SaveActionTests {

        // Row 14: constructor sets no disabling flags (Save is always enabled)

        @Test
        void testConstructorSetsNoDisablingFlags() {
            var action = SaveAction.createAction(mainFrame());
            assertAll(
                () -> assertThat(action.hasFlag(UIAction.Flag.DISABLE_WHEN_PLAYING)).isFalse(),
                () -> assertThat(action.hasFlag(UIAction.Flag.DISABLE_IN_GRACE_MODE)).isFalse(),
                () -> assertThat(action.hasFlag(UIAction.Flag.OPENS_DIALOG)).isFalse()
            );
        }

        // Row 15: perform(source) bypasses the message bus and returns mainFrame.save() result

        @Test
        void testPerformReturnsTrueWhenSaveSucceeds() {
            when(mainFrame().save()).thenReturn(true);
            var action = SaveAction.createAction(mainFrame());
            assertThat(action.perform(null)).isTrue();
        }

        @Test
        void testPerformReturnsFalseWhenSaveFails() {
            when(mainFrame().save()).thenReturn(false);
            var action = SaveAction.createAction(mainFrame());
            assertThat(action.perform(null)).isFalse();
        }

        // Row 16: actionPerformed posts SaveCommand on the message bus

        @Test
        void testActionPerformedPostsSaveCommand() {
            try (var messageCenterMock = mockStatic(MessageCenter.class)) {
                var action = SaveAction.createAction(mainFrame());
                action.actionPerformed(
                    new ActionEvent(action, ActionEvent.ACTION_PERFORMED, "save"));

                messageCenterMock.verify(
                    () -> MessageCenter.post(any(SaveCommand.class)));
            }
        }
    }

    // -------------------------------------------------------------------------
    // SaveAsAction — rows 17 & 18
    // -------------------------------------------------------------------------

    @Nested
    class SaveAsActionTests {

        // Row 18: constructor sets DISABLE_WHEN_PLAYING, DISABLE_IN_GRACE_MODE, and OPENS_DIALOG flags

        @Test
        void testConstructorSetsAllThreeFlags() {
            var action = SaveAsAction.createAction(mainFrame());
            assertAll(
                () -> assertThat(action.hasFlag(UIAction.Flag.DISABLE_WHEN_PLAYING)).isTrue(),
                () -> assertThat(action.hasFlag(UIAction.Flag.DISABLE_IN_GRACE_MODE)).isTrue(),
                () -> assertThat(action.hasFlag(UIAction.Flag.OPENS_DIALOG)).isTrue()
            );
        }

        // Row 17: actionPerformed posts SaveAsCommand on the message bus

        @Test
        void testActionPerformedPostsSaveAsCommand() {
            try (var messageCenterMock = mockStatic(MessageCenter.class)) {
                var action = SaveAsAction.createAction(mainFrame());
                action.actionPerformed(
                    new ActionEvent(action, ActionEvent.ACTION_PERFORMED, "save-as"));

                messageCenterMock.verify(
                    () -> MessageCenter.post(any(SaveAsCommand.class)));
            }
        }
    }

    // -------------------------------------------------------------------------
    // RevertToSavedAction
    // -------------------------------------------------------------------------

    @Nested
    class RevertToSavedActionTests {

        @Test
        void testConstructorSetsDisableWhenPlaying() {
            var action = RevertToSavedAction.createAction(mainFrame());
            assertAll(
                () -> assertThat(action.hasFlag(UIAction.Flag.DISABLE_WHEN_PLAYING)).isTrue(),
                () -> assertThat(action.hasFlag(UIAction.Flag.DISABLE_IN_GRACE_MODE)).isFalse(),
                () -> assertThat(action.hasFlag(UIAction.Flag.OPENS_DIALOG)).isFalse()
            );
        }

        private Song stubSong(boolean modified) {
            var song = mock(Song.class);
            when(song.isModified()).thenReturn(modified);
            when(mockEnv().score().getSong()).thenReturn(song);
            return song;
        }

        @Test
        void testUpdateEnabledStateFalseWhenCurrentFileIsNull() {
            when(mainFrame().getCurrentFile()).thenReturn(null);
            stubSong(true);
            var action = RevertToSavedAction.createAction(mainFrame());
            assertThat(action.updateEnabledState()).isFalse();
        }

        @Test
        void testUpdateEnabledStateFalseWhenNotModified() {
            when(mainFrame().getCurrentFile()).thenReturn(new File("some-song.mssw"));
            stubSong(false);
            var action = RevertToSavedAction.createAction(mainFrame());
            assertThat(action.updateEnabledState()).isFalse();
        }

        @Test
        void testUpdateEnabledStateTrueWhenCurrentFileIsNotNullAndModified() {
            when(mainFrame().getCurrentFile()).thenReturn(new File("some-song.mssw"));
            stubSong(true);
            var action = RevertToSavedAction.createAction(mainFrame());
            assertThat(action.updateEnabledState()).isTrue();
        }

        @Test
        void testUpdateEnabledStateFalseWhenScoreViewIsNull() {
            when(mainFrame().getScoreView()).thenReturn(null);
            when(mainFrame().getCurrentFile()).thenReturn(new File("some-song.mssw"));
            var action = RevertToSavedAction.createAction(mainFrame());
            assertThat(action.updateEnabledState()).isFalse();
        }

        @Test
        void testDocumentWasSavedDisablesActionAfterSave() {
            when(mainFrame().getCurrentFile()).thenReturn(new File("some-song.mssw"));
            var song = stubSong(true);
            var action = RevertToSavedAction.createAction(mainFrame());
            assertThat(action.updateEnabledState()).isTrue();

            // Saving clears the modified flag; the handler must re-disable the action.
            when(song.isModified()).thenReturn(false);
            action.documentWasSaved(new DocumentWasSavedNotification());

            assertThat(action.isEnabled()).isFalse();
        }

        @Test
        void testActionPerformedPostsRevertToSavedCommand() {
            try (var messageCenterMock = mockStatic(MessageCenter.class)) {
                var action = RevertToSavedAction.createAction(mainFrame());
                action.actionPerformed(
                    new ActionEvent(action, ActionEvent.ACTION_PERFORMED, "revert-to-saved"));

                messageCenterMock.verify(
                    () -> MessageCenter.post(any(RevertToSavedCommand.class)));
            }
        }
    }

    // -------------------------------------------------------------------------
    // QuitAction — rows 21 & 22
    // -------------------------------------------------------------------------

    @Nested
    class QuitActionTests {

        // Row 21: constructor sets platform-appropriate name and accelerator

        @Test
        void testConstructorSetsNameAndAcceleratorForCurrentPlatform() {
            var action = QuitAction.createAction(mainFrame());

            if (SystemInfo.isMacOS) {
                // On macOS the system provides the accelerator; the action must not set one
                assertThat(action.getName()).isEqualTo(QuitAction.NAME);
                assertThat(action.getAccelerator()).isNull();
            } else {
                // On non-macOS platforms the action sets Alt+F4
                assertThat(action.getName()).isEqualTo(QuitAction.NAME);
                assertThat(action.getAccelerator()).isEqualTo(
                    KeyStroke.getKeyStroke(KeyEvent.VK_F4, InputEvent.ALT_DOWN_MASK));
            }
        }

        // Row 22: constructor sets no disabling flags (quit is always enabled)

        @Test
        void testConstructorSetsNoDisablingFlags() {
            var action = QuitAction.createAction(mainFrame());
            assertAll(
                () -> assertThat(action.hasFlag(UIAction.Flag.DISABLE_WHEN_PLAYING)).isFalse(),
                () -> assertThat(action.hasFlag(UIAction.Flag.DISABLE_IN_GRACE_MODE)).isFalse(),
                () -> assertThat(action.hasFlag(UIAction.Flag.OPENS_DIALOG)).isFalse()
            );
        }
    }

    // -------------------------------------------------------------------------
    // PrintAction — rows 23 & 24
    // -------------------------------------------------------------------------

    @Nested
    class PrintActionTests {

        // Row 24: constructor sets DISABLE_WHEN_PLAYING, DISABLE_IN_GRACE_MODE, and OPENS_DIALOG

        @Test
        void testConstructorSetsAllThreeFlags() {
            var action = PrintAction.createAction(mainFrame());
            assertAll(
                () -> assertThat(action.hasFlag(UIAction.Flag.DISABLE_WHEN_PLAYING)).isTrue(),
                () -> assertThat(action.hasFlag(UIAction.Flag.DISABLE_IN_GRACE_MODE)).isTrue(),
                () -> assertThat(action.hasFlag(UIAction.Flag.OPENS_DIALOG)).isTrue()
            );
        }

        // Row 23: actionPerformed posts PrintCommand on the message bus

        @Test
        void testActionPerformedPostsPrintCommand() {
            try (var messageCenterMock = mockStatic(MessageCenter.class)) {
                var action = PrintAction.createAction(mainFrame());
                action.actionPerformed(
                    new ActionEvent(action, ActionEvent.ACTION_PERFORMED, "print-document"));

                messageCenterMock.verify(
                    () -> MessageCenter.post(any(PrintCommand.class)));
            }
        }
    }
}
