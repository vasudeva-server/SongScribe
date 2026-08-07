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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import songscribe.MainFrameMockTest;
import songscribe.message.MessageCenter;
import songscribe.message.command.PasteboardOpCommand;
import songscribe.message.notification.ClipboardDidChangeNotification;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.ui.edit.GraceModeManager;
import songscribe.ui.playback.PlaybackController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class PasteboardActionTest extends MainFrameMockTest {

    private MockedStatic<PlaybackController> playbackControllerMock;

    @BeforeEach
    void setUp() {
        // Must follow MainFrame setup: PlaybackController.isPlaying() is checked by
        // updateEnabledState() for DISABLE_WHEN_PLAYING actions.
        playbackControllerMock = mockStatic(PlaybackController.class);
        playbackControllerMock.when(PlaybackController::isPlaying).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        playbackControllerMock.close();
    }

    // Row 1: musicSelectionDidChange — enablement branches

    @Nested
    class MusicSelectionDidChange {

        @Test
        void testCopyEnabledWhenSelectionSizeGreaterThanZero() {
            when(mockEnv().score().getSelectionSize()).thenReturn(1);
            var action = CopyAction.createAction(mainFrame());
            action.setEnabled(false);

            action.musicSelectionDidChange(
                new MusicSelectionDidChangeNotification(mockEnv().score()));

            assertThat(action.isEnabled()).isTrue();
        }

        @Test
        void testCopyDisabledWhenSelectionSizeIsZero() {
            when(mockEnv().score().getSelectionSize()).thenReturn(0);
            var action = CopyAction.createAction(mainFrame());
            action.setEnabled(true);

            action.musicSelectionDidChange(
                new MusicSelectionDidChangeNotification(mockEnv().score()));

            assertThat(action.isEnabled()).isFalse();
        }

        @Test
        void testCutEnabledWhenSelectionSizeGreaterThanZero() {
            when(mockEnv().score().getSelectionSize()).thenReturn(2);
            var action = CutAction.createAction(mainFrame());
            action.setEnabled(false);

            action.musicSelectionDidChange(
                new MusicSelectionDidChangeNotification(mockEnv().score()));

            assertThat(action.isEnabled()).isTrue();
        }

        @Test
        void testCutDisabledWhenSelectionSizeIsZero() {
            when(mockEnv().score().getSelectionSize()).thenReturn(0);
            var action = CutAction.createAction(mainFrame());
            action.setEnabled(true);

            action.musicSelectionDidChange(
                new MusicSelectionDidChangeNotification(mockEnv().score()));

            assertThat(action.isEnabled()).isFalse();
        }

        @Test
        void testPasteEnabledWhenPasteboardNonEmpty() {
            when(mockEnv().score().getPasteboardSize()).thenReturn(1);
            var action = PasteAction.createAction(mainFrame());
            action.setEnabled(false);

            action.musicSelectionDidChange(
                new MusicSelectionDidChangeNotification(mockEnv().score()));

            assertThat(action.isEnabled()).isTrue();
        }

        @Test
        void testPasteDisabledWhenPasteboardEmpty() {
            when(mockEnv().score().getPasteboardSize()).thenReturn(0);
            var action = PasteAction.createAction(mainFrame());
            action.setEnabled(true);

            action.musicSelectionDidChange(
                new MusicSelectionDidChangeNotification(mockEnv().score()));

            assertThat(action.isEnabled()).isFalse();
        }
    }

    // clipboardDidChange — Paste's enabled state must be refreshed from clipboard
    // content, not just from selection changes (ClipboardManager.setFragment/clear
    // post ClipboardDidChangeNotification precisely so this handler re-evaluates it).

    @Nested
    class ClipboardDidChange {

        @Test
        void testPasteEnabledWhenPasteboardNonEmpty() {
            when(mockEnv().score().getPasteboardSize()).thenReturn(1);
            var action = PasteAction.createAction(mainFrame());
            action.setEnabled(false);

            action.clipboardDidChange(new ClipboardDidChangeNotification());

            assertThat(action.isEnabled()).isTrue();
        }

        @Test
        void testPasteDisabledWhenPasteboardEmpty() {
            when(mockEnv().score().getPasteboardSize()).thenReturn(0);
            var action = PasteAction.createAction(mainFrame());
            action.setEnabled(true);

            action.clipboardDidChange(new ClipboardDidChangeNotification());

            assertThat(action.isEnabled()).isFalse();
        }
    }

    // Row 2: actionPerformed dispatches PasteboardOpCommand with correct operation

    @Nested
    class ActionPerformed {

        private MockedStatic<MessageCenter> messageCenterMock;

        @BeforeEach
        void setUpMessageCenter() {
            messageCenterMock = mockStatic(MessageCenter.class);
        }

        @AfterEach
        void tearDownMessageCenter() {
            messageCenterMock.close();
        }

        @Test
        void testActionPerformedDispatchesCopyCommand() {
            var action = CopyAction.createAction(mainFrame());
            action.actionPerformed(new ActionEvent(action, ActionEvent.ACTION_PERFORMED, "edit-copy"));

            var captor = ArgumentCaptor.forClass(PasteboardOpCommand.class);
            messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
            assertThat(captor.getValue().getOperation())
                .isEqualTo(PasteboardAction.Operation.COPY);
        }

        @Test
        void testActionPerformedDispatchesCutCommand() {
            var action = CutAction.createAction(mainFrame());
            action.actionPerformed(new ActionEvent(action, ActionEvent.ACTION_PERFORMED, "edit-cut"));

            var captor = ArgumentCaptor.forClass(PasteboardOpCommand.class);
            messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
            assertThat(captor.getValue().getOperation())
                .isEqualTo(PasteboardAction.Operation.CUT);
        }

        @Test
        void testActionPerformedDispatchesDeleteCommand() {
            var action = DeleteAction.createAction(mainFrame());
            action.actionPerformed(new ActionEvent(action, ActionEvent.ACTION_PERFORMED, "edit-delete"));

            var captor = ArgumentCaptor.forClass(PasteboardOpCommand.class);
            messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
            assertThat(captor.getValue().getOperation())
                .isEqualTo(PasteboardAction.Operation.DELETE);
        }

        @Test
        void testActionPerformedDispatchesPasteCommand() {
            var action = PasteAction.createAction(mainFrame());
            action.actionPerformed(new ActionEvent(action, ActionEvent.ACTION_PERFORMED, "edit-paste"));

            var captor = ArgumentCaptor.forClass(PasteboardOpCommand.class);
            messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
            assertThat(captor.getValue().getOperation())
                .isEqualTo(PasteboardAction.Operation.PASTE);
        }
    }

    // Row 3: PasteAction's modality flags — required so Cmd+V doesn't enter paste
    // mode on top of an in-progress grace mode (phase 5 task 7). Nothing else
    // covers this: LyricEditorActionAuditTest whitelists toolbar actions only.

    @Nested
    class PasteActionFlags {

        @Test
        void testPasteActionCarriesGraceModeAndTextEditingDisableFlags() {
            var action = PasteAction.createAction(mainFrame());

            assertThat(action.hasFlag(UIAction.Flag.DISABLE_IN_GRACE_MODE)).isTrue();
            assertThat(action.hasFlag(UIAction.Flag.DISABLE_WHEN_EDITING_TEXT)).isTrue();
        }

        @Test
        void testPasteActionIsDisabledWhileGraceModeIsActive() {
            try (var graceModeMock = mockStatic(GraceModeManager.class)) {
                graceModeMock.when(GraceModeManager::isActive).thenReturn(true);

                var action = PasteAction.createAction(mainFrame());
                var result = action.updateEnabledState();

                assertThat(result).isFalse();
                assertThat(action.isEnabled()).isFalse();
            }
        }
    }

    // Issue #713: with the terminal selected, every pasteboard operation is disabled — Copy,
    // Cut and Paste through PasteboardAction.updateScoreEnabledState()'s shared early return,
    // Delete through DeleteAction's own override of the same method.

    @Nested
    class TerminalSelected {

        @BeforeEach
        void setUpTerminal() {
            when(mockEnv().coordinator().isTerminalSelected()).thenReturn(true);
        }

        @Test
        void testCopyDisabledWhenTerminalSelected() {
            when(mockEnv().score().getSelectionSize()).thenReturn(1);
            var action = CopyAction.createAction(mainFrame());

            assertThat(action.updateScoreEnabledState()).isFalse();
        }

        @Test
        void testCutDisabledWhenTerminalSelected() {
            when(mockEnv().score().getSelectionSize()).thenReturn(1);
            var action = CutAction.createAction(mainFrame());

            assertThat(action.updateScoreEnabledState()).isFalse();
        }

        @Test
        void testPasteDisabledWhenTerminalSelected() {
            when(mockEnv().score().getPasteboardSize()).thenReturn(1);
            var action = PasteAction.createAction(mainFrame());

            assertThat(action.updateScoreEnabledState()).isFalse();
        }

        // Guards the ordering of DeleteAction's early return: canDeleteLine() true would
        // otherwise make Delete enabled via its `|| scoreView.canDeleteLine()` disjunct if the
        // terminal check were ever dropped or reordered after it (phase 5 task 5).
        @Test
        void testDeleteDisabledWhenTerminalSelectedEvenWhenLineIsDeletable() {
            when(mockEnv().score().canDeleteLine()).thenReturn(true);
            var action = DeleteAction.createAction(mainFrame());

            assertThat(action.updateScoreEnabledState()).isFalse();
        }
    }

    // The terminal gate must not be over-broad: an ordinary single-element selection still
    // leaves every operation enabled.

    @Nested
    class OrdinarySelectionStillEnabled {

        @BeforeEach
        void setUpOrdinaryElement() {
            when(mockEnv().coordinator().isTerminalSelected()).thenReturn(false);
        }

        @Test
        void testCopyEnabledForOrdinarySelection() {
            when(mockEnv().score().getSelectionSize()).thenReturn(1);
            var action = CopyAction.createAction(mainFrame());

            assertThat(action.updateScoreEnabledState()).isTrue();
        }

        @Test
        void testCutEnabledForOrdinarySelection() {
            when(mockEnv().score().getSelectionSize()).thenReturn(1);
            var action = CutAction.createAction(mainFrame());

            assertThat(action.updateScoreEnabledState()).isTrue();
        }

        @Test
        void testPasteEnabledForOrdinarySelection() {
            when(mockEnv().score().getPasteboardSize()).thenReturn(1);
            var action = PasteAction.createAction(mainFrame());

            assertThat(action.updateScoreEnabledState()).isTrue();
        }

        @Test
        void testDeleteEnabledForOrdinarySelection() {
            when(mockEnv().coordinator().hasActiveSelection()).thenReturn(true);
            var action = DeleteAction.createAction(mainFrame());

            assertThat(action.updateScoreEnabledState()).isTrue();
        }
    }
}
