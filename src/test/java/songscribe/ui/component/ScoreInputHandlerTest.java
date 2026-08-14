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
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.crotchetRest;
import static songscribe.dom.StaffElementFactory.graceQuaver;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;

import javax.swing.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.engraving.Staff;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.command.DeselectCommand;
import songscribe.message.command.ToggleBeamWithPreviousCommand;
import songscribe.message.command.ToggleFallOnLastInsertionCommand;
import songscribe.message.command.ToggleGlissandoWithPreviousCommand;
import songscribe.message.command.ToggleTieWithPreviousCommand;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.ElementModification;
import songscribe.message.mutation.Mutation;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.ui.Mode;
import songscribe.ui.OptionDialogs;
import songscribe.ui.action.ActionsTestSupport;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.component.score.ScoreComponent;
import songscribe.ui.selection.Selection;
import songscribe.ui.selection.ReflectionTestHelper;
import songscribe.ui.selection.SelectionCoordinator;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.edit.GraceModeManager;
import songscribe.ui.edit.InsertionPointMode;
import songscribe.ui.playback.PlaybackController;
import songscribe.ui.playback.PlayThread;
import songscribe.util.UIUtils;

class ScoreInputHandlerTest extends UnitTest {

    // installKeyBindings takes each last-insertion keystroke off the menu action the key
    // falls through to, so those constants have to be populated before it runs.
    @BeforeEach
    void initializeActions() {
        ActionsTestSupport.initializeActions();
    }

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
            var insertionPointMode = mock(InsertionPointMode.class);

            try (var emm = mockStatic(EditModeManager.class)) {
                emm.when(EditModeManager::getInsertionPointMode).thenReturn(insertionPointMode);

                handler.mouseClicked(mouseClickEvent(MouseEvent.BUTTON1));

                verify(callback).requestFocusInWindow();
            }
        }

        @Test
        void testMouseClickedButton1WhenAPlacementIsPendingCancelsIt() {
            var callback = mock(InputHandlerCallback.class);
            when(callback.getSelectionCoordinator()).thenReturn(mock(SelectionCoordinator.class));
            var handler = new ScoreInputHandler(callback);
            var insertionPointMode = mock(InsertionPointMode.class);
            when(insertionPointMode.isInProgress()).thenReturn(true);

            try (var emm = mockStatic(EditModeManager.class)) {
                emm.when(EditModeManager::getInsertionPointMode).thenReturn(insertionPointMode);

                handler.mouseClicked(mouseClickEvent(MouseEvent.BUTTON1));

                verify(insertionPointMode).cancel();
            }
        }

        @Test
        void testMouseClickedButton1WhenNoPlacementIsPendingDoesNotCancel() {
            var callback = mock(InputHandlerCallback.class);
            when(callback.getSelectionCoordinator()).thenReturn(mock(SelectionCoordinator.class));
            var handler = new ScoreInputHandler(callback);
            var insertionPointMode = mock(InsertionPointMode.class);
            when(insertionPointMode.isInProgress()).thenReturn(false);

            try (var emm = mockStatic(EditModeManager.class)) {
                emm.when(EditModeManager::getInsertionPointMode).thenReturn(insertionPointMode);

                handler.mouseClicked(mouseClickEvent(MouseEvent.BUTTON1));

                verify(insertionPointMode, never()).cancel();
            }
        }

        @Test
        void testMouseClickedButton1InSelectModeOutsideAnyLinePostsDeselectCommand() {
            var callback = mock(InputHandlerCallback.class);
            var coordinator = mock(SelectionCoordinator.class);
            when(callback.getSelectionCoordinator()).thenReturn(coordinator);
            when(coordinator.isInSelectMode()).thenReturn(true);
            var handler = new ScoreInputHandler(callback);
            var insertionPointMode = mock(InsertionPointMode.class);

            try (
                var emm = mockStatic(EditModeManager.class);
                var mc = mockStatic(MessageCenter.class)
            ) {
                emm.when(EditModeManager::getInsertionPointMode).thenReturn(insertionPointMode);

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
            var insertionPointMode = mock(InsertionPointMode.class);

            try (
                var emm = mockStatic(EditModeManager.class);
                var mc = mockStatic(MessageCenter.class)
            ) {
                emm.when(EditModeManager::getInsertionPointMode).thenReturn(insertionPointMode);

                handler.mouseClicked(mouseClickEvent(MouseEvent.BUTTON1));

                mc.verify(() -> MessageCenter.post(any(DeselectCommand.class)), never());
            }
        }

        /**
         * Unlike the Escape key — where the first press only cancels the pending placement and leaves
         * the selection intact — a click does both at once, since the click has already
         * moved the user's attention off the selection.
         */
        @Test
        void testMouseClickedButton1InSelectModeWhileAPlacementIsPendingCancelsAndDeselects() {
            var callback = mock(InputHandlerCallback.class);
            var coordinator = mock(SelectionCoordinator.class);
            when(callback.getSelectionCoordinator()).thenReturn(coordinator);
            when(coordinator.isInSelectMode()).thenReturn(true);
            var handler = new ScoreInputHandler(callback);
            var insertionPointMode = mock(InsertionPointMode.class);
            when(insertionPointMode.isInProgress()).thenReturn(true);

            try (
                var emm = mockStatic(EditModeManager.class);
                var mc = mockStatic(MessageCenter.class)
            ) {
                emm.when(EditModeManager::getInsertionPointMode).thenReturn(insertionPointMode);

                handler.mouseClicked(mouseClickEvent(MouseEvent.BUTTON1));

                verify(insertionPointMode).cancel();
                mc.verify(() -> MessageCenter.post(any(DeselectCommand.class)));
            }
        }

        /**
         * The non-BUTTON1 guard must skip every effect of the handler, not just the focus
         * request, so the preconditions for all three are satisfied here.
         */
        @Test
        void testMouseClickedNonButton1SkipsPlacementCancelAndDeselect() {
            var callback = mock(InputHandlerCallback.class);
            var coordinator = mock(SelectionCoordinator.class);
            when(callback.getSelectionCoordinator()).thenReturn(coordinator);
            when(coordinator.isInSelectMode()).thenReturn(true);
            var handler = new ScoreInputHandler(callback);
            var insertionPointMode = mock(InsertionPointMode.class);
            when(insertionPointMode.isInProgress()).thenReturn(true);

            try (
                var emm = mockStatic(EditModeManager.class);
                var mc = mockStatic(MessageCenter.class)
            ) {
                emm.when(EditModeManager::getInsertionPointMode).thenReturn(insertionPointMode);

                handler.mouseClicked(mouseClickEvent(MouseEvent.BUTTON3));

                verify(insertionPointMode, never()).cancel();
                mc.verify(() -> MessageCenter.post(any(DeselectCommand.class)), never());
                verify(callback, never()).requestFocusInWindow();
            }
        }

        // -------------------------------------------------------------------
        // Double-click dispatch to a ScoreComponent's openEditor()
        // -------------------------------------------------------------------

        private static final int CONTAINER_SIZE_PX = 200;
        private static final int SCORE_COMPONENT_X_PX = 20;
        private static final int SCORE_COMPONENT_Y_PX = 20;
        private static final int SCORE_COMPONENT_WIDTH_PX = 100;
        private static final int SCORE_COMPONENT_HEIGHT_PX = 40;
        private static final int SCORE_COMPONENT_CLICK_X_PX =
            SCORE_COMPONENT_X_PX + SCORE_COMPONENT_WIDTH_PX / 2;
        private static final int SCORE_COMPONENT_CLICK_Y_PX =
            SCORE_COMPONENT_Y_PX + SCORE_COMPONENT_HEIGHT_PX / 2;
        private static final int EMPTY_AREA_CLICK_X_PX = 180;
        private static final int EMPTY_AREA_CLICK_Y_PX = 180;
        private static final int SINGLE_CLICK = 1;

        /** What the {@link StubScoreComponent} under the click answers from {@code openEditor()}. */
        private enum EditorOutcome {
            OPENS,
            DOES_NOT_OPEN
        }

        /** What {@code PlaybackController.isPlaying()} answers while the click is dispatched. */
        private enum PlaybackState {
            PLAYING,
            NOT_PLAYING
        }

        @Test
        void testMouseClickedDoubleClickOnScoreComponentWhoseOpenEditorAnswersTrueIsConsumed() {
            var tree = componentTreeWithScoreComponent(EditorOutcome.OPENS);
            var callback = mock(InputHandlerCallback.class);
            var handler = new ScoreInputHandler(callback);
            var insertionPointMode = mock(InsertionPointMode.class);

            try (
                var emm = mockStatic(EditModeManager.class);
                var mc = mockStatic(MessageCenter.class);
                var playback = mockStatic(PlaybackController.class)
            ) {
                emm.when(EditModeManager::getInsertionPointMode).thenReturn(insertionPointMode);
                playback.when(PlaybackController::isPlaying).thenReturn(false);

                handler.mouseClicked(clickOnScoreComponent(tree, UIUtils.DOUBLE_CLICK_COUNT));

                mc.verify(() -> MessageCenter.post(any(DeselectCommand.class)), never());
                verify(insertionPointMode, never()).cancel();
            }

            assertThat(tree.scoreComponent().openEditorCallCount()).isEqualTo(1);
        }

        @Test
        void testMouseClickedSingleClickOnScoreComponentFallsThroughToExistingPath() {
            var tree = componentTreeWithScoreComponent(EditorOutcome.OPENS);

            clickFallsThroughToExistingPath(
                clickOnScoreComponent(tree, SINGLE_CLICK), PlaybackState.NOT_PLAYING);

            // Click count 1 fails isLeftDoubleClick before scoreComponentAt is ever consulted.
            assertThat(tree.scoreComponent().openEditorCallCount()).isZero();
        }

        @Test
        void testMouseClickedDoubleClickOnScoreComponentWhoseOpenEditorAnswersFalseFallsThroughToExistingPath() {
            var tree = componentTreeWithScoreComponent(EditorOutcome.DOES_NOT_OPEN);

            clickFallsThroughToExistingPath(
                clickOnScoreComponent(tree, UIUtils.DOUBLE_CLICK_COUNT), PlaybackState.NOT_PLAYING);

            assertThat(tree.scoreComponent().openEditorCallCount()).isEqualTo(1);
        }

        @Test
        void testMouseClickedDoubleClickWhereNoScoreComponentSitsFallsThroughToExistingPath() {
            var container = new JPanel();
            container.setSize(CONTAINER_SIZE_PX, CONTAINER_SIZE_PX);

            clickFallsThroughToExistingPath(
                clickEvent(container, EMPTY_AREA_CLICK_X_PX, EMPTY_AREA_CLICK_Y_PX, UIUtils.DOUBLE_CLICK_COUNT),
                PlaybackState.NOT_PLAYING);
        }

        @Test
        void testMouseClickedDoubleClickDuringPlaybackDoesNotDispatchToOpenEditorAndFallsThrough() {
            var tree = componentTreeWithScoreComponent(EditorOutcome.OPENS);

            clickFallsThroughToExistingPath(
                clickOnScoreComponent(tree, UIUtils.DOUBLE_CLICK_COUNT), PlaybackState.PLAYING);

            // isPlaying() short-circuits the dispatch before scoreComponentAt is consulted.
            assertThat(tree.scoreComponent().openEditorCallCount()).isZero();
        }

        /**
         * {@code scoreComponentAt} answers with the deepest match: the point also lands on a
         * plain child nested inside the {@link StubScoreComponent}, which is not itself a
         * score component, so the search must fall back to the ancestor it descended
         * through. Nothing in the production tree nests a component inside a
         * {@code ScoreComponent} today, so this is the only coverage that branch gets.
         */
        @Test
        void testMouseClickedDoubleClickOnChildOfScoreComponentStillReachesOpenEditor() {
            var tree = componentTreeWithScoreComponent(EditorOutcome.OPENS);
            var child = new JPanel();
            child.setBounds(0, 0, SCORE_COMPONENT_WIDTH_PX, SCORE_COMPONENT_HEIGHT_PX);
            tree.scoreComponent().add(child);

            dispatchDoubleClickOnScoreComponent(tree);

            assertThat(tree.scoreComponent().openEditorCallCount()).isEqualTo(1);
        }

        /**
         * The failure mode {@code scoreComponentAt} resolves by bounds to avoid: an overlay
         * that is a <em>sibling</em> of the score component — the shape {@code LyricEditor}
         * and {@code LineOverlayComponent} have against {@code MainPanel} — covering it and
         * sitting above it in z-order. A stacking-order lookup answers with the overlay and
         * never reaches the score component underneath, silently killing the gesture; a
         * bounds search steps past the overlay because nothing in it is a score component.
         * <p>
         * No such overlay reaches the title band in the production tree today, so this test
         * is the only thing standing between a future one and a gesture that quietly stops
         * working.
         */
        @Test
        void testMouseClickedDoubleClickUnderASiblingOverlayStillReachesOpenEditor() {
            var tree = componentTreeWithScoreComponent(EditorOutcome.OPENS);
            var overlay = new JPanel();
            overlay.setBounds(
                SCORE_COMPONENT_X_PX, SCORE_COMPONENT_Y_PX, SCORE_COMPONENT_WIDTH_PX, SCORE_COMPONENT_HEIGHT_PX);

            // Index 0 is the top of the z-order, so a stacking-order lookup finds the
            // overlay before the score component it covers.
            tree.container().add(overlay, 0);

            dispatchDoubleClickOnScoreComponent(tree);

            assertThat(tree.scoreComponent().openEditorCallCount()).isEqualTo(1);
        }

        /**
         * Dispatches a left double-click at the center of {@code tree}'s score component,
         * outside playback, so a test can assert what the dispatch reached.
         * <p>
         * The fall-through path is stubbed out even though these tests expect the dispatch
         * to consume the click: should it ever not, the click carries on into that path,
         * and an unstubbed collaborator would abort the test with a
         * {@code NullPointerException} from deep inside {@code mouseClicked} instead of
         * the {@code openEditor} assertion the test was written to report.
         */
        private void dispatchDoubleClickOnScoreComponent(ScoreComponentTree tree) {
            var callback = mock(InputHandlerCallback.class);
            var coordinator = mock(SelectionCoordinator.class);
            when(callback.getSelectionCoordinator()).thenReturn(coordinator);
            var handler = new ScoreInputHandler(callback);
            var insertionPointMode = mock(InsertionPointMode.class);

            try (
                var emm = mockStatic(EditModeManager.class);
                var mc = mockStatic(MessageCenter.class);
                var playback = mockStatic(PlaybackController.class)
            ) {
                emm.when(EditModeManager::getInsertionPointMode).thenReturn(insertionPointMode);
                playback.when(PlaybackController::isPlaying).thenReturn(false);

                handler.mouseClicked(clickOnScoreComponent(tree, UIUtils.DOUBLE_CLICK_COUNT));
            }
        }

        /**
         * Runs {@code mouseClicked} with select mode and an in-progress paste armed, and
         * playback in {@code playbackState}, then asserts the click fell through to the
         * existing paste-cancel / deselect / focus path — the assertion every fall-through
         * scenario above shares.
         */
        private void clickFallsThroughToExistingPath(MouseEvent event, PlaybackState playbackState) {
            var callback = mock(InputHandlerCallback.class);
            var coordinator = mock(SelectionCoordinator.class);
            when(callback.getSelectionCoordinator()).thenReturn(coordinator);
            when(coordinator.isInSelectMode()).thenReturn(true);
            var handler = new ScoreInputHandler(callback);
            var insertionPointMode = mock(InsertionPointMode.class);
            when(insertionPointMode.isInProgress()).thenReturn(true);

            try (
                var emm = mockStatic(EditModeManager.class);
                var mc = mockStatic(MessageCenter.class);
                var playback = mockStatic(PlaybackController.class)
            ) {
                emm.when(EditModeManager::getInsertionPointMode).thenReturn(insertionPointMode);
                playback.when(PlaybackController::isPlaying).thenReturn(playbackState == PlaybackState.PLAYING);

                handler.mouseClicked(event);

                verify(insertionPointMode).cancel();
                mc.verify(() -> MessageCenter.post(any(DeselectCommand.class)));
                verify(callback).requestFocusInWindow();
            }
        }

        /**
         * A container sized to hold a {@link StubScoreComponent} at a fixed position, for tests
         * that dispatch a click through {@code scoreComponentAt}'s real bounds search — a
         * mocked {@code Component} has no bounds for that search to test the click against.
         */
        private ScoreComponentTree componentTreeWithScoreComponent(EditorOutcome editorOutcome) {
            var container = new JPanel();
            container.setSize(CONTAINER_SIZE_PX, CONTAINER_SIZE_PX);
            var scoreComponent = new StubScoreComponent(editorOutcome);
            scoreComponent.setBounds(
                SCORE_COMPONENT_X_PX, SCORE_COMPONENT_Y_PX, SCORE_COMPONENT_WIDTH_PX, SCORE_COMPONENT_HEIGHT_PX);
            container.add(scoreComponent);
            return new ScoreComponentTree(container, scoreComponent);
        }

        private record ScoreComponentTree(JPanel container, StubScoreComponent scoreComponent) {
        }

        /**
         * A click of {@code clickCount} at the center of {@code tree}'s
         * {@link StubScoreComponent} — where every dispatch test above aims.
         */
        private MouseEvent clickOnScoreComponent(ScoreComponentTree tree, int clickCount) {
            return clickEvent(
                tree.container(), SCORE_COMPONENT_CLICK_X_PX, SCORE_COMPONENT_CLICK_Y_PX, clickCount);
        }

        /**
         * Builds a left-button click event on {@code component} at ({@code x}, {@code y}) in
         * its own coordinate space — the same space {@code scoreComponentAt} resolves through —
         * with the given click count.
         */
        private MouseEvent clickEvent(Component component, int x, int y, int clickCount) {
            return new MouseEvent(
                component, MouseEvent.MOUSE_CLICKED, 0L, 0, x, y, x, y, clickCount, false, MouseEvent.BUTTON1
            );
        }

        /**
         * A {@link ScoreComponent} stub that answers {@code openEditor()} with a fixed value and
         * counts how many times it was called, so a test can assert whether the dispatch
         * reached this specific component.
         */
        private static final class StubScoreComponent extends ScoreComponent {

            private final EditorOutcome editorOutcome;
            private int openEditorCallCount = 0;

            StubScoreComponent(EditorOutcome editorOutcome) {
                this.editorOutcome = editorOutcome;
            }

            @Override
            protected void render(Graphics2D g2) {
                // Not exercised by these tests.
            }

            @Override
            public boolean openEditor() {
                openEditorCallCount++;
                return editorOutcome == EditorOutcome.OPENS;
            }

            int openEditorCallCount() {
                return openEditorCallCount;
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
        var event = popupTriggerEvent(MouseEvent.MOUSE_PRESSED);

        handler.mousePressed(event);

        verifyNoInteractions(callback);
    }

    @Test
    void testMouseReleasedIsNoOpForPopupTriggerEvent() {
        var callback = mock(InputHandlerCallback.class);
        var handler = new ScoreInputHandler(callback);
        var event = popupTriggerEvent(MouseEvent.MOUSE_RELEASED);

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

            try (var lc = mockStatic(LineComponent.class)) {
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
            var insertionPointMode = mock(InsertionPointMode.class);
            when(graceModeManager.isInProgress()).thenReturn(true);

            try (var emm = mockStatic(EditModeManager.class)) {
                emm.when(EditModeManager::getGraceModeManager).thenReturn(graceModeManager);
                emm.when(EditModeManager::getInsertionPointMode).thenReturn(insertionPointMode);

                var event = keyEvent(KeyEvent.VK_ESCAPE);
                handler.keyPressed(event);

                verify(graceModeManager).keyPressed(event);
            }
        }

        @Test
        void testKeyPressedEscapeWhenAPlacementIsPendingCancelsItAndDoesNotDeselect() {
            var callback = mock(InputHandlerCallback.class);
            when(callback.getMode()).thenReturn(Mode.SELECT);
            var window = mock(Window.class);
            when(callback.getWindow()).thenReturn(window);
            var handler = new ScoreInputHandler(callback);
            var graceModeManager = mock(GraceModeManager.class);
            var insertionPointMode = mock(InsertionPointMode.class);
            when(graceModeManager.isInProgress()).thenReturn(false);
            when(insertionPointMode.isInProgress()).thenReturn(true);

            try (
                var emm = mockStatic(EditModeManager.class);
                var mc = mockStatic(MessageCenter.class)
            ) {
                emm.when(EditModeManager::getGraceModeManager).thenReturn(graceModeManager);
                emm.when(EditModeManager::getInsertionPointMode).thenReturn(insertionPointMode);

                handler.keyPressed(keyEvent(KeyEvent.VK_ESCAPE));

                verify(insertionPointMode).cancel();
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
            var insertionPointMode = mock(InsertionPointMode.class);
            when(graceModeManager.isInProgress()).thenReturn(false);

            try (
                var emm = mockStatic(EditModeManager.class);
                var mc = mockStatic(MessageCenter.class)
            ) {
                emm.when(EditModeManager::getGraceModeManager).thenReturn(graceModeManager);
                emm.when(EditModeManager::getInsertionPointMode).thenReturn(insertionPointMode);

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
            var insertionPointMode = mock(InsertionPointMode.class);
            when(graceModeManager.isInProgress()).thenReturn(false);

            try (
                var emm = mockStatic(EditModeManager.class);
                var ui = mockStatic(UIUtils.class);
                var mc = mockStatic(MessageCenter.class)
            ) {
                emm.when(EditModeManager::getGraceModeManager).thenReturn(graceModeManager);
                emm.when(EditModeManager::getInsertionPointMode).thenReturn(insertionPointMode);
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
            var insertionPointMode = mock(InsertionPointMode.class);
            when(graceModeManager.isInProgress()).thenReturn(false);

            try (
                var emm = mockStatic(EditModeManager.class);
                var ui = mockStatic(UIUtils.class);
                var mc = mockStatic(MessageCenter.class)
            ) {
                emm.when(EditModeManager::getGraceModeManager).thenReturn(graceModeManager);
                emm.when(EditModeManager::getInsertionPointMode).thenReturn(insertionPointMode);
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
            var insertionPointMode = mock(InsertionPointMode.class);
            when(graceModeManager.isInProgress()).thenReturn(false);

            try (
                var emm = mockStatic(EditModeManager.class);
                var mc = mockStatic(MessageCenter.class)
            ) {
                emm.when(EditModeManager::getGraceModeManager).thenReturn(graceModeManager);
                emm.when(EditModeManager::getInsertionPointMode).thenReturn(insertionPointMode);

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

            try (var lc = mockStatic(LineComponent.class)) {
                handler.keyReleased(keyEvent(KeyEvent.VK_ALT));

                lc.verify(() -> LineComponent.setAltPressed(false));
            }
        }
    }

    // -------------------------------------------------------------------
    // Rows 246-254: KeyAction(VK_ENTER) places the paste-mode fragment or,
    // with no placement pending, opens the lyric editor on the selection — either
    // way returning before touching the selection coordinator
    // -------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class EnterKeyPressed {

        @Test
        void testEnterWithAPlacementPendingCallsPlaceAndSkipsSelectionHandling() {
            var callback = mock(InputHandlerCallback.class);
            var insertionPointMode = mock(InsertionPointMode.class);
            when(insertionPointMode.isInProgress()).thenReturn(true);

            try (var emm = mockStatic(EditModeManager.class)) {
                emm.when(EditModeManager::getInsertionPointMode).thenReturn(insertionPointMode);

                pressArrowKey(callback, KeyEvent.VK_ENTER);

                verify(insertionPointMode).place();
                verify(callback, never()).editLyricOnSelection();
                // The VK_ENTER branch returns immediately, so the arrow-key
                // selection path below it must never run.
                verify(callback, never()).getSelectionCoordinator();
            }
        }

        @Test
        void testEnterWithNoPlacementPendingOpensLyricEditorOnSelection() {
            var callback = mock(InputHandlerCallback.class);
            var insertionPointMode = mock(InsertionPointMode.class);
            when(insertionPointMode.isInProgress()).thenReturn(false);

            try (var emm = mockStatic(EditModeManager.class)) {
                emm.when(EditModeManager::getInsertionPointMode).thenReturn(insertionPointMode);

                pressArrowKey(callback, KeyEvent.VK_ENTER);

                verify(callback).editLyricOnSelection();
                verify(insertionPointMode, never()).place();
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

            var range = selectedRangeOrFail(coordinator);
            assertThat(coordinator.getActiveLineIndex()).isEqualTo(0);
            assertThat(range.begin()).isEqualTo(0);
            assertThat(range.end()).isEqualTo(0);
        }

        @Test
        void testRightMovesSingleSelectionToNextElement() {
            var coordinator = ReflectionTestHelper.createCoordinator(threeCrotchets(), List.of());
            ReflectionTestHelper.selectNote(coordinator, 0);

            pressArrowKey(selectionCallback(coordinator), KeyEvent.VK_RIGHT);

            var range = selectedRangeOrFail(coordinator);
            assertThat(coordinator.getActiveLineIndex()).isEqualTo(0);
            assertThat(range.begin()).isEqualTo(1);
            assertThat(range.end()).isEqualTo(1);
        }

        @Test
        void testLeftOnMultiSelectionCollapsesToNextToLastElement() {
            var coordinator = ReflectionTestHelper.createCoordinator(threeCrotchets(), List.of());
            ReflectionTestHelper.selectRange(coordinator, 0, 2);

            pressArrowKey(selectionCallback(coordinator), KeyEvent.VK_LEFT);

            var range = selectedRangeOrFail(coordinator);
            assertThat(range.begin()).isEqualTo(1);
            assertThat(range.end()).isEqualTo(1);
        }

        @Test
        void testRightOnMultiSelectionCollapsesToSecondElement() {
            var coordinator = ReflectionTestHelper.createCoordinator(threeCrotchets(), List.of());
            ReflectionTestHelper.selectRange(coordinator, 0, 2);

            pressArrowKey(selectionCallback(coordinator), KeyEvent.VK_RIGHT);

            var range = selectedRangeOrFail(coordinator);
            assertThat(range.begin()).isEqualTo(1);
            assertThat(range.end()).isEqualTo(1);
        }

        @Test
        void testLeftAtFirstElementWithNoPreviousLineIsNoOp() {
            var coordinator = ReflectionTestHelper.createCoordinator(threeCrotchets(), List.of());
            ReflectionTestHelper.selectNote(coordinator, 0);

            pressArrowKey(selectionCallback(coordinator), KeyEvent.VK_LEFT);

            var range = selectedRangeOrFail(coordinator);
            assertThat(coordinator.getActiveLineIndex()).isEqualTo(0);
            assertThat(range.begin()).isEqualTo(0);
            assertThat(range.end()).isEqualTo(0);
        }

        @Test
        void testRightAtLastElementWithNoNextLineIsNoOp() {
            var coordinator = ReflectionTestHelper.createCoordinator(threeCrotchets(), List.of());
            ReflectionTestHelper.selectNote(coordinator, 2);

            pressArrowKey(selectionCallback(coordinator), KeyEvent.VK_RIGHT);

            var range = selectedRangeOrFail(coordinator);
            assertThat(coordinator.getActiveLineIndex()).isEqualTo(0);
            assertThat(range.begin()).isEqualTo(2);
            assertThat(range.end()).isEqualTo(2);
        }

        @Test
        void testLeftAtFirstElementCrossesToPreviousLineLastElement() {
            var coordinator = twoLineCoordinator(2, 2);
            coordinator.activateLine(1);
            ReflectionTestHelper.selectNote(coordinator, 0);

            pressArrowKey(selectionCallback(coordinator), KeyEvent.VK_LEFT);

            var range = selectedRangeOrFail(coordinator);
            assertThat(coordinator.getActiveLineIndex()).isEqualTo(0);
            assertThat(range.begin()).isEqualTo(1);
            assertThat(range.end()).isEqualTo(1);
        }

        @Test
        void testRightAtLastElementCrossesToNextLineFirstElement() {
            var coordinator = twoLineCoordinator(2, 2);
            coordinator.activateLine(0);
            ReflectionTestHelper.selectNote(coordinator, 1);

            pressArrowKey(selectionCallback(coordinator), KeyEvent.VK_RIGHT);

            var range = selectedRangeOrFail(coordinator);
            assertThat(coordinator.getActiveLineIndex()).isEqualTo(1);
            assertThat(range.begin()).isEqualTo(0);
            assertThat(range.end()).isEqualTo(0);
        }

        @Test
        void testRightAtLastElementWithEmptyNextLineIsNoOp() {
            var coordinator = twoLineCoordinator(2, 0);
            coordinator.activateLine(0);
            ReflectionTestHelper.selectNote(coordinator, 1);

            pressArrowKey(selectionCallback(coordinator), KeyEvent.VK_RIGHT);

            // The next line has no elements, so the selection stays put on line 0.
            var range = selectedRangeOrFail(coordinator);
            assertThat(coordinator.getActiveLineIndex()).isEqualTo(0);
            assertThat(range.begin()).isEqualTo(1);
            assertThat(range.end()).isEqualTo(1);
        }

        @Test
        void testLeftAtFirstElementWithEmptyPreviousLineIsNoOp() {
            var coordinator = twoLineCoordinator(0, 2);
            coordinator.activateLine(1);
            ReflectionTestHelper.selectNote(coordinator, 0);

            pressArrowKey(selectionCallback(coordinator), KeyEvent.VK_LEFT);

            // The previous line has no elements, so the selection stays put on line 1.
            var range = selectedRangeOrFail(coordinator);
            assertThat(coordinator.getActiveLineIndex()).isEqualTo(1);
            assertThat(range.begin()).isEqualTo(0);
            assertThat(range.end()).isEqualTo(0);
        }

        /**
         * Right-arrow on the terminal used to call {@code selectSingle} with an index one past
         * the end of the line; it must now leave the selection alone instead (issue #713).
         */
        @Test
        void testRightAtTerminalIsNoOp() {
            var line = lineEndingInTerminal();
            var terminalIndex = line.elementCount() - 1;
            var coordinator = new SelectionCoordinator(mock(ScoreView.class));
            coordinator.registerLine(0, line);
            coordinator.activateLine(0);
            coordinator.selectSingleElement(0, terminalIndex);

            pressArrowKey(selectionCallback(coordinator), KeyEvent.VK_RIGHT);

            var range = selectedRangeOrFail(coordinator);
            assertThat(coordinator.getActiveLineIndex()).isEqualTo(0);
            assertThat(range.begin()).isEqualTo(terminalIndex);
            assertThat(range.end()).isEqualTo(terminalIndex);
        }

        @Test
        void testLeftAtTerminalMovesToThePreviousElement() {
            var line = lineEndingInTerminal();
            var terminalIndex = line.elementCount() - 1;
            var coordinator = new SelectionCoordinator(mock(ScoreView.class));
            coordinator.registerLine(0, line);
            coordinator.activateLine(0);
            coordinator.selectSingleElement(0, terminalIndex);

            pressArrowKey(selectionCallback(coordinator), KeyEvent.VK_LEFT);

            var range = selectedRangeOrFail(coordinator);
            assertThat(coordinator.getActiveLineIndex()).isEqualTo(0);
            assertThat(range.begin()).isEqualTo(terminalIndex - 1);
            assertThat(range.end()).isEqualTo(terminalIndex - 1);
        }

        private List<StaffElement> threeCrotchets() {
            return List.of(
                crotchet(),
                crotchet(),
                crotchet()
            );
        }
    }

    /**
     * A song-backed line holding two crotchets followed by the song's auto-maintained
     * terminal, so the last element is a real, indexable element rather than a detached
     * fixture (issue #713). Shared by the plain-arrow and Shift+arrow groups, which exercise
     * two different branches against the same shape of line.
     */
    private static Line lineEndingInTerminal() {
        var song = new Song();
        var line = song.getLine(0);

        song.withoutMutationTracking(() -> {
            line.addElement(crotchet());
            line.addElement(crotchet());
        });

        return line;
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
            final var originalPositionSp = 4;
            var song = new Song();
            var line = song.getLine(0);
            var note = crotchet();
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
            final var originalPositionSp = 4;
            var song = new Song();
            var line = song.getLine(0);
            var note = crotchet();
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
            final var sharedPositionSp = 4;
            var song = new Song();
            var line = song.getLine(0);
            var movedNote = crotchet();
            movedNote.setStaffPosition(sharedPositionSp);
            movedNote.setAccidental(StaffElement.Accidental.SHARP);
            var inheritingNote = crotchet();
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
            final var firstPositionSp = 0;
            final var secondPositionSp = 2;
            var song = new Song();
            var line = song.getLine(0);
            var firstNote = crotchet();
            firstNote.setStaffPosition(firstPositionSp);
            var secondNote = crotchet();
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
            var note = crotchet();
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
            var note = crotchet();
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
            var rest = crotchetRest();
            song.withoutMutationTracking(() -> line.addElement(rest));

            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectNote(coordinator, 0);

            pressArrowKey(selectionCallback(coordinator), KeyEvent.VK_UP);

            // A rest is not pitched, so the shift group is empty and nothing is mutated.
            messageCenterMock.verify(() -> MessageCenter.post(any(Message.class)), never());
        }

        @Test
        void testDownCollapsingUnselectedPrecedingGraceNoteSlidesSelectionRangeDown() {
            final var gracePositionSp = 4;
            var song = new Song();
            var line = song.getLine(0);
            var grace = graceQuaver();
            grace.setStaffPosition(gracePositionSp);
            var host = crotchet();
            host.setStaffPosition(gracePositionSp - 1);
            var extra = crotchet();
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

            // The shift no longer repairs the selection itself — it records the removal and the
            // coordinator splices the range when the notification arrives. MessageCenter is
            // mocked here, so replay the recorded batch into the coordinator by hand.
            coordinator.revalidateElementSelection(
                new SongDidChangeNotification(capturedMutations(), song));

            // The grace note collapsed into the host, shifting every later index down by
            // one; the surviving selection must track the same two notes (now at 0 and 1),
            // not the stale pre-removal indices (1 and 2).
            assertThat(line.effectiveElementCount()).isEqualTo(2);
            var range = selectedRangeOrFail(coordinator);
            assertThat(range.begin()).isEqualTo(0);
            assertThat(range.end()).isEqualTo(1);
        }

        /**
         * Captures all posted messages and returns the {@link ElementModification}
         * carried by whichever {@link SongDidChangeNotification} recorded the shift.
         */
        private ElementModification capturedPitchModification() {
            return capturedMutations().stream()
                .filter(m -> m instanceof ElementModification)
                .map(m -> (ElementModification) m)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No ElementModification in captured notifications"));
        }

        /**
         * Every mutation carried by the {@link SongDidChangeNotification}s posted so far, in
         * post order. {@code MessageCenter} is mocked for this group, so nothing the production
         * bus would deliver actually arrives; a test that needs a subscriber's reaction has to
         * replay the batch into that subscriber itself.
         */
        private List<Mutation> capturedMutations() {
            var captor = ArgumentCaptor.forClass(Message.class);
            messageCenterMock.verify(() -> MessageCenter.post(captor.capture()), atLeastOnce());

            return captor.getAllValues().stream()
                .filter(m -> m instanceof SongDidChangeNotification)
                .map(m -> (SongDidChangeNotification) m)
                .flatMap(n -> n.getMutations().stream())
                .toList();
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

        /**
         * Shift+Right from the last selectable element must not sweep the terminal in — a
         * click is the only gesture that may select it (issue #713). The plain-arrow tests
         * above cover a different branch, so this one is not redundant with them.
         */
        @Test
        void testShiftRightAtLastSelectableElementDoesNotExtendOntoTheTerminal() {
            var line = lineEndingInTerminal();
            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line, List.of());
            ReflectionTestHelper.selectNote(coordinator, line.effectiveElementCount() - 1);
            var callback = selectionCallback(coordinator);

            pressShiftArrowKey(callback, KeyEvent.VK_RIGHT);

            verify(callback, never()).extendSelectionTo(anyInt());
        }

        /**
         * Shift+Left starting from the terminal extends backwards normally — the terminal is
         * the anchor, and the coordinator's clamp pulls that anchor back into the resulting
         * range rather than the keystroke being refused.
         */
        @Test
        void testShiftLeftFromTheTerminalExtendsBackward() {
            var line = lineEndingInTerminal();
            var terminalIndex = line.elementCount() - 1;
            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line, List.of());
            ReflectionTestHelper.selectNote(coordinator, terminalIndex);
            var callback = selectionCallback(coordinator);

            pressShiftArrowKey(callback, KeyEvent.VK_LEFT);

            verify(callback).extendSelectionTo(terminalIndex - 1);
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
                crotchet(),
                crotchet(),
                crotchet()
            );
        }
    }

    // -------------------------------------------------------------------
    // Row 70: installKeyBindings registers one binding per key code
    // -------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class InstallKeyBindings {

        /**
         * Every keystroke the method claims has to reach a live action. Which keystrokes those
         * are is asserted per key by the binding tests below; a bare total would only
         * guarantee that adding a key edits this line.
         */
        @Test
        void testEveryReturnedBindingIsWiredIntoTheComponent() {
            var callback = mock(InputHandlerCallback.class);
            var handler = new ScoreInputHandler(callback);
            var component = new JPanel();

            var bindings = handler.installKeyBindings(component);

            assertThat(bindings).isNotEmpty();

            var inputMap = component.getInputMap(JComponent.WHEN_FOCUSED);
            var actionMap = component.getActionMap();

            for (var entry : bindings.entrySet()) {
                var keystroke = entry.getKey();
                var actionKey = entry.getValue();

                assertThat(inputMap.get(keystroke)).isEqualTo(actionKey);
                assertThat(actionMap.get(actionKey)).isNotNull();
            }
        }
    }

    // -------------------------------------------------------------------
    // The last-insertion key bindings: plain B, shift-G, plain F and plain T
    // -------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class LastInsertionKeyBindings {

        /**
         * One key: the keystroke its binding must claim, and the command a press posts.
         *
         * <p>The keystroke is written out as a literal even though the binding takes it from
         * the menu action the key falls through to. That literal is what pins the two
         * together — the binding reports itself disabled precisely so the key reaches the
         * action's accelerator, and the fall-through only lands on the right command while
         * the two agree.
         */
        private record LastInsertionKey(
            String name,
            KeyStroke keyStroke,
            Class<? extends Message> command
        ) {
            @Override
            public String toString() {
                return name;
            }
        }

        /** What leaves a key unusable, and so leaves the keystroke for the menu accelerator. */
        private enum Blocker {
            SELECT_MODE, GRACE_IN_PROGRESS, PLACEMENT_IN_PROGRESS
        }

        /** The modifiers a key could be bound with; each key must claim exactly one of them. */
        private static final List<Integer> MODIFIERS = List.of(
            0,
            UIUtils.MENU_SHORTCUT_MASK,
            InputEvent.SHIFT_DOWN_MASK,
            InputEvent.ALT_DOWN_MASK
        );

        private static List<LastInsertionKey> keys() {
            return List.of(
                new LastInsertionKey(
                    "b",
                    KeyStroke.getKeyStroke(KeyEvent.VK_B, 0),
                    ToggleBeamWithPreviousCommand.class),
                new LastInsertionKey(
                    "shift-g",
                    KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.SHIFT_DOWN_MASK),
                    ToggleGlissandoWithPreviousCommand.class),
                new LastInsertionKey(
                    "f",
                    KeyStroke.getKeyStroke(KeyEvent.VK_F, 0),
                    ToggleFallOnLastInsertionCommand.class),
                new LastInsertionKey(
                    "t",
                    KeyStroke.getKeyStroke(KeyEvent.VK_T, 0),
                    ToggleTieWithPreviousCommand.class)
            );
        }

        private static List<Arguments> keysAndBlockers() {
            return keys().stream()
                .flatMap(key -> Arrays.stream(Blocker.values()).map(blocker -> Arguments.of(key, blocker)))
                .toList();
        }

        @ParameterizedTest
        @MethodSource("keys")
        void testInstallKeyBindingsRegistersTheKeyButNoModifiedVariantOfIt(LastInsertionKey key) {
            var handler = new ScoreInputHandler(mock(InputHandlerCallback.class));

            var bindings = handler.installKeyBindings(new JPanel());
            var keyStroke = key.keyStroke();

            assertThat(bindings).containsKey(keyStroke);

            for (var modifiers : MODIFIERS) {
                // Compared as keystrokes, not as masks: AWT folds the legacy modifier bits into
                // the stored value, so shift-G's modifiers are not SHIFT_DOWN_MASK on its own.
                var variant = KeyStroke.getKeyStroke(keyStroke.getKeyCode(), modifiers);

                if (!variant.equals(keyStroke)) {
                    assertThat(bindings).doesNotContainKey(variant);
                }
            }
        }

        @ParameterizedTest
        @MethodSource("keys")
        void testKeyInEditModeConsumesItAndPostsItsCommand(LastInsertionKey key) {
            var callback = mock(InputHandlerCallback.class);
            when(callback.getMode()).thenReturn(Mode.EDIT);

            try (
                var emm = mockStatic(EditModeManager.class);
                var mc = mockStatic(MessageCenter.class)
            ) {
                emm.when(EditModeManager::getGraceModeManager).thenReturn(mock(GraceModeManager.class));
                emm.when(EditModeManager::getInsertionPointMode).thenReturn(mock(InsertionPointMode.class));

                var consumed = pressLastInsertionKey(callback, key.keyStroke());

                assertThat(consumed).as("binding handled %s", key).isTrue();
                mc.verify(() -> MessageCenter.post(any(key.command())));
            }
        }

        @ParameterizedTest
        @MethodSource("keysAndBlockers")
        void testBlockedKeyIsLeftUnconsumedForTheMenuAccelerator(LastInsertionKey key, Blocker blocker) {
            try (
                var emm = mockStatic(EditModeManager.class);
                var mc = mockStatic(MessageCenter.class)
            ) {
                var consumed = pressLastInsertionKey(callbackBlockedBy(blocker, emm), key.keyStroke());

                assertThat(consumed)
                    .as("binding must not swallow %s when %s", key, blocker)
                    .isFalse();
                mc.verify(() -> MessageCenter.post(any(key.command())), never());
            }
        }

        /** A callback in the one state {@code blocker} names, with the other two clear. */
        private InputHandlerCallback callbackBlockedBy(
            Blocker blocker,
            MockedStatic<EditModeManager> editModeManager
        ) {
            var callback = mock(InputHandlerCallback.class);
            when(callback.getMode()).thenReturn(blocker == Blocker.SELECT_MODE ? Mode.SELECT : Mode.EDIT);

            // The mode is read before either manager is consulted, so the select-mode case
            // reaches neither singleton.
            if (blocker != Blocker.SELECT_MODE) {
                var graceModeManager = mock(GraceModeManager.class);
                when(graceModeManager.isInProgress()).thenReturn(blocker == Blocker.GRACE_IN_PROGRESS);
                var insertionPointMode = mock(InsertionPointMode.class);
                when(insertionPointMode.isInProgress()).thenReturn(blocker == Blocker.PLACEMENT_IN_PROGRESS);

                editModeManager.when(EditModeManager::getGraceModeManager).thenReturn(graceModeManager);
                editModeManager.when(EditModeManager::getInsertionPointMode).thenReturn(insertionPointMode);
            }

            return callback;
        }
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    /**
     * Installs key bindings on a fresh component and dispatches {@code keyStroke} the way
     * Swing does: an enabled binding runs and the key is consumed; a disabled one is
     * skipped without running, leaving the key to fall through to the root pane, where the
     * corresponding action's own accelerator lives.
     * <p>
     * Calling {@code actionPerformed} unconditionally instead would hide the difference
     * that matters — an action that runs and decides to do nothing still swallows the
     * key, which is what would break the select-mode shortcut.
     *
     * @param callback The mode/state source the binding consults
     * @param keyStroke The last-insertion binding's keystroke
     * @return Whether the binding consumed the key
     */
    private boolean pressLastInsertionKey(InputHandlerCallback callback, KeyStroke keyStroke) {
        var handler = new ScoreInputHandler(callback);
        var component = new JPanel();
        handler.installKeyBindings(component);

        var action = component.getActionMap().get(
            component.getInputMap(JComponent.WHEN_FOCUSED).get(keyStroke));

        if (!action.isEnabled()) {
            return false;
        }

        action.actionPerformed(new ActionEvent(component, ActionEvent.ACTION_PERFORMED, ""));

        return true;
    }

    /** A left-button event of type {@code id} flagged as a popup trigger. */
    private MouseEvent popupTriggerEvent(int id) {
        return new MouseEvent(
            mock(Component.class), id, 0L, 0, 0, 0, 0, 0, 1, true, MouseEvent.BUTTON1
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
            component.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(keyCode, 0)));
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
            component.getInputMap(JComponent.WHEN_FOCUSED).get(
                KeyStroke.getKeyStroke(keyCode, InputEvent.SHIFT_DOWN_MASK)));
        action.actionPerformed(new ActionEvent(component, ActionEvent.ACTION_PERFORMED, ""));
    }

    /** A callback whose only wired behavior is exposing {@code coordinator}. */
    private InputHandlerCallback selectionCallback(SelectionCoordinator coordinator) {
        var callback = mock(InputHandlerCallback.class);
        when(callback.getSelectionCoordinator()).thenReturn(coordinator);
        return callback;
    }

    /** Returns {@code coordinator}'s selected range, failing the test if there is none. */
    private Selection.Range selectedRangeOrFail(SelectionCoordinator coordinator) {
        var range = coordinator.getRange();

        assertThat(range).as("Expected a selected range").isNotNull();

        return range;
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
                firstLine.addElement(crotchet());
            }
        });

        var secondLine = new Line(song);

        song.withoutMutationTracking(() -> {
            for (var i = 0; i < secondLineNoteCount; i++) {
                secondLine.addElement(crotchet());
            }
        });

        song.addLine(secondLine);

        var coordinator = new SelectionCoordinator(mock(ScoreView.class));
        coordinator.registerLine(0, firstLine);
        coordinator.registerLine(1, secondLine);
        return coordinator;
    }
}
