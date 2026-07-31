/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package songscribe.ui.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.awt.Component;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.KeyStroke;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.engraving.Staff;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.command.DeselectCommand;
import songscribe.message.command.ToggleBeamWithPreviousCommand;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.ElementModification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.ui.Mode;
import songscribe.ui.OptionDialogs;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.selection.LineSelectionState;
import songscribe.ui.selection.ReflectionTestHelper;
import songscribe.ui.selection.SelectionCoordinator;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.edit.GraceModeManager;
import songscribe.ui.edit.PasteModeManager;
import songscribe.ui.playback.PlayThread;
import songscribe.util.UIUtils;

class ScoreInputHandlerTest extends UnitTest {

    // -------------------------------------------------------------------
    // Rows 59-60: mouseClicked requests focus only for BUTTON1
    // -------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class MouseClicked {

        @Test
        void testMouseClickedNonButton1DoesNotRequestFocus() {
            var callback = mock(InputHandlerCallback.class);
            var handler = new ScoreInputHandler(callback);

            handler.mouseClicked(mouseClickEvent(MouseEvent.BUTTON3));

            verify(callback, never()).requestFocusInWindow();
        }

        @Test
        void testMouseClickedButton1RequestsFocus() {
            var callback = mock(InputHandlerCallback.class);
            when(callback.getSelectionCoordinator()).thenReturn(mock(SelectionCoordinator.class));
            var handler = new ScoreInputHandler(callback);
            var pasteModeManager = mock(PasteModeManager.class);

            try (MockedStatic<EditModeManager> emm = mockStatic(EditModeManager.class)) {
                emm.when(EditModeManager::getPasteModeManager).thenReturn(pasteModeManager);

                handler.mouseClicked(mouseClickEvent(MouseEvent.BUTTON1));

                verify(callback).requestFocusInWindow();
            }
        }

        @Test
        void testMouseClickedButton1WhenPasteModeInProgressCancelsPasteMode() {
            var callback = mock(InputHandlerCallback.class);
            when(callback.getSelectionCoordinator()).thenReturn(mock(SelectionCoordinator.class));
            var handler = new ScoreInputHandler(callback);
            var pasteModeManager = mock(PasteModeManager.class);
            when(pasteModeManager.isInProgress()).thenReturn(true);

            try (MockedStatic<EditModeManager> emm = mockStatic(EditModeManager.class)) {
                emm.when(EditModeManager::getPasteModeManager).thenReturn(pasteModeManager);

                handler.mouseClicked(mouseClickEvent(MouseEvent.BUTTON1));

                verify(pasteModeManager).cancel();
            }
        }

        @Test
        void testMouseClickedButton1WhenPasteModeNotInProgressDoesNotCancel() {
            var callback = mock(InputHandlerCallback.class);
            when(callback.getSelectionCoordinator()).thenReturn(mock(SelectionCoordinator.class));
            var handler = new ScoreInputHandler(callback);
            var pasteModeManager = mock(PasteModeManager.class);
            when(pasteModeManager.isInProgress()).thenReturn(false);

            try (MockedStatic<EditModeManager> emm = mockStatic(EditModeManager.class)) {
                emm.when(EditModeManager::getPasteModeManager).thenReturn(pasteModeManager);

                handler.mouseClicked(mouseClickEvent(MouseEvent.BUTTON1));

                verify(pasteModeManager, never()).cancel();
            }
        }

        @Test
        void testMouseClickedButton1InSelectModeOutsideAnyLinePostsDeselectCommand() {
            var callback = mock(InputHandlerCallback.class);
            var coordinator = mock(SelectionCoordinator.class);
            when(callback.getSelectionCoordinator()).thenReturn(coordinator);
            when(coordinator.isInSelectMode()).thenReturn(true);
            var handler = new ScoreInputHandler(callback);
            var pasteModeManager = mock(PasteModeManager.class);

            try (
                MockedStatic<EditModeManager> emm = mockStatic(EditModeManager.class);
                MockedStatic<MessageCenter> mc = mockStatic(MessageCenter.class)
            ) {
                emm.when(EditModeManager::getPasteModeManager).thenReturn(pasteModeManager);

                handler.mouseClicked(mouseClickEvent(MouseEvent.BUTTON1));

                mc.verify(() -> MessageCenter.post(any(DeselectCommand.class)));
            }
        }

        @Test
        void testMouseClickedButton1NotInSelectModeDoesNotPostDeselectCommand() {
            var callback = mock(InputHandlerCallback.class);
            var coordinator = mock(SelectionCoordinator.class);
            when(callback.getSelectionCoordinator()).thenReturn(coordinator);
            when(coordinator.isInSelectMode()).thenReturn(false);
            var handler = new ScoreInputHandler(callback);
            var pasteModeManager = mock(PasteModeManager.class);

            try (
                MockedStatic<EditModeManager> emm = mockStatic(EditModeManager.class);
                MockedStatic<MessageCenter> mc = mockStatic(MessageCenter.class)
            ) {
                emm.when(EditModeManager::getPasteModeManager).thenReturn(pasteModeManager);

                handler.mouseClicked(mouseClickEvent(MouseEvent.BUTTON1));

                mc.verify(() -> MessageCenter.post(any(DeselectCommand.class)), never());
            }
        }

        /**
         * Unlike the Escape key — where the first press only cancels paste mode and leaves
         * the selection intact — a click does both at once, since the click has already
         * moved the user's attention off the selection.
         */
        @Test
        void testMouseClickedButton1InSelectModeWhilePasteModeInProgressCancelsAndDeselects() {
            var callback = mock(InputHandlerCallback.class);
            var coordinator = mock(SelectionCoordinator.class);
            when(callback.getSelectionCoordinator()).thenReturn(coordinator);
            when(coordinator.isInSelectMode()).thenReturn(true);
            var handler = new ScoreInputHandler(callback);
            var pasteModeManager = mock(PasteModeManager.class);
            when(pasteModeManager.isInProgress()).thenReturn(true);

            try (
                MockedStatic<EditModeManager> emm = mockStatic(EditModeManager.class);
                MockedStatic<MessageCenter> mc = mockStatic(MessageCenter.class)
            ) {
                emm.when(EditModeManager::getPasteModeManager).thenReturn(pasteModeManager);

                handler.mouseClicked(mouseClickEvent(MouseEvent.BUTTON1));

                verify(pasteModeManager).cancel();
                mc.verify(() -> MessageCenter.post(any(DeselectCommand.class)));
            }
        }

        /**
         * The non-BUTTON1 guard must skip every effect of the handler, not just the focus
         * request, so the preconditions for all three are satisfied here.
         */
        @Test
        void testMouseClickedNonButton1SkipsPasteCancelAndDeselect() {
            var callback = mock(InputHandlerCallback.class);
            var coordinator = mock(SelectionCoordinator.class);
            when(callback.getSelectionCoordinator()).thenReturn(coordinator);
            when(coordinator.isInSelectMode()).thenReturn(true);
            var handler = new ScoreInputHandler(callback);
            var pasteModeManager = mock(PasteModeManager.class);
            when(pasteModeManager.isInProgress()).thenReturn(true);

            try (
                MockedStatic<EditModeManager> emm = mockStatic(EditModeManager.class);
                MockedStatic<MessageCenter> mc = mockStatic(MessageCenter.class)
            ) {
                emm.when(EditModeManager::getPasteModeManager).thenReturn(pasteModeManager);

                handler.mouseClicked(mouseClickEvent(MouseEvent.BUTTON3));

                verify(pasteModeManager, never()).cancel();
                mc.verify(() -> MessageCenter.post(any(DeselectCommand.class)), never());
                verify(callback, never()).requestFocusInWindow();
            }
        }
    }

    // -------------------------------------------------------------------
    // Rows 61-62: mousePressed / mouseReleased are no-ops; the score no
    // longer shows a popup menu on a popup-trigger event
    // -------------------------------------------------------------------

    @Test
    void testMousePressedIsNoOpForPopupTriggerEvent() {
        var callback = mock(InputHandlerCallback.class);
        var handler = new ScoreInputHandler(callback);
        var event = mouseEvent(MouseEvent.MOUSE_PRESSED, true);

        handler.mousePressed(event);

        verifyNoInteractions(callback);
    }

    @Test
    void testMouseReleasedIsNoOpForPopupTriggerEvent() {
        var callback = mock(InputHandlerCallback.class);
        var handler = new ScoreInputHandler(callback);
        var event = mouseEvent(MouseEvent.MOUSE_RELEASED, true);

        handler.mouseReleased(event);

        verifyNoInteractions(callback);
    }

    // -------------------------------------------------------------------
    // Row 63: keyPressed(ALT) clears the preview element, sets altPressed,
    // and triggers a repaint
    // -------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AltKeyPressed {

        @Test
        void testKeyPressedAltClearsPreviewSetsAltPressedAndRepaints() {
            var callback = mock(InputHandlerCallback.class);
            var handler = new ScoreInputHandler(callback);

            try (MockedStatic<LineComponent> lc = mockStatic(LineComponent.class)) {
                handler.keyPressed(keyEvent(KeyEvent.VK_ALT));

                lc.verify(LineComponent::clearPreviewElement);
                lc.verify(() -> LineComponent.setAltPressed(true));
            }

            verify(callback).repaint();
        }
    }

    // -------------------------------------------------------------------
    // Rows 64-65: keyPressed(ESCAPE) behavior
    // -------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class EscapeKeyPressed {

        @Test
        void testKeyPressedEscapeDelegatesToGraceModeManagerWhenInProgress() {
            var callback = mock(InputHandlerCallback.class);
            var handler = new ScoreInputHandler(callback);
            var graceModeManager = mock(GraceModeManager.class);
            var pasteModeManager = mock(PasteModeManager.class);
            when(graceModeManager.isInProgress()).thenReturn(true);

            try (MockedStatic<EditModeManager> emm = mockStatic(EditModeManager.class)) {
                emm.when(EditModeManager::getGraceModeManager).thenReturn(graceModeManager);
                emm.when(EditModeManager::getPasteModeManager).thenReturn(pasteModeManager);

                var event = keyEvent(KeyEvent.VK_ESCAPE);
                handler.keyPressed(event);

                verify(graceModeManager).keyPressed(event);
            }
        }

        @Test
        void testKeyPressedEscapeWhenPasteModeInProgressCancelsPasteModeAndDoesNotDeselect() {
            var callback = mock(InputHandlerCallback.class);
            when(callback.getMode()).thenReturn(Mode.SELECT);
            var window = mock(Window.class);
            when(callback.getWindow()).thenReturn(window);
            var handler = new ScoreInputHandler(callback);
            var graceModeManager = mock(GraceModeManager.class);
            var pasteModeManager = mock(PasteModeManager.class);
            when(graceModeManager.isInProgress()).thenReturn(false);
            when(pasteModeManager.isInProgress()).thenReturn(true);

            try (
                MockedStatic<EditModeManager> emm = mockStatic(EditModeManager.class);
                MockedStatic<MessageCenter> mc = mockStatic(MessageCenter.class)
            ) {
                emm.when(EditModeManager::getGraceModeManager).thenReturn(graceModeManager);
                emm.when(EditModeManager::getPasteModeManager).thenReturn(pasteModeManager);

                handler.keyPressed(keyEvent(KeyEvent.VK_ESCAPE));

                verify(pasteModeManager).cancel();
                // Paste-mode cancellation must short-circuit the SELECT-mode deselect
                // fallback below it — proves the branch is exclusive, not merely reached.
                mc.verify(() -> MessageCenter.post(any(DeselectCommand.class)), never());
            }
        }

        @Test
        void testKeyPressedEscapeInSelectModeWithNullWindowPostsDeselectCommand() {
            var callback = mock(InputHandlerCallback.class);
            when(callback.getMode()).thenReturn(Mode.SELECT);
            // null window short-circuits the text-editing guard, so a deselect is posted
            when(callback.getWindow()).thenReturn(null);
            var handler = new ScoreInputHandler(callback);
            var graceModeManager = mock(GraceModeManager.class);
            var pasteModeManager = mock(PasteModeManager.class);
            when(graceModeManager.isInProgress()).thenReturn(false);

            try (
                MockedStatic<EditModeManager> emm = mockStatic(EditModeManager.class);
                MockedStatic<MessageCenter> mc = mockStatic(MessageCenter.class)
            ) {
                emm.when(EditModeManager::getGraceModeManager).thenReturn(graceModeManager);
                emm.when(EditModeManager::getPasteModeManager).thenReturn(pasteModeManager);

                handler.keyPressed(keyEvent(KeyEvent.VK_ESCAPE));

                mc.verify(() -> MessageCenter.post(any(DeselectCommand.class)));
            }
        }

        @Test
        void testKeyPressedEscapeInSelectModeNotEditingTextPostsDeselectCommand() {
            var callback = mock(InputHandlerCallback.class);
            when(callback.getMode()).thenReturn(Mode.SELECT);
            var window = mock(Window.class);
            when(callback.getWindow()).thenReturn(window);
            var handler = new ScoreInputHandler(callback);
            var graceModeManager = mock(GraceModeManager.class);
            var pasteModeManager = mock(PasteModeManager.class);
            when(graceModeManager.isInProgress()).thenReturn(false);

            try (
                MockedStatic<EditModeManager> emm = mockStatic(EditModeManager.class);
                MockedStatic<UIUtils> ui = mockStatic(UIUtils.class);
                MockedStatic<MessageCenter> mc = mockStatic(MessageCenter.class)
            ) {
                emm.when(EditModeManager::getGraceModeManager).thenReturn(graceModeManager);
                emm.when(EditModeManager::getPasteModeManager).thenReturn(pasteModeManager);
                ui.when(() -> UIUtils.isEditingTextIn(window)).thenReturn(false);

                handler.keyPressed(keyEvent(KeyEvent.VK_ESCAPE));

                mc.verify(() -> MessageCenter.post(any(DeselectCommand.class)));
            }
        }

        @Test
        void testKeyPressedEscapeInSelectModeWhileEditingTextDoesNotPostDeselect() {
            var callback = mock(InputHandlerCallback.class);
            when(callback.getMode()).thenReturn(Mode.SELECT);
            var window = mock(Window.class);
            when(callback.getWindow()).thenReturn(window);
            var handler = new ScoreInputHandler(callback);
            var graceModeManager = mock(GraceModeManager.class);
            var pasteModeManager = mock(PasteModeManager.class);
            when(graceModeManager.isInProgress()).thenReturn(false);

            try (
                MockedStatic<EditModeManager> emm = mockStatic(EditModeManager.class);
                MockedStatic<UIUtils> ui = mockStatic(UIUtils.class);
                MockedStatic<MessageCenter> mc = mockStatic(MessageCenter.class)
            ) {
                emm.when(EditModeManager::getGraceModeManager).thenReturn(graceModeManager);
                emm.when(EditModeManager::getPasteModeManager).thenReturn(pasteModeManager);
                ui.when(() -> UIUtils.isEditingTextIn(window)).thenReturn(true);

                handler.keyPressed(keyEvent(KeyEvent.VK_ESCAPE));

                mc.verify(() -> MessageCenter.post(any(DeselectCommand.class)), never());
            }
        }

        @Test
        void testKeyPressedEscapeInEditModeDoesNotPostDeselect() {
            var callback = mock(InputHandlerCallback.class);
            when(callback.getMode()).thenReturn(Mode.EDIT);
            var handler = new ScoreInputHandler(callback);
            var graceModeManager = mock(GraceModeManager.class);
            var pasteModeManager = mock(PasteModeManager.class);
            when(graceModeManager.isInProgress()).thenReturn(false);

            try (
                MockedStatic<EditModeManager> emm = mockStatic(EditModeManager.class);
                MockedStatic<MessageCenter> mc = mockStatic(MessageCenter.class)
            ) {
                emm.when(EditModeManager::getGraceModeManager).thenReturn(graceModeManager);
                emm.when(EditModeManager::getPasteModeManager).thenReturn(pasteModeManager);

                handler.keyPressed(keyEvent(KeyEvent.VK_ESCAPE));

                mc.verify(() -> MessageCenter.post(any(DeselectCommand.class)), never());
            }
        }
    }

    // -------------------------------------------------------------------
    // Row 66: keyReleased(ALT) sets altPressed=false via LineComponent
    // -------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AltKeyReleased {

        @Test
        void testKeyReleasedAltSetsAltPressedFalse() {
            var callback = mock(InputHandlerCallback.class);
            var handler = new ScoreInputHandler(callback);

            try (MockedStatic<LineComponent> lc = mockStatic(LineComponent.class)) {
                handler.keyReleased(keyEvent(KeyEvent.VK_ALT));

                lc.verify(() -> LineComponent.setAltPressed(false));
            }
        }
    }

    // -------------------------------------------------------------------
    // Rows 246-254: KeyAction(VK_ENTER) places the paste-mode fragment or,
    // outside paste mode, opens the lyric editor on the selection — either
    // way returning before touching the selection coordinator
    // -------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class EnterKeyPressed {

        @Test
        void testEnterInPasteModeCallsPlaceAndSkipsSelectionHandling() {
            var callback = mock(InputHandlerCallback.class);
            var pasteModeManager = mock(PasteModeManager.class);
            when(pasteModeManager.isInProgress()).thenReturn(true);

            try (MockedStatic<EditModeManager> emm = mockStatic(EditModeManager.class)) {
                emm.when(EditModeManager::getPasteModeManager).thenReturn(pasteModeManager);

                pressArrowKey(callback, KeyEvent.VK_ENTER);

                verify(pasteModeManager).place();
                verify(callback, never()).editLyricOnSelection();
                // The VK_ENTER branch returns immediately, so the arrow-key
                // selection path below it must never run.
                verify(callback, never()).getSelectionCoordinator();
            }
        }

        @Test
        void testEnterOutsidePasteModeOpensLyricEditorOnSelection() {
            var callback = mock(InputHandlerCallback.class);
            var pasteModeManager = mock(PasteModeManager.class);
            when(pasteModeManager.isInProgress()).thenReturn(false);

            try (MockedStatic<EditModeManager> emm = mockStatic(EditModeManager.class)) {
                emm.when(EditModeManager::getPasteModeManager).thenReturn(pasteModeManager);

                pressArrowKey(callback, KeyEvent.VK_ENTER);

                verify(callback).editLyricOnSelection();
                verify(pasteModeManager, never()).place();
                verify(callback, never()).getSelectionCoordinator();
            }
        }
    }

    // -------------------------------------------------------------------
    // Arrow-key navigation of an active selection (Left/Right)
    // -------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SelectionArrowNavigation {

        @Test
        void testLeftMovesSingleSelectionToPreviousElement() {
            var coordinator = ReflectionTestHelper.createCoordinator(threeCrotchets(), List.of());
            ReflectionTestHelper.selectNote(coordinator, 1);

            pressArrowKey(selectionCallback(coordinator), KeyEvent.VK_LEFT);

            var state = activeSelectionOrFail(coordinator);
            assertThat(coordinator.getActiveLineIndex()).isEqualTo(0);
            assertThat(state.getSelectionBegin()).isEqualTo(0);
            assertThat(state.getSelectionEnd()).isEqualTo(0);
        }

        @Test
        void testRightMovesSingleSelectionToNextElement() {
            var coordinator = ReflectionTestHelper.createCoordinator(threeCrotchets(), List.of());
            ReflectionTestHelper.selectNote(coordinator, 0);

            pressArrowKey(selectionCallback(coordinator), KeyEvent.VK_RIGHT);

            var state = activeSelectionOrFail(coordinator);
            assertThat(coordinator.getActiveLineIndex()).isEqualTo(0);
            assertThat(state.getSelectionBegin()).isEqualTo(1);
            assertThat(state.getSelectionEnd()).isEqualTo(1);
        }

        @Test
        void testLeftOnMultiSelectionCollapsesToNextToLastElement() {
            var coordinator = ReflectionTestHelper.createCoordinator(threeCrotchets(), List.of());
            ReflectionTestHelper.selectRange(coordinator, 0, 2);

            pressArrowKey(selectionCallback(coordinator), KeyEvent.VK_LEFT);

            var state = activeSelectionOrFail(coordinator);
            assertThat(state.getSelectionBegin()).isEqualTo(1);
            assertThat(state.getSelectionEnd()).isEqualTo(1);
        }

        @Test
        void testRightOnMultiSelectionCollapsesToSecondElement() {
            var coordinator = ReflectionTestHelper.createCoordinator(threeCrotchets(), List.of());
            ReflectionTestHelper.selectRange(coordinator, 0, 2);

            pressArrowKey(selectionCallback(coordinator), KeyEvent.VK_RIGHT);

            var state = activeSelectionOrFail(coordinator);
            assertThat(state.getSelectionBegin()).isEqualTo(1);
            assertThat(state.getSelectionEnd()).isEqualTo(1);
        }

        @Test
        void testLeftAtFirstElementWithNoPreviousLineIsNoOp() {
            var coordinator = ReflectionTestHelper.createCoordinator(threeCrotchets(), List.of());
            ReflectionTestHelper.selectNote(coordinator, 0);

            pressArrowKey(selectionCallback(coordinator), KeyEvent.VK_LEFT);

            var state = activeSelectionOrFail(coordinator);
            assertThat(coordinator.getActiveLineIndex()).isEqualTo(0);
            assertThat(state.getSelectionBegin()).isEqualTo(0);
            assertThat(state.getSelectionEnd()).isEqualTo(0);
        }

        @Test
        void testRightAtLastElementWithNoNextLineIsNoOp() {
            var coordinator = ReflectionTestHelper.createCoordinator(threeCrotchets(), List.of());
            ReflectionTestHelper.selectNote(coordinator, 2);

            pressArrowKey(selectionCallback(coordinator), KeyEvent.VK_RIGHT);

            var state = activeSelectionOrFail(coordinator);
            assertThat(coordinator.getActiveLineIndex()).isEqualTo(0);
            assertThat(state.getSelectionBegin()).isEqualTo(2);
            assertThat(state.getSelectionEnd()).isEqualTo(2);
        }

        @Test
        void testLeftAtFirstElementCrossesToPreviousLineLastElement() {
            var coordinator = twoLineCoordinator(2, 2);
            coordinator.activateLine(1);
            ReflectionTestHelper.selectNote(coordinator, 0);

            pressArrowKey(selectionCallback(coordinator), KeyEvent.VK_LEFT);

            var state = activeSelectionOrFail(coordinator);
            assertThat(coordinator.getActiveLineIndex()).isEqualTo(0);
            assertThat(state.getSelectionBegin()).isEqualTo(1);
            assertThat(state.getSelectionEnd()).isEqualTo(1);
        }

        @Test
        void testRightAtLastElementCrossesToNextLineFirstElement() {
            var coordinator = twoLineCoordinator(2, 2);
            coordinator.activateLine(0);
            ReflectionTestHelper.selectNote(coordinator, 1);

            pressArrowKey(selectionCallback(coordinator), KeyEvent.VK_RIGHT);

            var state = activeSelectionOrFail(coordinator);
            assertThat(coordinator.getActiveLineIndex()).isEqualTo(1);
            assertThat(state.getSelectionBegin()).isEqualTo(0);
            assertThat(state.getSelectionEnd()).isEqualTo(0);
        }

        @Test
        void testRightAtLastElementWithEmptyNextLineIsNoOp() {
            var coordinator = twoLineCoordinator(2, 0);
            coordinator.activateLine(0);
            ReflectionTestHelper.selectNote(coordinator, 1);

            pressArrowKey(selectionCallback(coordinator), KeyEvent.VK_RIGHT);

            // The next line has no elements, so the selection stays put on line 0.
            var state = activeSelectionOrFail(coordinator);
            assertThat(coordinator.getActiveLineIndex()).isEqualTo(0);
            assertThat(state.getSelectionBegin()).isEqualTo(1);
            assertThat(state.getSelectionEnd()).isEqualTo(1);
        }

        @Test
        void testLeftAtFirstElementWithEmptyPreviousLineIsNoOp() {
            var coordinator = twoLineCoordinator(0, 2);
            coordinator.activateLine(1);
            ReflectionTestHelper.selectNote(coordinator, 0);

            pressArrowKey(selectionCallback(coordinator), KeyEvent.VK_LEFT);

            // The previous line has no elements, so the selection stays put on line 1.
            var state = activeSelectionOrFail(coordinator);
            assertThat(coordinator.getActiveLineIndex()).isEqualTo(1);
            assertThat(state.getSelectionBegin()).isEqualTo(0);
            assertThat(state.getSelectionEnd()).isEqualTo(0);
        }

        private List<StaffElement> threeCrotchets() {
            return List.of(
                ElementType.CROTCHET.newInstance(),
                ElementType.CROTCHET.newInstance(),
                ElementType.CROTCHET.newInstance()
            );
        }
    }

    // -------------------------------------------------------------------
    // Arrow-key pitch shift of an active selection (Up/Down)
    // -------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SelectionArrowPitchShift {

        private MockedStatic<MessageCenter> messageCenterMock;

        // The pitch shift plays the anchor note when PLAY_SELECTED_NOTE is on (the default),
        // firing static PlayThread sends and spawning a real PlayThread. Mock both so the
        // tests neither leak a background thread nor depend on the MIDI receiver being null.
        private MockedStatic<PlayThread> playThreadStaticMock;
        private MockedConstruction<PlayThread> playThreadConstruction;

        @BeforeEach
        void setUp() {
            messageCenterMock = mockStatic(MessageCenter.class);
            playThreadStaticMock = mockStatic(PlayThread.class);
            playThreadConstruction = mockConstruction(PlayThread.class);
        }

        @AfterEach
        void tearDown() {
            playThreadConstruction.close();
            playThreadStaticMock.close();
            messageCenterMock.close();
        }

        @Test
        void testUpRaisesSingleSelectedNotePitchAndRecordsMutation() {
            final int originalPositionSp = 4;
            var song = new Song();
            var line = song.getLine(0);
            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(originalPositionSp);
            // An accidental is written for the staff position it sits on, so a note that leaves
            // that position gives it up. The fixture needs one for that clearing to be observable
            // at all — the ACCIDENTAL tag below comes from a fixed set and would be reported
            // whether or not anything actually changed.
            note.setAccidental(StaffElement.Accidental.SHARP);
            song.withoutMutationTracking(() -> line.addElement(note));

            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectNote(coordinator, 0);

            pressArrowKey(selectionCallback(coordinator), KeyEvent.VK_UP);

            assertThat(note.getStaffPosition()).isEqualTo(originalPositionSp - 1);
            assertThat(note.getAccidental()).isNull();

            var modification = capturedPitchModification();
            assertThat(modification.fields()).containsExactly(ElementField.PITCH, ElementField.ACCIDENTAL);
            assertThat(modification.beforeElement().getStaffPosition()).isEqualTo(originalPositionSp);
            assertThat(modification.beforeElement().getAccidental())
                .as("undo restores the accidental from the pre-move snapshot")
                .isEqualTo(StaffElement.Accidental.SHARP);
        }

        @Test
        void testDownLowersSingleSelectedNotePitchAndRecordsMutation() {
            final int originalPositionSp = 4;
            var song = new Song();
            var line = song.getLine(0);
            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(originalPositionSp);
            // See the Up test: without an accidental on the fixture, nothing observes the clear.
            note.setAccidental(StaffElement.Accidental.FLAT);
            song.withoutMutationTracking(() -> line.addElement(note));

            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectNote(coordinator, 0);

            pressArrowKey(selectionCallback(coordinator), KeyEvent.VK_DOWN);

            assertThat(note.getStaffPosition()).isEqualTo(originalPositionSp + 1);
            assertThat(note.getAccidental()).isNull();

            var modification = capturedPitchModification();
            assertThat(modification.fields()).containsExactly(ElementField.PITCH, ElementField.ACCIDENTAL);
            assertThat(modification.beforeElement().getStaffPosition()).isEqualTo(originalPositionSp);
            assertThat(modification.beforeElement().getAccidental())
                .as("undo restores the accidental from the pre-move snapshot")
                .isEqualTo(StaffElement.Accidental.FLAT);
        }

        // Pins the wiring between the arrow-key shift and the accidental reconciliation, which the
        // shift's own assertions above cannot reach. Two notes share a staff position; only the
        // first carries a sharp, so the second sounds sharp by inheriting it. Moving the first
        // away takes that sharp with it, and the second must be given one of its own or it would
        // silently change pitch — a note the user never touched.
        @Test
        void testShiftingANoteAwayWritesItsAccidentalOntoTheNoteThatInheritedIt() {
            final int sharedPositionSp = 4;
            var song = new Song();
            var line = song.getLine(0);
            var movedNote = ElementType.CROTCHET.newInstance();
            movedNote.setStaffPosition(sharedPositionSp);
            movedNote.setAccidental(StaffElement.Accidental.SHARP);
            var inheritingNote = ElementType.CROTCHET.newInstance();
            inheritingNote.setStaffPosition(sharedPositionSp);
            song.withoutMutationTracking(() -> {
                line.addElement(movedNote);
                line.addElement(inheritingNote);
            });

            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectNote(coordinator, 0);

            var inheritedPitchBefore = inheritingNote.getPitch();

            pressArrowKey(selectionCallback(coordinator), KeyEvent.VK_UP);

            assertThat(movedNote.getStaffPosition()).isEqualTo(sharedPositionSp - 1);
            assertThat(inheritingNote.getAccidental())
                .as("the note that was inheriting the sharp must now carry one of its own")
                .isEqualTo(StaffElement.Accidental.SHARP);
            assertThat(inheritingNote.getPitch())
                .as("a note the user did not touch must not change pitch")
                .isEqualTo(inheritedPitchBefore);
        }

        @Test
        void testUpShiftsEveryNoteInMultiSelection() {
            final int firstPositionSp = 0;
            final int secondPositionSp = 2;
            var song = new Song();
            var line = song.getLine(0);
            var firstNote = ElementType.CROTCHET.newInstance();
            firstNote.setStaffPosition(firstPositionSp);
            var secondNote = ElementType.CROTCHET.newInstance();
            secondNote.setStaffPosition(secondPositionSp);
            song.withoutMutationTracking(() -> {
                line.addElement(firstNote);
                line.addElement(secondNote);
            });

            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectRange(coordinator, 0, 1);

            pressArrowKey(selectionCallback(coordinator), KeyEvent.VK_UP);

            assertThat(firstNote.getStaffPosition()).isEqualTo(firstPositionSp - 1);
            assertThat(secondNote.getStaffPosition()).isEqualTo(secondPositionSp - 1);
        }

        @Test
        void testUpAtUpperBoundaryClampsAndRecordsNoMutation() {
            var song = new Song();
            var line = song.getLine(0);
            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(Staff.MIN_STAFF_POSITION_SP);
            song.withoutMutationTracking(() -> line.addElement(note));

            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectNote(coordinator, 0);

            pressArrowKey(selectionCallback(coordinator), KeyEvent.VK_UP);

            assertThat(note.getStaffPosition()).isEqualTo(Staff.MIN_STAFF_POSITION_SP);
            messageCenterMock.verify(() -> MessageCenter.post(any(Message.class)), never());
        }

        @Test
        void testDownAtLowerBoundaryClampsAndRecordsNoMutation() {
            var song = new Song();
            var line = song.getLine(0);
            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(Staff.MAX_STAFF_POSITION_SP);
            song.withoutMutationTracking(() -> line.addElement(note));

            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectNote(coordinator, 0);

            pressArrowKey(selectionCallback(coordinator), KeyEvent.VK_DOWN);

            assertThat(note.getStaffPosition()).isEqualTo(Staff.MAX_STAFF_POSITION_SP);
            messageCenterMock.verify(() -> MessageCenter.post(any(Message.class)), never());
        }

        @Test
        void testUpOnSelectedRestIsNoOp() {
            var song = new Song();
            var line = song.getLine(0);
            var rest = ElementType.CROTCHET_REST.newInstance();
            song.withoutMutationTracking(() -> line.addElement(rest));

            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectNote(coordinator, 0);

            pressArrowKey(selectionCallback(coordinator), KeyEvent.VK_UP);

            // A rest is not pitched, so the shift group is empty and nothing is mutated.
            messageCenterMock.verify(() -> MessageCenter.post(any(Message.class)), never());
        }

        @Test
        void testDownCollapsingUnselectedPrecedingGraceNoteShrinksSelectionRange() {
            final int gracePositionSp = 4;
            var song = new Song();
            var line = song.getLine(0);
            var grace = ElementType.GRACE_QUAVER.newInstance();
            grace.setStaffPosition(gracePositionSp);
            var host = ElementType.CROTCHET.newInstance();
            host.setStaffPosition(gracePositionSp - 1);
            var extra = ElementType.CROTCHET.newInstance();
            extra.setStaffPosition(gracePositionSp - 3);
            song.withoutMutationTracking(() -> {
                line.addElement(grace);
                line.addElement(host);
                line.addElement(extra);
            });

            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            // The grace note (index 0) is deliberately left out of the selection.
            ReflectionTestHelper.selectRange(coordinator, 1, 2);

            try (var optionDialogsMock = mockStatic(OptionDialogs.class)) {
                pressArrowKey(selectionCallback(coordinator), KeyEvent.VK_DOWN);

                optionDialogsMock.verify(() ->
                    OptionDialogs.showWarningMessage(any(), anyString(), anyString()));
            }

            // The grace note collapsed into the host, shifting every later index down by
            // one; the surviving selection must track the same two notes (now at 0 and 1),
            // not the stale pre-removal indices (1 and 2).
            assertThat(line.effectiveElementCount()).isEqualTo(2);
            var state = activeSelectionOrFail(coordinator);
            assertThat(state.getSelectionBegin()).isEqualTo(0);
            assertThat(state.getSelectionEnd()).isEqualTo(1);
        }

        /**
         * Captures all posted messages and returns the {@link ElementModification}
         * carried by whichever {@link SongDidChangeNotification} recorded the shift.
         */
        private ElementModification capturedPitchModification() {
            var captor = ArgumentCaptor.forClass(Message.class);
            messageCenterMock.verify(() -> MessageCenter.post(captor.capture()), atLeastOnce());

            return captor.getAllValues().stream()
                .filter(m -> m instanceof SongDidChangeNotification)
                .map(m -> (SongDidChangeNotification) m)
                .flatMap(n -> n.getMutations().stream())
                .filter(m -> m instanceof ElementModification)
                .map(m -> (ElementModification) m)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No ElementModification in captured notifications"));
        }
    }

    // -------------------------------------------------------------------
    // Shift+Left/Right extend or shrink an active selection. Each case verifies
    // the target index handed to the shared InputHandlerCallback.extendSelectionTo.
    // -------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SelectionShiftArrowExtension {

        @Test
        void testShiftRightExtendsSingleSelectionForward() {
            var coordinator = ReflectionTestHelper.createCoordinator(threeCrotchets(), List.of());
            ReflectionTestHelper.selectNote(coordinator, 0);
            var callback = selectionCallback(coordinator);

            pressShiftArrowKey(callback, KeyEvent.VK_RIGHT);

            verify(callback).extendSelectionTo(1);
        }

        @Test
        void testShiftLeftExtendsSingleSelectionBackward() {
            var coordinator = ReflectionTestHelper.createCoordinator(threeCrotchets(), List.of());
            ReflectionTestHelper.selectNote(coordinator, 2);
            var callback = selectionCallback(coordinator);

            pressShiftArrowKey(callback, KeyEvent.VK_LEFT);

            verify(callback).extendSelectionTo(1);
        }

        @Test
        void testShiftRightExtendsRangeFromMovingEnd() {
            var coordinator = ReflectionTestHelper.createCoordinator(threeCrotchets(), List.of());
            ReflectionTestHelper.selectRange(coordinator, 0, 1);
            var callback = selectionCallback(coordinator);

            pressShiftArrowKey(callback, KeyEvent.VK_RIGHT);

            verify(callback).extendSelectionTo(2);
        }

        @Test
        void testShiftLeftShrinksRangeFromMovingEnd() {
            var coordinator = ReflectionTestHelper.createCoordinator(threeCrotchets(), List.of());
            ReflectionTestHelper.selectRange(coordinator, 0, 2);
            var callback = selectionCallback(coordinator);

            pressShiftArrowKey(callback, KeyEvent.VK_LEFT);

            verify(callback).extendSelectionTo(1);
        }

        @Test
        void testShiftRightMovesTheEndOppositeTheAnchor() {
            // Anchor at index 2, selection [0..2]; the moving end is begin (0).
            var coordinator = ReflectionTestHelper.createCoordinator(threeCrotchets(), List.of());
            ReflectionTestHelper.selectRange(coordinator, 2, 0);
            var callback = selectionCallback(coordinator);

            pressShiftArrowKey(callback, KeyEvent.VK_RIGHT);

            verify(callback).extendSelectionTo(1);
        }

        @Test
        void testShiftRightAtLastElementIsNoOp() {
            var coordinator = ReflectionTestHelper.createCoordinator(threeCrotchets(), List.of());
            ReflectionTestHelper.selectNote(coordinator, 2);
            var callback = selectionCallback(coordinator);

            pressShiftArrowKey(callback, KeyEvent.VK_RIGHT);

            verify(callback, never()).extendSelectionTo(anyInt());
        }

        @Test
        void testShiftLeftAtFirstElementIsNoOp() {
            var coordinator = ReflectionTestHelper.createCoordinator(threeCrotchets(), List.of());
            ReflectionTestHelper.selectNote(coordinator, 0);
            var callback = selectionCallback(coordinator);

            pressShiftArrowKey(callback, KeyEvent.VK_LEFT);

            verify(callback, never()).extendSelectionTo(anyInt());
        }

        @Test
        void testShiftArrowWithNoActiveSelectionIsNoOp() {
            // Line 0 is active but nothing is selected, so there is nothing to extend.
            var coordinator = ReflectionTestHelper.createCoordinator(threeCrotchets(), List.of());
            var callback = selectionCallback(coordinator);

            pressShiftArrowKey(callback, KeyEvent.VK_RIGHT);

            verify(callback, never()).extendSelectionTo(anyInt());
        }

        private List<StaffElement> threeCrotchets() {
            return List.of(
                ElementType.CROTCHET.newInstance(),
                ElementType.CROTCHET.newInstance(),
                ElementType.CROTCHET.newInstance()
            );
        }
    }

    // -------------------------------------------------------------------
    // Row 70: installKeyBindings registers one binding per key code
    // -------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class InstallKeyBindings {

        @Test
        void testInstallKeyBindingsRegistersOneBindingPerKeyCode() {
            var callback = mock(InputHandlerCallback.class);
            var handler = new ScoreInputHandler(callback);
            var component = new JPanel();

            var bindings = handler.installKeyBindings(component);

            // 7 plain KEY_CODES bindings + shift-Left/Right extension bindings
            // + the plain-B beam-with-previous binding.
            final int expectedBindingCount = 10;
            assertThat(bindings).hasSize(expectedBindingCount);

            var inputMap = component.getInputMap(JPanel.WHEN_FOCUSED);
            var actionMap = component.getActionMap();

            for (var entry : bindings.entrySet()) {
                KeyStroke keystroke = entry.getKey();
                Object actionKey = entry.getValue();

                assertThat(inputMap.get(keystroke)).isEqualTo(actionKey);
                assertThat(actionMap.get(actionKey)).isNotNull();
            }
        }
    }

    // -------------------------------------------------------------------
    // Plain-B beam-with-previous binding
    // -------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class BeamKeyBinding {

        @Test
        void testInstallKeyBindingsRegistersPlainBButNotModifiedVariants() {
            var callback = mock(InputHandlerCallback.class);
            var handler = new ScoreInputHandler(callback);
            var component = new JPanel();

            var bindings = handler.installKeyBindings(component);

            assertThat(bindings).containsKey(KeyStroke.getKeyStroke(KeyEvent.VK_B, 0));
            assertThat(bindings).doesNotContainKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_B, UIUtils.MENU_SHORTCUT_MASK));
            assertThat(bindings).doesNotContainKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.SHIFT_DOWN_MASK));
            assertThat(bindings).doesNotContainKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.ALT_DOWN_MASK));
        }

        @Test
        void testPlainBInEditModeConsumesTheKeyAndPostsToggleBeamWithPreviousCommand() {
            var callback = mock(InputHandlerCallback.class);
            when(callback.getMode()).thenReturn(Mode.EDIT);
            var graceModeManager = mock(GraceModeManager.class);
            var pasteModeManager = mock(PasteModeManager.class);

            try (
                MockedStatic<EditModeManager> emm = mockStatic(EditModeManager.class);
                MockedStatic<MessageCenter> mc = mockStatic(MessageCenter.class)
            ) {
                emm.when(EditModeManager::getGraceModeManager).thenReturn(graceModeManager);
                emm.when(EditModeManager::getPasteModeManager).thenReturn(pasteModeManager);

                var consumed = pressBeamKey(callback);

                assertThat(consumed).as("binding handled the key").isTrue();
                mc.verify(() -> MessageCenter.post(any(ToggleBeamWithPreviousCommand.class)));
            }
        }

        @Test
        void testPlainBInSelectModeLeavesTheKeyForTheToggleBeamAccelerator() {
            var callback = mock(InputHandlerCallback.class);
            when(callback.getMode()).thenReturn(Mode.SELECT);

            try (MockedStatic<MessageCenter> mc = mockStatic(MessageCenter.class)) {
                var consumed = pressBeamKey(callback);

                assertThat(consumed)
                    .as("binding must not swallow b outside edit mode")
                    .isFalse();
                mc.verify(() -> MessageCenter.post(any(ToggleBeamWithPreviousCommand.class)), never());
            }
        }

        @Test
        void testPlainBWhileGraceModeInProgressLeavesTheKeyUnconsumed() {
            var callback = mock(InputHandlerCallback.class);
            when(callback.getMode()).thenReturn(Mode.EDIT);
            var graceModeManager = mock(GraceModeManager.class);
            when(graceModeManager.isInProgress()).thenReturn(true);
            var pasteModeManager = mock(PasteModeManager.class);

            try (
                MockedStatic<EditModeManager> emm = mockStatic(EditModeManager.class);
                MockedStatic<MessageCenter> mc = mockStatic(MessageCenter.class)
            ) {
                emm.when(EditModeManager::getGraceModeManager).thenReturn(graceModeManager);
                emm.when(EditModeManager::getPasteModeManager).thenReturn(pasteModeManager);

                var consumed = pressBeamKey(callback);

                assertThat(consumed).as("binding handled the key").isFalse();
                mc.verify(() -> MessageCenter.post(any(ToggleBeamWithPreviousCommand.class)), never());
            }
        }

        @Test
        void testPlainBWhilePasteModeInProgressLeavesTheKeyUnconsumed() {
            var callback = mock(InputHandlerCallback.class);
            when(callback.getMode()).thenReturn(Mode.EDIT);
            var graceModeManager = mock(GraceModeManager.class);
            var pasteModeManager = mock(PasteModeManager.class);
            when(pasteModeManager.isInProgress()).thenReturn(true);

            try (
                MockedStatic<EditModeManager> emm = mockStatic(EditModeManager.class);
                MockedStatic<MessageCenter> mc = mockStatic(MessageCenter.class)
            ) {
                emm.when(EditModeManager::getGraceModeManager).thenReturn(graceModeManager);
                emm.when(EditModeManager::getPasteModeManager).thenReturn(pasteModeManager);

                var consumed = pressBeamKey(callback);

                assertThat(consumed).as("binding handled the key").isFalse();
                mc.verify(() -> MessageCenter.post(any(ToggleBeamWithPreviousCommand.class)), never());
            }
        }

        /**
         * Installs key bindings on a fresh component and dispatches plain {@code b} the way
         * Swing does: an enabled binding runs and the key is consumed; a disabled one is
         * skipped without running, leaving the key to fall through to the root pane, where
         * the toggle-beam action's own plain-{@code b} accelerator lives.
         * <p>
         * Calling {@code actionPerformed} unconditionally instead would hide the difference
         * that matters — an action that runs and decides to do nothing still swallows the
         * key, which is what would break the select-mode shortcut.
         *
         * @param callback The mode/state source the binding consults
         * @return Whether the binding consumed the key
         */
        private boolean pressBeamKey(InputHandlerCallback callback) {
            var handler = new ScoreInputHandler(callback);
            var component = new JPanel();
            handler.installKeyBindings(component);

            var action = component.getActionMap().get(
                component.getInputMap(JPanel.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(KeyEvent.VK_B, 0)));

            if (!action.isEnabled()) {
                return false;
            }

            action.actionPerformed(new ActionEvent(component, ActionEvent.ACTION_PERFORMED, ""));

            return true;
        }
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    private MouseEvent mouseEvent(int id, boolean popupTrigger) {
        return new MouseEvent(
            mock(Component.class), id, 0L, 0, 0, 0, 0, 0, 1, popupTrigger, MouseEvent.BUTTON1
        );
    }

    private MouseEvent mouseClickEvent(int button) {
        return new MouseEvent(
            mock(Component.class), MouseEvent.MOUSE_CLICKED, 0L, 0, 0, 0, 0, 0, 1, false, button
        );
    }

    private KeyEvent keyEvent(int keyCode) {
        return new KeyEvent(
            mock(Component.class), KeyEvent.KEY_PRESSED, 0L, 0, keyCode, KeyEvent.CHAR_UNDEFINED
        );
    }

    /**
     * Installs key bindings on a fresh component and fires the arrow-key action
     * for {@code keyCode}, exercising {@code ScoreInputHandler.KeyAction} exactly
     * as a real key press would.
     */
    private void pressArrowKey(InputHandlerCallback callback, int keyCode) {
        var handler = new ScoreInputHandler(callback);
        var component = new JPanel();
        handler.installKeyBindings(component);

        var action = component.getActionMap().get(
            component.getInputMap(JPanel.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(keyCode, 0)));
        action.actionPerformed(new ActionEvent(component, ActionEvent.ACTION_PERFORMED, ""));
    }

    /**
     * Fires the Shift+arrow action for {@code keyCode}, exercising the selection
     * extension branch of {@code ScoreInputHandler.KeyAction}.
     */
    private void pressShiftArrowKey(InputHandlerCallback callback, int keyCode) {
        var handler = new ScoreInputHandler(callback);
        var component = new JPanel();
        handler.installKeyBindings(component);

        var action = component.getActionMap().get(
            component.getInputMap(JPanel.WHEN_FOCUSED).get(
                KeyStroke.getKeyStroke(keyCode, InputEvent.SHIFT_DOWN_MASK)));
        action.actionPerformed(new ActionEvent(component, ActionEvent.ACTION_PERFORMED, ""));
    }

    /** A callback whose only wired behavior is exposing {@code coordinator}. */
    private InputHandlerCallback selectionCallback(SelectionCoordinator coordinator) {
        var callback = mock(InputHandlerCallback.class);
        when(callback.getSelectionCoordinator()).thenReturn(coordinator);
        return callback;
    }

    /** Returns {@code coordinator}'s active selection state, failing the test if none exists. */
    private LineSelectionState activeSelectionOrFail(SelectionCoordinator coordinator) {
        var state = coordinator.getActiveSelection();

        if (state == null) {
            throw new AssertionError("Expected an active selection");
        }

        return state;
    }

    /**
     * Builds a two-line {@link SelectionCoordinator} backed by a real {@link Song}:
     * line 0 with {@code firstLineNoteCount} crotchets, line 1 (the new last line,
     * carrying the auto-maintained terminal) with {@code secondLineNoteCount}
     * crotchets. No line is activated.
     */
    private SelectionCoordinator twoLineCoordinator(int firstLineNoteCount, int secondLineNoteCount) {
        var song = new Song();
        var firstLine = song.getLine(0);

        song.withoutMutationTracking(() -> {
            for (var i = 0; i < firstLineNoteCount; i++) {
                firstLine.addElement(ElementType.CROTCHET.newInstance());
            }
        });

        var secondLine = new Line(song);

        song.withoutMutationTracking(() -> {
            for (var i = 0; i < secondLineNoteCount; i++) {
                secondLine.addElement(ElementType.CROTCHET.newInstance());
            }
        });

        song.addLine(secondLine);

        var coordinator = new SelectionCoordinator(mock(ScoreView.class));
        coordinator.registerLineState(0, new LineSelectionState(firstLine));
        coordinator.registerLineState(1, new LineSelectionState(secondLine));
        return coordinator;
    }
}
