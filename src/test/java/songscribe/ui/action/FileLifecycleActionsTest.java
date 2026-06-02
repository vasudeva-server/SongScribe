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

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.MainFrameMockTest;
import songscribe.message.MessageCenter;
import songscribe.message.command.NewFileCommand;
import songscribe.message.command.OpenFileCommand;
import songscribe.message.command.ShowOpenDialogCommand;
import songscribe.prefs.RecentDocumentsManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

/**
 * Tests for NewAction, OpenAction, and OpenRecentAction (section 5E rows 1–7).
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

                var captor = org.mockito.ArgumentCaptor.forClass(OpenFileCommand.class);
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
}
