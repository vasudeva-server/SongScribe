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

package songscribe.ui.edit;

import module java.desktop;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JRootPane;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.ScaleContext;
import songscribe.layout.ElementColumn;
import songscribe.layout.HorizontalSpacingCalculator;
import songscribe.layout.InsertionSpacingCalculator;
import songscribe.layout.LayoutResult;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.notification.DurationWasSelectedNotification;
import songscribe.message.notification.GraceModeStateDidChangeNotification;
import songscribe.ui.action.Actions;
import songscribe.ui.component.MainFrame;
import songscribe.ui.ViewScale;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.component.score.PreviewElementManager;
import songscribe.ui.selection.SelectionCoordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GraceModeManagerTest extends UnitTest {

    /** Mocked EditModeManager — injected to satisfy the non-null constructor contract. */
    private EditModeManager editModeManager;
    /** Mocked SelectionCoordinator injected into each GraceModeManager under test. */
    private SelectionCoordinator selectionCoordinator;

    @BeforeEach
    void setUp() {
        editModeManager = mock(EditModeManager.class);
        selectionCoordinator = mock(SelectionCoordinator.class);
        ScaleContext.setPixelsPerStaffSpace(ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE);
    }

    @AfterEach
    void tearDown() {
        // Reset the static singleton so tests don't bleed into each other.
        resetStaticInstance(null);
    }

    // -------------------------------------------------------------------------
    // isActive / isInProgress — rows 1-2
    // -------------------------------------------------------------------------

    @Nested
    class IsActiveAndIsInProgress {

        @Test
        void testIsActiveReturnsFalseWhenNoInstance() {
            resetStaticInstance(null);
            assertThat(GraceModeManager.isActive()).isFalse();
        }

        @Test
        void testIsActiveReturnsFalseWhenInstanceIsInactive() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            // Initial state is INACTIVE
            assertThat(GraceModeManager.isActive()).isFalse();
            assertThat(manager.isInProgress()).isFalse();
        }

        @Test
        void testIsActiveReturnsTrueWhenStateIsNonInactive() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            manager.setState(GraceModeManager.State.GRACE_NOTE);
            assertThat(GraceModeManager.isActive()).isTrue();
            assertThat(manager.isInProgress()).isTrue();
        }

        @Test
        void testIsInProgressIsFalseOnlyForInactiveState() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);

            for (var state : GraceModeManager.State.values()) {
                manager.setState(state);
                var expectedInProgress = state != GraceModeManager.State.INACTIVE;
                assertThat(manager.isInProgress())
                    .as("isInProgress for state %s", state)
                    .isEqualTo(expectedInProgress);
            }
        }
    }

    // -------------------------------------------------------------------------
    // isPendingCancel — row 3
    // -------------------------------------------------------------------------

    @Nested
    class IsPendingCancel {

        @Test
        void testIsPendingCancelReturnsFalseWhenNoInstance() {
            resetStaticInstance(null);
            var element = ElementType.GRACE_QUAVER.newInstance();
            assertThat(GraceModeManager.isPendingCancel(element)).isFalse();
        }

        @Test
        void testIsPendingCancelReturnsFalseWhenPendingCancelIsFalse() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var element = ElementType.GRACE_QUAVER.newInstance();
            // pendingCancel defaults to false
            assertThat(GraceModeManager.isPendingCancel(element)).isFalse();
        }

        @Test
        void testIsPendingCancelReturnsFalseForDifferentElement() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            var otherElement = ElementType.CROTCHET.newInstance();

            manager.setPendingCancel(true);
            manager.setGraceNote(graceNote);

            // Different instance — identity check must fail
            assertThat(GraceModeManager.isPendingCancel(otherElement)).isFalse();
        }

        @Test
        void testIsPendingCancelReturnsTrueForExactGraceNoteInstance() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var graceNote = ElementType.GRACE_QUAVER.newInstance();

            manager.setPendingCancel(true);
            manager.setGraceNote(graceNote);

            assertThat(GraceModeManager.isPendingCancel(graceNote)).isTrue();
        }
    }

    // -------------------------------------------------------------------------
    // getCancelThresholdPx / getConnectThresholdPx — row 4
    // -------------------------------------------------------------------------

    @Nested
    class ThresholdPx {

        @Test
        void testCancelThresholdReturnsMinus1WhenNoInstance() {
            resetStaticInstance(null);
            assertThat(GraceModeManager.getCancelThresholdPx()).isEqualTo(-1);
        }

        @Test
        void testConnectThresholdReturnsMinus1WhenNoInstance() {
            resetStaticInstance(null);
            assertThat(GraceModeManager.getConnectThresholdPx()).isEqualTo(-1);
        }

        @Test
        void testCancelThresholdReturnsMinus1WhenGraceNoteIsNull() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var lineComponent = mock(LineComponent.class);
            manager.setGraceNote(null);
            manager.setGraceLineComponent(lineComponent);
            assertThat(GraceModeManager.getCancelThresholdPx()).isEqualTo(-1);
        }

        @Test
        void testCancelThresholdReturnsMinus1WhenLineComponentIsNull() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            manager.setGraceNote(ElementType.GRACE_QUAVER.newInstance());
            manager.setGraceLineComponent(null);
            assertThat(GraceModeManager.getCancelThresholdPx()).isEqualTo(-1);
        }

        @Test
        void testCancelThresholdReturnsMinus1WhenLayoutIsNull() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var lineComponent = mock(LineComponent.class);
            when(lineComponent.getLayoutResult()).thenReturn(null);
            manager.setGraceNote(ElementType.GRACE_QUAVER.newInstance());
            manager.setGraceLineComponent(lineComponent);
            assertThat(GraceModeManager.getCancelThresholdPx()).isEqualTo(-1);
        }

        @Test
        void testCancelThresholdIsGraceXPxMinusSlopPx() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            var lineComponent = mock(LineComponent.class);
            var layout = mock(LayoutResult.class);

            // Place grace note at xSs = 10.0 → with 8 px/ss → 80 px
            when(layout.getElementXSs(graceNote)).thenReturn(10.0);
            when(lineComponent.getLayoutResult()).thenReturn(layout);
            var scoreViewStub = graceScoreViewStub();
            when(lineComponent.getScoreView()).thenReturn(scoreViewStub);
            manager.setGraceNote(graceNote);
            manager.setGraceLineComponent(lineComponent);

            var expectedPx = (int) Math.round(10.0 * ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE) - GraceModeManager.GRACE_SLOP_PX;
            assertThat(GraceModeManager.getCancelThresholdPx()).isEqualTo(expectedPx);
        }

        @Test
        void testConnectThresholdReturnsMinus1WhenColumnIsNull() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            var lineComponent = mock(LineComponent.class);
            var layout = mock(LayoutResult.class);

            when(layout.getElementColumn(graceNote)).thenReturn(null);
            when(lineComponent.getLayoutResult()).thenReturn(layout);
            var scoreViewStub = graceScoreViewStub();
            when(lineComponent.getScoreView()).thenReturn(scoreViewStub);
            manager.setGraceNote(graceNote);
            manager.setGraceLineComponent(lineComponent);

            assertThat(GraceModeManager.getConnectThresholdPx()).isEqualTo(-1);
        }

        @Test
        void testConnectThresholdIsRightEdgePxPlusSlopPx() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            var lineComponent = mock(LineComponent.class);
            var layout = mock(LayoutResult.class);
            var column = mock(ElementColumn.class);

            // Right edge = xSs + rightExtentSs = 5.0 + 2.0 = 7.0 → 56 px at 8 px/ss
            when(column.getXSs()).thenReturn(5.0);
            when(column.getRightExtentSs()).thenReturn(2.0);
            when(layout.getElementColumn(graceNote)).thenReturn(column);
            when(lineComponent.getLayoutResult()).thenReturn(layout);
            var scoreViewStub = graceScoreViewStub();
            when(lineComponent.getScoreView()).thenReturn(scoreViewStub);
            manager.setGraceNote(graceNote);
            manager.setGraceLineComponent(lineComponent);

            var rightEdgeSs = 5.0 + 2.0;
            var expectedPx = (int) Math.round(rightEdgeSs * ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE) + GraceModeManager.GRACE_SLOP_PX;
            assertThat(GraceModeManager.getConnectThresholdPx()).isEqualTo(expectedPx);
        }
    }

    // -------------------------------------------------------------------------
    // getLockedInsertionXSs — row 5
    // -------------------------------------------------------------------------

    @Nested
    class GetLockedInsertionXSs {

        @Test
        void testReturnsZeroWhenGraceNoteIsNull() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            manager.setGraceNote(null);
            manager.setGraceLine(detachedLine());
            manager.setGraceLineComponent(mock(LineComponent.class));
            assertThat(manager.getLockedInsertionXSs()).isEqualTo(0.0);
        }

        @Test
        void testReturnsZeroWhenGraceLineIsNull() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            manager.setGraceNote(ElementType.GRACE_QUAVER.newInstance());
            manager.setGraceLine(null);
            manager.setGraceLineComponent(mock(LineComponent.class));
            assertThat(manager.getLockedInsertionXSs()).isEqualTo(0.0);
        }

        @Test
        void testReturnsZeroWhenGraceLineComponentIsNull() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            manager.setGraceNote(ElementType.GRACE_QUAVER.newInstance());
            manager.setGraceLine(detachedLine());
            manager.setGraceLineComponent(null);
            assertThat(manager.getLockedInsertionXSs()).isEqualTo(0.0);
        }

        @Test
        void testReturnsZeroWhenLayoutIsNull() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var lineComponent = mock(LineComponent.class);
            when(lineComponent.getLayoutResult()).thenReturn(null);
            manager.setGraceNote(ElementType.GRACE_QUAVER.newInstance());
            manager.setGraceLine(detachedLine());
            manager.setGraceLineComponent(lineComponent);
            assertThat(manager.getLockedInsertionXSs()).isEqualTo(0.0);
        }

        @Test
        void testReturnsZeroWhenColumnIsNull() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            var lineComponent = mock(LineComponent.class);
            var layout = mock(LayoutResult.class);
            when(layout.getElementColumn(graceNote)).thenReturn(null);
            when(lineComponent.getLayoutResult()).thenReturn(layout);
            var scoreViewStub = graceScoreViewStub();
            when(lineComponent.getScoreView()).thenReturn(scoreViewStub);
            manager.setGraceNote(graceNote);
            manager.setGraceLine(detachedLine());
            manager.setGraceLineComponent(lineComponent);
            assertThat(manager.getLockedInsertionXSs()).isEqualTo(0.0);
        }

        @Test
        void testComputedValueIsColumnXPlusRightExtentPlusGapPlusHostLeftExtent() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            // Preview element with no accidental → hostLeftExtentSs = 0
            var hostPreview = ElementType.CROTCHET.newInstance();
            var lineComponent = mock(LineComponent.class);
            var layout = mock(LayoutResult.class);
            var column = mock(ElementColumn.class);

            when(column.getXSs()).thenReturn(3.0);
            when(column.getRightExtentSs()).thenReturn(1.5);
            when(layout.getElementColumn(graceNote)).thenReturn(column);
            when(lineComponent.getLayoutResult()).thenReturn(layout);
            var scoreViewStub = graceScoreViewStub();
            when(lineComponent.getScoreView()).thenReturn(scoreViewStub);

            manager.setGraceNote(graceNote);
            manager.setGraceLine(detachedLine());
            manager.setGraceLineComponent(lineComponent);

            // EditModeManager.getPreviewElement() is static — must mock statically.
            // No accidental on hostPreview → hostLeftExtentSs = 0.
            // Expected = xSs + rightExtentSs + GRACE_HOST_REST_SS + |hostLeftExtentSs|
            try (var emMock = mockStatic(EditModeManager.class)) {
                emMock.when(EditModeManager::getPreviewElement).thenReturn(hostPreview);
                var expected = 3.0 + 1.5 + HorizontalSpacingCalculator.GRACE_HOST_REST_SS + 0.0;
                assertThat(manager.getLockedInsertionXSs()).isEqualTo(expected);
            }
        }

        @Test
        void testUsesZeroHostLeftExtentWhenPreviewElementIsNull() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            var lineComponent = mock(LineComponent.class);
            var layout = mock(LayoutResult.class);
            var column = mock(ElementColumn.class);

            when(column.getXSs()).thenReturn(4.0);
            when(column.getRightExtentSs()).thenReturn(2.0);
            when(layout.getElementColumn(graceNote)).thenReturn(column);
            when(lineComponent.getLayoutResult()).thenReturn(layout);
            var scoreViewStub = graceScoreViewStub();
            when(lineComponent.getScoreView()).thenReturn(scoreViewStub);

            manager.setGraceNote(graceNote);
            manager.setGraceLine(detachedLine());
            manager.setGraceLineComponent(lineComponent);

            // EditModeManager.getPreviewElement() is static — must mock statically.
            try (var emMock = mockStatic(EditModeManager.class)) {
                emMock.when(EditModeManager::getPreviewElement).thenReturn(null);
                var expected = 4.0 + 2.0 + HorizontalSpacingCalculator.GRACE_HOST_REST_SS;
                assertThat(manager.getLockedInsertionXSs()).isEqualTo(expected);
            }
        }
    }

    // -------------------------------------------------------------------------
    // mousePressed — row 6
    // -------------------------------------------------------------------------

    @Nested
    class MousePressed {

        private MockedStatic<MessageCenter> messageCenterMock;
        // EditModeManager.getPreviewElement() is static; must mock statically for all paths.
        private MockedStatic<EditModeManager> editModeManagerMock;

        @BeforeEach
        void setUp() {
            messageCenterMock = mockStatic(MessageCenter.class);
            editModeManagerMock = mockStatic(EditModeManager.class);
            // enterGraceNote triggers Actions.DURATION_ACTION_GROUP.select() →
            // applyToSelectionIfActive() → mainFrame.requireScoreView(). Actions are
            // initialized with mockFrame so they call it directly — no static mock needed.
            var mockFrame = mock(MainFrame.class);
            var mockScore = mock(ScoreView.class);
            var mockRootPane = mock(JRootPane.class);
            when(mockRootPane.getInputMap(anyInt())).thenReturn(new InputMap());
            when(mockRootPane.getActionMap()).thenReturn(new ActionMap());
            when(mockFrame.getRootPane()).thenReturn(mockRootPane);
            when(mockFrame.requireScoreView()).thenReturn(mockScore);
            when(mockFrame.getScoreView()).thenReturn(mockScore);
            when(mockScore.getSelectionCoordinator()).thenReturn(mock(SelectionCoordinator.class));
            Actions.initialize(mockFrame);
        }

        @AfterEach
        void tearDown() {
            Actions.resetForTest();
            editModeManagerMock.close();
            messageCenterMock.close();
        }

        @Test
        void testReturnsTrueAndConsumesWhenStateIsGraceNoteInsert() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            manager.setState(GraceModeManager.State.GRACE_NOTE_INSERT);
            var lineComponent = mock(LineComponent.class);
            var e = mouseEvent(lineComponent, MouseEvent.MOUSE_PRESSED, 0, 0, MouseEvent.BUTTON1);

            assertThat(manager.mousePressed(lineComponent, e)).isTrue();
        }

        @Test
        void testReturnsFalseWhenStateIsGraceNote() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            manager.setState(GraceModeManager.State.GRACE_NOTE);
            var lineComponent = mock(LineComponent.class);
            var e = mouseEvent(lineComponent, MouseEvent.MOUSE_PRESSED, 0, 0, MouseEvent.BUTTON1);

            assertThat(manager.mousePressed(lineComponent, e)).isFalse();
        }

        @Test
        void testReturnsFalseForNonButton1() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var lineComponent = mock(LineComponent.class);
            var e = mouseEvent(lineComponent, MouseEvent.MOUSE_PRESSED, 0, 0, MouseEvent.BUTTON2);

            assertThat(manager.mousePressed(lineComponent, e)).isFalse();
        }

        @Test
        void testReturnsFalseWhenPreviewElementIsNull() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            // getPreviewElement() is static — configure the class-level static mock.
            editModeManagerMock.when(EditModeManager::getPreviewElement).thenReturn(null);
            var lineComponent = mock(LineComponent.class);
            var e = mouseEvent(lineComponent, MouseEvent.MOUSE_PRESSED, 0, 0, MouseEvent.BUTTON1);

            assertThat(manager.mousePressed(lineComponent, e)).isFalse();
        }

        @Test
        void testReturnsFalseWhenPreviewElementIsNotGraceQuaver() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            editModeManagerMock.when(EditModeManager::getPreviewElement)
                .thenReturn(ElementType.CROTCHET.newInstance());
            var lineComponent = mock(LineComponent.class);
            var e = mouseEvent(lineComponent, MouseEvent.MOUSE_PRESSED, 0, 0, MouseEvent.BUTTON1);

            assertThat(manager.mousePressed(lineComponent, e)).isFalse();
        }

        @Test
        void testReturnsTrueAndDoesNotEnterGraceNoteWhenInsideGraceHostPair() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            editModeManagerMock.when(EditModeManager::getPreviewElement)
                .thenReturn(ElementType.GRACE_QUAVER.newInstance());

            try (var previewMock = mockStatic(PreviewElementManager.class)) {
                previewMock.when(PreviewElementManager::getCurrentXIndex).thenReturn(1);

                var line = mock(Line.class);
                when(line.isInsideGraceHostPair(1)).thenReturn(true);
                var lineComponent = mock(LineComponent.class);
                when(lineComponent.getLine()).thenReturn(line);
                var e = mouseEvent(lineComponent, MouseEvent.MOUSE_PRESSED, 0, 0, MouseEvent.BUTTON1);

                assertThat(manager.mousePressed(lineComponent, e)).isTrue();
                // State must remain INACTIVE (no grace note entered)
                assertThat(manager.isInProgress()).isFalse();
            }
        }

        @Test
        void testReturnsFalseWhenXIndexIsNegative() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            editModeManagerMock.when(EditModeManager::getPreviewElement)
                .thenReturn(ElementType.GRACE_QUAVER.newInstance());

            try (var previewMock = mockStatic(PreviewElementManager.class)) {
                previewMock.when(PreviewElementManager::getCurrentXIndex).thenReturn(-1);

                var line = mock(Line.class);
                when(line.isInsideGraceHostPair(-1)).thenReturn(false);
                var lineComponent = mock(LineComponent.class);
                when(lineComponent.getLine()).thenReturn(line);
                var e = mouseEvent(lineComponent, MouseEvent.MOUSE_PRESSED, 0, 0, MouseEvent.BUTTON1);

                assertThat(manager.mousePressed(lineComponent, e)).isFalse();
            }
        }

        @Test
        void testReturnsTrueAndShowsErrorWhenNoRoomForGraceNote() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            editModeManagerMock.when(EditModeManager::getPreviewElement)
                .thenReturn(ElementType.GRACE_QUAVER.newInstance());

            try (var previewMock = mockStatic(PreviewElementManager.class);
                 var calcMock = mockStatic(InsertionSpacingCalculator.class)) {

                previewMock.when(PreviewElementManager::getCurrentXIndex).thenReturn(0);
                calcMock.when(
                    () -> InsertionSpacingCalculator.hasRoomForGraceNote(any(), anyInt(), any(), any())
                ).thenReturn(false);

                var line = mock(Line.class);
                when(line.isInsideGraceHostPair(0)).thenReturn(false);
                var lineComponent = mock(LineComponent.class);
                when(lineComponent.getLine()).thenReturn(line);
                var e = mouseEvent(lineComponent, MouseEvent.MOUSE_PRESSED, 0, 0, MouseEvent.BUTTON1);

                assertThat(manager.mousePressed(lineComponent, e)).isTrue();
                // Dialogs suppressed; state must remain INACTIVE
                assertThat(manager.isInProgress()).isFalse();
            }
        }

        @Test
        void testEntersGraceNoteStateWhenRoomCheckPasses() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            editModeManagerMock.when(EditModeManager::getPreviewElement)
                .thenReturn(ElementType.GRACE_QUAVER.newInstance());

            try (var previewMock = mockStatic(PreviewElementManager.class);
                 var calcMock = mockStatic(InsertionSpacingCalculator.class)) {

                previewMock.when(PreviewElementManager::getCurrentXIndex).thenReturn(0);
                calcMock.when(
                    () -> InsertionSpacingCalculator.hasRoomForGraceNote(any(), anyInt(), any(), any())
                ).thenReturn(true);

                // Real line with one element; enterGraceNote reads it at index 0
                var line = lineWith(ElementType.GRACE_QUAVER);
                var lineComponent = mock(LineComponent.class);
                when(lineComponent.getLine()).thenReturn(line);
                when(lineComponent.getLayoutResult()).thenReturn(null);
                var e = mouseEvent(lineComponent, MouseEvent.MOUSE_PRESSED, 50, 60, MouseEvent.BUTTON1);

                var result = manager.mousePressed(lineComponent, e);

                assertThat(result).isTrue();
                assertThat(manager.isInProgress()).isTrue();
            }
        }
    }

    // -------------------------------------------------------------------------
    // mouseReleased — row 7
    // -------------------------------------------------------------------------

    @Nested
    class MouseReleased {

        private MockedStatic<MessageCenter> messageCenterMock;

        @BeforeEach
        void setUp() {
            messageCenterMock = mockStatic(MessageCenter.class);
        }

        @AfterEach
        void tearDown() {
            messageCenterMock.close();
        }

        @Test
        void testReturnsTrueAndConsumesWhenStateIsGraceNoteInsert() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            manager.setState(GraceModeManager.State.GRACE_NOTE_INSERT);
            var lineComponent = mock(LineComponent.class);
            var e = mouseEvent(lineComponent, MouseEvent.MOUSE_RELEASED, 0, 0, MouseEvent.BUTTON1);

            assertThat(manager.mouseReleased(lineComponent, e)).isTrue();
        }

        @Test
        void testReturnsFalseWhenStateIsInactive() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var lineComponent = mock(LineComponent.class);
            var e = mouseEvent(lineComponent, MouseEvent.MOUSE_RELEASED, 0, 0, MouseEvent.BUTTON1);

            assertThat(manager.mouseReleased(lineComponent, e)).isFalse();
        }

        @Test
        void testFinishesWithCancelWhenMouseDownPointIsNull() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var line = detachedLine();
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            line.addElement(graceNote);

            manager.setState(GraceModeManager.State.GRACE_NOTE);
            setField(manager, "mouseDownPoint", null);
            manager.setGraceNote(graceNote);
            manager.setGraceLine(line);
            setField(manager, "graceNoteIndex", 0);

            var lineComponent = mock(LineComponent.class);
            var e = mouseEvent(lineComponent, MouseEvent.MOUSE_RELEASED, 0, 0, MouseEvent.BUTTON1);

            assertThat(manager.mouseReleased(lineComponent, e)).isTrue();
            // finish(cancel=true) removes the grace note and resets to INACTIVE
            assertThat(manager.isInProgress()).isFalse();
        }

        @Test
        void testDragLeftWithPendingCancelFinishesWithCancel() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var line = detachedLine();
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            line.addElement(graceNote);

            // Simulate: state=GRACE_NOTE, pendingCancel=true, drag time >= MIN_DRAG_MILLIS
            var startPoint = new Point(100, 100);
            manager.setState(GraceModeManager.State.GRACE_NOTE);
            manager.setPendingCancel(true);
            setField(manager, "mouseDownPoint", startPoint);
            // Make the down time old enough to count as a drag
            setField(manager, "mouseDownTime", System.currentTimeMillis() - GraceModeManager.MIN_DRAG_MILLIS);
            manager.setGraceNote(graceNote);
            manager.setGraceLine(line);
            setField(manager, "graceNoteIndex", 0);

            var lineComponent = mock(LineComponent.class);
            // Move far left to clearly exceed GRACE_SLOP_PX
            var e = mouseEvent(lineComponent, MouseEvent.MOUSE_RELEASED, 50, 100, MouseEvent.BUTTON1);

            assertThat(manager.mouseReleased(lineComponent, e)).isTrue();
            // finish(cancel=true) resets to INACTIVE
            assertThat(manager.isInProgress()).isFalse();
        }

        @Test
        void testClickTransitionsToGraceNoteInsertState() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var line = detachedLine();
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            line.addElement(graceNote);
            var lineComponent = mock(LineComponent.class);
            when(lineComponent.getLayoutResult()).thenReturn(null);

            // Short time since mouse-down → classified as click
            var startPoint = new Point(100, 100);
            manager.setState(GraceModeManager.State.GRACE_NOTE);
            setField(manager, "mouseDownPoint", startPoint);
            setField(manager, "mouseDownTime", System.currentTimeMillis());
            manager.setGraceNote(graceNote);
            manager.setGraceLine(line);
            setField(manager, "graceNoteIndex", 0);
            // graceLineComponent is null → getLockedInsertionXSs returns 0 → enterGraceNoteInsert calls finish

            var e = mouseEvent(lineComponent, MouseEvent.MOUSE_RELEASED, 100, 100, MouseEvent.BUTTON1);

            assertThat(manager.mouseReleased(lineComponent, e)).isTrue();
            // enterGraceNoteInsert called; since graceLineComponent is null, getLockedInsertionXSs returns 0 → finish(true) → INACTIVE
            assertThat(manager.isInProgress()).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // mouseDragged — row 8
    // -------------------------------------------------------------------------

    @Nested
    class MouseDragged {

        private MockedStatic<MessageCenter> messageCenterMock;
        private MockedStatic<PreviewElementManager> previewMock;

        @BeforeEach
        void setUp() {
            messageCenterMock = mockStatic(MessageCenter.class);
            previewMock = mockStatic(PreviewElementManager.class);
        }

        @AfterEach
        void tearDown() {
            previewMock.close();
            messageCenterMock.close();
        }

        @Test
        void testReturnsTrueAndConsumesWhenStateIsGraceNoteInsert() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            manager.setState(GraceModeManager.State.GRACE_NOTE_INSERT);
            var lineComponent = mock(LineComponent.class);
            var e = mouseEvent(lineComponent, MouseEvent.MOUSE_DRAGGED, 0, 0, MouseEvent.BUTTON1);

            assertThat(manager.mouseDragged(lineComponent, e)).isTrue();
        }

        @Test
        void testReturnsFalseWhenStateIsInactive() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var lineComponent = mock(LineComponent.class);
            var e = mouseEvent(lineComponent, MouseEvent.MOUSE_DRAGGED, 0, 0, MouseEvent.BUTTON1);

            assertThat(manager.mouseDragged(lineComponent, e)).isFalse();
        }

        @Test
        void testPendingCancelSetWhenDragLeftOfGraceNote() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            var lineComponent = mock(LineComponent.class);
            var layout = mock(LayoutResult.class);
            var column = mock(ElementColumn.class);

            // Grace note at xSs=10.0 → cancelThreshold = round(10*8) - 4 = 76
            when(layout.getElementXSs(graceNote)).thenReturn(10.0);
            when(layout.getElementColumn(graceNote)).thenReturn(column);
            when(column.getXSs()).thenReturn(10.0);
            when(column.getRightExtentSs()).thenReturn(2.0);
            when(lineComponent.getLayoutResult()).thenReturn(layout);
            var scoreViewStub = graceScoreViewStub();
            when(lineComponent.getScoreView()).thenReturn(scoreViewStub);

            manager.setState(GraceModeManager.State.GRACE_NOTE);
            manager.setGraceNote(graceNote);
            manager.setGraceLine(detachedLine());
            manager.setGraceLineComponent(lineComponent);
            // Simulate an old mouse-down time to make isDrag = true
            setField(manager, "mouseDownTime", System.currentTimeMillis() - GraceModeManager.MIN_DRAG_MILLIS);

            // Mouse x <= cancelThreshold (76) → pendingCancel should become true
            var e = mouseEvent(lineComponent, MouseEvent.MOUSE_DRAGGED, 70, 0, MouseEvent.BUTTON1);
            var result = manager.mouseDragged(lineComponent, e);

            assertThat(result).isTrue();
            assertThat(manager.isPendingCancel()).isTrue();
        }

        @Test
        void testPendingConnectSetWithoutMutatingSlideWhenDragRightWithEligibleHost() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            var hostNote = ElementType.CROTCHET.newInstance();
            var line = detachedLine();
            line.addElement(graceNote);
            line.addElement(hostNote);

            var lineComponent = mock(LineComponent.class);
            var layout = mock(LayoutResult.class);
            var column = mock(ElementColumn.class);

            // Right edge at xSs=10+2=12 → connectThreshold = round(12*8) + 4 = 100
            when(layout.getElementXSs(graceNote)).thenReturn(10.0);
            when(layout.getElementColumn(graceNote)).thenReturn(column);
            when(column.getXSs()).thenReturn(10.0);
            when(column.getRightExtentSs()).thenReturn(2.0);
            when(lineComponent.getLayoutResult()).thenReturn(layout);
            var scoreViewStub = graceScoreViewStub();
            when(lineComponent.getScoreView()).thenReturn(scoreViewStub);

            manager.setState(GraceModeManager.State.GRACE_NOTE);
            manager.setGraceNote(graceNote);
            setField(manager, "graceNoteIndex", 0);
            manager.setGraceLine(line);
            manager.setGraceLineComponent(lineComponent);
            setField(manager, "mouseDownTime", System.currentTimeMillis() - GraceModeManager.MIN_DRAG_MILLIS);

            // Mouse x >= connectThreshold (100) → pendingConnect should become true.
            // The glissando feedback is render-only (drawn from the pendingConnect flag);
            // the drag must not mutate the element, or the untracked slide change would
            // corrupt undo's before-state clone in the tracked connect commit.
            var e = mouseEvent(lineComponent, MouseEvent.MOUSE_DRAGGED, 105, 0, MouseEvent.BUTTON1);
            manager.mouseDragged(lineComponent, e);

            assertThat(manager.isPendingConnect()).isTrue();
            assertThat(graceNote.hasGlissando()).isFalse();
        }

        @Test
        void testPendingConnectClearedWithoutMutatingSlideWhenDragBackLeft() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            var line = detachedLine();
            line.addElement(graceNote);

            var lineComponent = mock(LineComponent.class);
            var layout = mock(LayoutResult.class);
            var column = mock(ElementColumn.class);

            when(layout.getElementXSs(graceNote)).thenReturn(10.0);
            when(layout.getElementColumn(graceNote)).thenReturn(column);
            when(column.getXSs()).thenReturn(10.0);
            when(column.getRightExtentSs()).thenReturn(2.0);
            when(lineComponent.getLayoutResult()).thenReturn(layout);
            var scoreViewStub = graceScoreViewStub();
            when(lineComponent.getScoreView()).thenReturn(scoreViewStub);

            manager.setState(GraceModeManager.State.GRACE_NOTE);
            manager.setGraceNote(graceNote);
            setField(manager, "graceNoteIndex", 0);
            manager.setGraceLine(line);
            manager.setGraceLineComponent(lineComponent);
            setField(manager, "pendingConnect", true);
            setField(manager, "mouseDownTime", System.currentTimeMillis() - GraceModeManager.MIN_DRAG_MILLIS);

            // Mouse at x=50 → not right of grace note → pendingConnect = false
            var e = mouseEvent(lineComponent, MouseEvent.MOUSE_DRAGGED, 50, 0, MouseEvent.BUTTON1);
            manager.mouseDragged(lineComponent, e);

            assertThat(manager.isPendingConnect()).isFalse();
            assertThat(graceNote.hasGlissando()).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // mouseClicked — row 9 (wrong-level: unit coverage for state-machine paths)
    // -------------------------------------------------------------------------

    @Nested
    class MouseClicked {

        private MockedStatic<MessageCenter> messageCenterMock;
        private MockedStatic<PreviewElementManager> previewMock;

        @BeforeEach
        void setUp() {
            messageCenterMock = mockStatic(MessageCenter.class);
            previewMock = mockStatic(PreviewElementManager.class);
        }

        @AfterEach
        void tearDown() {
            previewMock.close();
            messageCenterMock.close();
        }

        @Test
        void testReturnsFalseWhenNotInProgress() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var lineComponent = mock(LineComponent.class);
            var e = mouseEvent(lineComponent, MouseEvent.MOUSE_CLICKED, 0, 0, MouseEvent.BUTTON1);

            assertThat(manager.mouseClicked(lineComponent, e)).isFalse();
        }

        @Test
        void testReturnsTrueWhenInProgressButNotGraceNoteInsertState() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            manager.setState(GraceModeManager.State.GRACE_NOTE);
            var lineComponent = mock(LineComponent.class);
            var e = mouseEvent(lineComponent, MouseEvent.MOUSE_CLICKED, 0, 0, MouseEvent.BUTTON1);

            // Active in non-INSERT state — consume to prevent normal insertion
            assertThat(manager.mouseClicked(lineComponent, e)).isTrue();
        }

        @Test
        void testJustEnteredInsertFlagSuppressesFirstClick() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            manager.setState(GraceModeManager.State.GRACE_NOTE_INSERT);
            setField(manager, "justEnteredInsert", true);
            var lineComponent = mock(LineComponent.class);
            var e = mouseEvent(lineComponent, MouseEvent.MOUSE_CLICKED, 0, 0, MouseEvent.BUTTON1);

            assertThat(manager.mouseClicked(lineComponent, e)).isTrue();
            // Flag must be cleared after suppression
            assertThat(manager.isJustEnteredInsert()).isFalse();
        }

        @Test
        void testCancelWhenClickOnDifferentLine() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            var line = detachedLine();
            line.addElement(graceNote);

            var graceLineComponent = mock(LineComponent.class);
            var otherLineComponent = mock(LineComponent.class);

            manager.setState(GraceModeManager.State.GRACE_NOTE_INSERT);
            manager.setGraceNote(graceNote);
            setField(manager, "graceNoteIndex", 0);
            manager.setGraceLine(line);
            manager.setGraceLineComponent(graceLineComponent);

            var e = mouseEvent(otherLineComponent, MouseEvent.MOUSE_CLICKED, 0, 0, MouseEvent.BUTTON1);
            assertThat(manager.mouseClicked(otherLineComponent, e)).isTrue();

            // finish(cancel=true) → state INACTIVE
            assertThat(manager.isInProgress()).isFalse();
        }

        @Test
        void testCancelWhenClickLeftOfGraceNote() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            var line = detachedLine();
            line.addElement(graceNote);

            var lineComponent = mock(LineComponent.class);
            var layout = mock(LayoutResult.class);
            // Grace note at xSs=10.0 → cancelThreshold = round(10*8) - 4 = 76
            when(layout.getElementXSs(graceNote)).thenReturn(10.0);
            when(lineComponent.getLayoutResult()).thenReturn(layout);
            var scoreViewStub = graceScoreViewStub();
            when(lineComponent.getScoreView()).thenReturn(scoreViewStub);

            manager.setState(GraceModeManager.State.GRACE_NOTE_INSERT);
            manager.setGraceNote(graceNote);
            setField(manager, "graceNoteIndex", 0);
            manager.setGraceLine(line);
            manager.setGraceLineComponent(lineComponent);

            // Click at x=70 <= cancelThreshold → cancel
            var e = mouseEvent(lineComponent, MouseEvent.MOUSE_CLICKED, 70, 0, MouseEvent.BUTTON1);
            assertThat(manager.mouseClicked(lineComponent, e)).isTrue();
            assertThat(manager.isInProgress()).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // enterGraceNoteInsert abort when host note won't fit — row 22
    // -------------------------------------------------------------------------

    @Nested
    class EnterGraceNoteInsertAbort {

        private MockedStatic<MessageCenter> messageCenterMock;
        private MockedStatic<EditModeManager> editModeManagerMock;
        private MockedStatic<InsertionSpacingCalculator> calcMock;

        @BeforeEach
        void setUp() {
            messageCenterMock = mockStatic(MessageCenter.class);
            editModeManagerMock = mockStatic(EditModeManager.class);
            calcMock = mockStatic(InsertionSpacingCalculator.class);
            var mockFrame = mock(MainFrame.class);
            var mockScore = mock(ScoreView.class);
            var mockRootPane = mock(JRootPane.class);
            when(mockRootPane.getInputMap(anyInt())).thenReturn(new InputMap());
            when(mockRootPane.getActionMap()).thenReturn(new ActionMap());
            when(mockFrame.getRootPane()).thenReturn(mockRootPane);
            when(mockFrame.requireScoreView()).thenReturn(mockScore);
            when(mockFrame.getScoreView()).thenReturn(mockScore);
            when(mockScore.getSelectionCoordinator()).thenReturn(mock(SelectionCoordinator.class));
            Actions.initialize(mockFrame);
        }

        @AfterEach
        void tearDown() throws Exception {
            // Drain pending invokeLater tasks (finish() posts invokeLater to re-enable
            // GRACE_EIGHTH_NOTE_ACTION) before resetting Actions so the lambda sees a
            // live action object rather than null.
            javax.swing.SwingUtilities.invokeAndWait(() -> {});
            Actions.resetForTest();
            calcMock.close();
            editModeManagerMock.close();
            messageCenterMock.close();
        }

        @Test
        void testFinishesWithCancelWhenHostNoteDoesNotFit() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            var hostPreview = ElementType.CROTCHET.newInstance();
            var line = detachedLine();
            line.addElement(graceNote);

            var lineComponent = mock(LineComponent.class);
            var layout = mock(LayoutResult.class);
            var column = mock(ElementColumn.class);

            // Set up layout so getLockedInsertionXSs returns a non-zero value
            when(column.getXSs()).thenReturn(5.0);
            when(column.getRightExtentSs()).thenReturn(1.0);
            when(layout.getElementColumn(graceNote)).thenReturn(column);
            when(lineComponent.getLayoutResult()).thenReturn(layout);
            var scoreViewStub = graceScoreViewStub();
            when(lineComponent.getScoreView()).thenReturn(scoreViewStub);

            editModeManagerMock.when(EditModeManager::getPreviewElement).thenReturn(hostPreview);

            // calculateInsertion returns a result that doesn't fit
            var mockResult = mock(InsertionSpacingCalculator.InsertionResult.class);
            when(mockResult.fitsWithinLine(anyDouble())).thenReturn(false);
            calcMock.when(() -> InsertionSpacingCalculator.calculateInsertion(
                any(), any(), anyInt(), any(), any()
            )).thenReturn(mockResult);

            // Set state and fields to trigger enterGraceNoteInsert via click (mouseReleased)
            var startPoint = new Point(100, 100);
            manager.setState(GraceModeManager.State.GRACE_NOTE);
            setField(manager, "mouseDownPoint", startPoint);
            setField(manager, "mouseDownTime", System.currentTimeMillis());
            manager.setGraceNote(graceNote);
            setField(manager, "graceNoteIndex", 0);
            manager.setGraceLine(line);
            manager.setGraceLineComponent(lineComponent);

            // click event (x,y same as startPoint → no drag)
            var e = mouseEvent(lineComponent, MouseEvent.MOUSE_RELEASED, 100, 100, MouseEvent.BUTTON1);
            assertThat(manager.mouseReleased(lineComponent, e)).isTrue();

            // enterGraceNoteInsert → computeHostInsertion returns null → finish(cancel=true) → INACTIVE
            assertThat(manager.isInProgress()).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // abort() removes the grace note without tracking (no undo step) — row 23
    // -------------------------------------------------------------------------

    @Nested
    class FinishCancel {

        private MockedStatic<MessageCenter> messageCenterMock;

        @BeforeEach
        void setUp() {
            messageCenterMock = mockStatic(MessageCenter.class);
            var mockFrame = mock(MainFrame.class);
            var mockScore = mock(ScoreView.class);
            var mockRootPane = mock(JRootPane.class);
            when(mockRootPane.getInputMap(anyInt())).thenReturn(new InputMap());
            when(mockRootPane.getActionMap()).thenReturn(new ActionMap());
            when(mockFrame.getRootPane()).thenReturn(mockRootPane);
            when(mockFrame.requireScoreView()).thenReturn(mockScore);
            when(mockFrame.getScoreView()).thenReturn(mockScore);
            when(mockScore.getSelectionCoordinator()).thenReturn(mock(SelectionCoordinator.class));
            Actions.initialize(mockFrame);
        }

        @AfterEach
        void tearDown() throws Exception {
            // Drain pending invokeLater tasks (finish() posts invokeLater to re-enable
            // GRACE_EIGHTH_NOTE_ACTION) before resetting Actions so the lambda sees a
            // live action object rather than null.
            javax.swing.SwingUtilities.invokeAndWait(() -> {});
            Actions.resetForTest();
            messageCenterMock.close();
        }

        @Test
        void testCancelRemovesGraceNoteWithoutModificationBracket() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            var line = detachedLine();
            line.addElement(graceNote);

            // detachedLine's song mock leaves withoutMutationTracking a no-op by default;
            // stub it to run its body so abort()'s untracked removal actually executes.
            doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; })
                .when(line.getSong()).withoutMutationTracking(any(Runnable.class));

            manager.setState(GraceModeManager.State.GRACE_NOTE);
            setField(manager, "mouseDownPoint", null);  // triggers abort()
            manager.setGraceNote(graceNote);
            setField(manager, "graceNoteIndex", 0);
            manager.setGraceLine(line);
            manager.setGraceLineComponent(mock(LineComponent.class));

            var e = mouseEvent(mock(LineComponent.class), MouseEvent.MOUSE_RELEASED, 0, 0, MouseEvent.BUTTON1);
            manager.mouseReleased(mock(LineComponent.class), e);

            // The grace note is removed via withoutMutationTracking, so no undo step is
            // created: the removal must not open a modification bracket.
            assertThat(line.elementCount()).isEqualTo(0);
            verify(line.getSong()).withoutMutationTracking(any(Runnable.class));
            verify(line.getSong(), never()).withModification(any(Runnable.class));
        }

        @Test
        void testCancelDoesNotRemoveWhenGraceNoteIsNull() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var line = detachedLine();
            var note = ElementType.CROTCHET.newInstance();
            line.addElement(note);

            manager.setState(GraceModeManager.State.GRACE_NOTE);
            setField(manager, "mouseDownPoint", null);
            manager.setGraceNote(null);  // no grace note to remove
            setField(manager, "graceNoteIndex", -1);
            manager.setGraceLine(line);
            manager.setGraceLineComponent(mock(LineComponent.class));

            var e = mouseEvent(mock(LineComponent.class), MouseEvent.MOUSE_RELEASED, 0, 0, MouseEvent.BUTTON1);
            manager.mouseReleased(mock(LineComponent.class), e);

            // Line element should remain untouched
            assertThat(line.elementCount()).isEqualTo(1);
        }
    }

    // -------------------------------------------------------------------------
    // finish(cancel=false) resets state — row 24
    // -------------------------------------------------------------------------

    @Nested
    class FinishResetsState {

        private MockedStatic<MessageCenter> messageCenterMock;

        @BeforeEach
        void setUp() {
            messageCenterMock = mockStatic(MessageCenter.class);
            var mockFrame = mock(MainFrame.class);
            var mockScore = mock(ScoreView.class);
            var mockRootPane = mock(JRootPane.class);
            when(mockRootPane.getInputMap(anyInt())).thenReturn(new InputMap());
            when(mockRootPane.getActionMap()).thenReturn(new ActionMap());
            when(mockFrame.getRootPane()).thenReturn(mockRootPane);
            when(mockFrame.requireScoreView()).thenReturn(mockScore);
            when(mockFrame.getScoreView()).thenReturn(mockScore);
            when(mockScore.getSelectionCoordinator()).thenReturn(mock(SelectionCoordinator.class));
            Actions.initialize(mockFrame);
        }

        @AfterEach
        void tearDown() throws Exception {
            // Drain pending invokeLater tasks (finish() posts invokeLater to re-enable
            // GRACE_EIGHTH_NOTE_ACTION) before resetting Actions so the lambda sees a
            // live action object rather than null.
            javax.swing.SwingUtilities.invokeAndWait(() -> {});
            Actions.resetForTest();
            messageCenterMock.close();
        }

        @Test
        void testFinishResetsAllStateFieldsToDefaults() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            var line = detachedLine();
            line.addElement(graceNote);

            manager.setState(GraceModeManager.State.GRACE_NOTE);
            setField(manager, "mouseDownPoint", null);  // triggers finish(cancel=true)
            manager.setGraceNote(graceNote);
            setField(manager, "graceNoteIndex", 0);
            manager.setGraceLine(line);
            manager.setGraceLineComponent(mock(LineComponent.class));
            manager.setPendingCancel(true);
            setField(manager, "pendingConnect", true);
            setField(manager, "justEnteredInsert", true);

            var e = mouseEvent(mock(LineComponent.class), MouseEvent.MOUSE_RELEASED, 0, 0, MouseEvent.BUTTON1);
            manager.mouseReleased(mock(LineComponent.class), e);

            // finish() must reset all state
            assertThat(manager.isInProgress()).isFalse();
            assertThat(manager.isPendingCancel()).isFalse();
            assertThat(manager.isPendingConnect()).isFalse();
            assertThat(manager.isJustEnteredInsert()).isFalse();
            assertThat(manager.getGraceNote()).isNull();
            assertThat(manager.getGraceLine()).isNull();
            assertThat(manager.getGraceLineComponent()).isNull();
            assertThat(manager.getGraceNoteIndex()).isEqualTo(-1);
        }

        @Test
        void testFinishPostsGraceModeStateDidChangeNotificationWithFalse() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            var line = detachedLine();
            line.addElement(graceNote);

            manager.setState(GraceModeManager.State.GRACE_NOTE);
            setField(manager, "mouseDownPoint", null);
            manager.setGraceNote(graceNote);
            setField(manager, "graceNoteIndex", 0);
            manager.setGraceLine(line);
            manager.setGraceLineComponent(mock(LineComponent.class));

            var e = mouseEvent(mock(LineComponent.class), MouseEvent.MOUSE_RELEASED, 0, 0, MouseEvent.BUTTON1);
            manager.mouseReleased(mock(LineComponent.class), e);

            var captor = ArgumentCaptor.forClass(Message.class);
            messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
            var notification = captor.getValue();
            assertThat(notification).isInstanceOf(GraceModeStateDidChangeNotification.class);
            assertThat(((GraceModeStateDidChangeNotification) notification).isActive()).isFalse();
        }

        /**
         * The teardown the host-preview glissando depends on (refs #650). Nothing else takes that
         * overlay down on the way out of grace mode, and the state its gate reads has just been
         * cleared — so without this call a commit would leave the preview line doubling the real
         * glissando it became, and an abort would leave one running to a removed grace note. Driven
         * through the abort path; {@code commit()} shares the same {@code resetState()}.
         */
        @Test
        void testFinishRebuildsThePreviewOverlays() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            var line = detachedLine();
            line.addElement(graceNote);

            manager.setState(GraceModeManager.State.GRACE_NOTE);
            setField(manager, "mouseDownPoint", null);  // triggers finish(cancel=true)
            manager.setGraceNote(graceNote);
            setField(manager, "graceNoteIndex", 0);
            manager.setGraceLine(line);
            manager.setGraceLineComponent(mock(LineComponent.class));

            var e = mouseEvent(mock(LineComponent.class), MouseEvent.MOUSE_RELEASED, 0, 0, MouseEvent.BUTTON1);

            try (var previewElementManagerMock = mockStatic(PreviewElementManager.class)) {
                manager.mouseReleased(mock(LineComponent.class), e);

                previewElementManagerMock.verify(PreviewElementManager::previewElementDidChange);
            }
        }
    }

    // -------------------------------------------------------------------------
    // hostPreviewGraceIndexOn — the grace→host preview glissando's gate (refs #650)
    // -------------------------------------------------------------------------

    @Nested
    class HostPreviewGraceIndexOn {

        /** Not 0, so an index that ignored grace mode and took the first element would not match. */
        private static final int GRACE_NOTE_INDEX = 2;

        private GraceModeManager manager;
        private LineComponent graceLineComponent;

        @BeforeEach
        void setUp() {
            manager = new GraceModeManager(editModeManager, selectionCoordinator);
            graceLineComponent = mock(LineComponent.class);
            manager.setGraceLineComponent(graceLineComponent);
            setField(manager, "graceNoteIndex", GRACE_NOTE_INDEX);
        }

        @Test
        void testReturnsTheGraceNoteIndexOnTheGraceLineDuringInsert() {
            manager.setState(GraceModeManager.State.GRACE_NOTE_INSERT);

            assertThat(manager.hostPreviewGraceIndexOn(graceLineComponent))
                .as("the host ghost is being positioned on this line -> its grace note's index")
                .isEqualTo(GRACE_NOTE_INDEX);
        }

        @Test
        void testReturnsMinusOneOnALineOtherThanTheGraceLine() {
            manager.setState(GraceModeManager.State.GRACE_NOTE_INSERT);

            assertThat(manager.hostPreviewGraceIndexOn(mock(LineComponent.class)))
                .as("the grace note is on a different line -> nothing to connect here")
                .isEqualTo(-1);
        }

        @Test
        void testReturnsMinusOneBeforeTheInsertPhase() {
            manager.setState(GraceModeManager.State.GRACE_NOTE);

            assertThat(manager.hostPreviewGraceIndexOn(graceLineComponent))
                .as("the grace note is still being placed -> there is no host ghost yet")
                .isEqualTo(-1);
        }

        @Test
        void testReturnsMinusOneWhenGraceModeIsInactive() {
            manager.setState(GraceModeManager.State.INACTIVE);

            assertThat(manager.hostPreviewGraceIndexOn(graceLineComponent))
                .as("grace mode is over -> the connecting line has nothing to draw")
                .isEqualTo(-1);
        }
    }

    // -------------------------------------------------------------------------
    // hasEligibleHostNote — row 25
    // (tested via mouseDragged pendingConnect logic)
    // -------------------------------------------------------------------------

    @Nested
    class HasEligibleHostNote {

        private MockedStatic<MessageCenter> messageCenterMock;
        private MockedStatic<PreviewElementManager> previewMock;

        @BeforeEach
        void setUp() {
            messageCenterMock = mockStatic(MessageCenter.class);
            previewMock = mockStatic(PreviewElementManager.class);
        }

        @AfterEach
        void tearDown() {
            previewMock.close();
            messageCenterMock.close();
        }

        @Test
        void testPendingConnectIsFalseWhenNoNextElement() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            // Line with only the grace note — no next element
            var line = detachedLine();
            line.addElement(graceNote);

            var lineComponent = mock(LineComponent.class);
            var layout = mock(LayoutResult.class);
            var column = mock(ElementColumn.class);

            // Position grace note far right so connectThreshold is at x=100
            when(layout.getElementXSs(graceNote)).thenReturn(10.0);
            when(layout.getElementColumn(graceNote)).thenReturn(column);
            when(column.getXSs()).thenReturn(10.0);
            when(column.getRightExtentSs()).thenReturn(2.0);
            when(lineComponent.getLayoutResult()).thenReturn(layout);
            var scoreViewStub = graceScoreViewStub();
            when(lineComponent.getScoreView()).thenReturn(scoreViewStub);

            manager.setState(GraceModeManager.State.GRACE_NOTE);
            manager.setGraceNote(graceNote);
            setField(manager, "graceNoteIndex", 0);
            manager.setGraceLine(line);
            manager.setGraceLineComponent(lineComponent);
            setField(manager, "mouseDownTime", System.currentTimeMillis() - GraceModeManager.MIN_DRAG_MILLIS);

            // Drag right past connectThreshold, but no eligible host
            var e = mouseEvent(lineComponent, MouseEvent.MOUSE_DRAGGED, 105, 0, MouseEvent.BUTTON1);
            manager.mouseDragged(lineComponent, e);

            assertThat(manager.isPendingConnect()).isFalse();
        }

        @Test
        void testPendingConnectIsFalseWhenNextElementIsNotPitchedNote() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            var restNote = ElementType.CROTCHET_REST.newInstance();
            var line = detachedLine();
            line.addElement(graceNote);
            line.addElement(restNote);  // next element is a rest, not a pitched note

            var lineComponent = mock(LineComponent.class);
            var layout = mock(LayoutResult.class);
            var column = mock(ElementColumn.class);

            when(layout.getElementXSs(graceNote)).thenReturn(10.0);
            when(layout.getElementColumn(graceNote)).thenReturn(column);
            when(column.getXSs()).thenReturn(10.0);
            when(column.getRightExtentSs()).thenReturn(2.0);
            when(lineComponent.getLayoutResult()).thenReturn(layout);
            var scoreViewStub = graceScoreViewStub();
            when(lineComponent.getScoreView()).thenReturn(scoreViewStub);

            manager.setState(GraceModeManager.State.GRACE_NOTE);
            manager.setGraceNote(graceNote);
            setField(manager, "graceNoteIndex", 0);
            manager.setGraceLine(line);
            manager.setGraceLineComponent(lineComponent);
            setField(manager, "mouseDownTime", System.currentTimeMillis() - GraceModeManager.MIN_DRAG_MILLIS);

            var e = mouseEvent(lineComponent, MouseEvent.MOUSE_DRAGGED, 105, 0, MouseEvent.BUTTON1);
            manager.mouseDragged(lineComponent, e);

            assertThat(manager.isPendingConnect()).isFalse();
        }

        @Test
        void testPendingConnectIsTrueWhenNextElementIsPitchedNote() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            var hostNote = ElementType.CROTCHET.newInstance();
            var line = detachedLine();
            line.addElement(graceNote);
            line.addElement(hostNote);  // pitched note follows grace note

            var lineComponent = mock(LineComponent.class);
            var layout = mock(LayoutResult.class);
            var column = mock(ElementColumn.class);

            when(layout.getElementXSs(graceNote)).thenReturn(10.0);
            when(layout.getElementColumn(graceNote)).thenReturn(column);
            when(column.getXSs()).thenReturn(10.0);
            when(column.getRightExtentSs()).thenReturn(2.0);
            when(lineComponent.getLayoutResult()).thenReturn(layout);
            var scoreViewStub = graceScoreViewStub();
            when(lineComponent.getScoreView()).thenReturn(scoreViewStub);

            manager.setState(GraceModeManager.State.GRACE_NOTE);
            manager.setGraceNote(graceNote);
            setField(manager, "graceNoteIndex", 0);
            manager.setGraceLine(line);
            manager.setGraceLineComponent(lineComponent);
            setField(manager, "mouseDownTime", System.currentTimeMillis() - GraceModeManager.MIN_DRAG_MILLIS);

            var e = mouseEvent(lineComponent, MouseEvent.MOUSE_DRAGGED, 105, 0, MouseEvent.BUTTON1);
            manager.mouseDragged(lineComponent, e);

            assertThat(manager.isPendingConnect()).isTrue();
        }
    }

    // -------------------------------------------------------------------------
    // GraceModeStateDidChangeNotification payloads — row 26
    // -------------------------------------------------------------------------

    @Nested
    class GraceModeStateNotification {

        private MockedStatic<MessageCenter> messageCenterMock;
        private MockedStatic<PreviewElementManager> previewMock;
        private MockedStatic<EditModeManager> editModeManagerMock;

        @BeforeEach
        void setUp() {
            messageCenterMock = mockStatic(MessageCenter.class);
            previewMock = mockStatic(PreviewElementManager.class);
            editModeManagerMock = mockStatic(EditModeManager.class);
            var mockFrame = mock(MainFrame.class);
            var mockScore = mock(ScoreView.class);
            var mockRootPane = mock(JRootPane.class);
            when(mockRootPane.getInputMap(anyInt())).thenReturn(new InputMap());
            when(mockRootPane.getActionMap()).thenReturn(new ActionMap());
            when(mockFrame.getRootPane()).thenReturn(mockRootPane);
            when(mockFrame.requireScoreView()).thenReturn(mockScore);
            when(mockFrame.getScoreView()).thenReturn(mockScore);
            when(mockScore.getSelectionCoordinator()).thenReturn(mock(SelectionCoordinator.class));
            Actions.initialize(mockFrame);
        }

        @AfterEach
        void tearDown() throws Exception {
            // Drain pending invokeLater tasks (finish() posts invokeLater to re-enable
            // GRACE_EIGHTH_NOTE_ACTION) before resetting Actions so the lambda sees a
            // live action object rather than null.
            javax.swing.SwingUtilities.invokeAndWait(() -> {});
            Actions.resetForTest();
            editModeManagerMock.close();
            previewMock.close();
            messageCenterMock.close();
        }

        @Test
        void testNotificationWithTruePostedOnEnterGraceNote() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            editModeManagerMock.when(EditModeManager::getPreviewElement)
                .thenReturn(ElementType.GRACE_QUAVER.newInstance());
            previewMock.when(PreviewElementManager::getCurrentXIndex).thenReturn(0);

            // Actions.initialize(mockFrame) was called in setUp, so QUARTER_NOTE_ACTION.mainFrame
            // already points at the mock. No save/restore needed.
            try (var calcMock = mockStatic(InsertionSpacingCalculator.class)) {
                calcMock.when(
                    () -> InsertionSpacingCalculator.hasRoomForGraceNote(any(), anyInt(), any(), any())
                ).thenReturn(true);

                var line = lineWith(ElementType.GRACE_QUAVER);
                var lineComponent = mock(LineComponent.class);
                when(lineComponent.getLine()).thenReturn(line);
                when(lineComponent.getLayoutResult()).thenReturn(null);
                var e = mouseEvent(lineComponent, MouseEvent.MOUSE_PRESSED, 50, 60, MouseEvent.BUTTON1);

                manager.mousePressed(lineComponent, e);

                var captor = ArgumentCaptor.forClass(Message.class);
                messageCenterMock.verify(() -> MessageCenter.post(captor.capture()),
                    org.mockito.Mockito.atLeastOnce());
                var graceModeNotification = captor.getAllValues().stream()
                    .filter(m -> m instanceof GraceModeStateDidChangeNotification)
                    .map(m -> (GraceModeStateDidChangeNotification) m)
                    .findFirst();
                assertThat(graceModeNotification).isPresent();
                assertThat(graceModeNotification.get().isActive()).isTrue();
            }
        }

        @Test
        void testNotificationWithFalsePostedOnFinish() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            var line = detachedLine();
            line.addElement(graceNote);

            manager.setState(GraceModeManager.State.GRACE_NOTE);
            setField(manager, "mouseDownPoint", null);
            manager.setGraceNote(graceNote);
            setField(manager, "graceNoteIndex", 0);
            manager.setGraceLine(line);
            manager.setGraceLineComponent(mock(LineComponent.class));

            var e = mouseEvent(mock(LineComponent.class), MouseEvent.MOUSE_RELEASED, 0, 0, MouseEvent.BUTTON1);
            manager.mouseReleased(mock(LineComponent.class), e);

            var captor = ArgumentCaptor.forClass(Message.class);
            messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
            assertThat(captor.getValue()).isInstanceOf(GraceModeStateDidChangeNotification.class);
            assertThat(((GraceModeStateDidChangeNotification) captor.getValue()).isActive()).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // enterGraceNote selects QUARTER_NOTE and clears embellishments — row 27
    // -------------------------------------------------------------------------

    @Nested
    class EnterGraceNoteActionGroupState {

        private MockedStatic<MessageCenter> messageCenterMock;
        private MockedStatic<PreviewElementManager> previewMock;
        private MockedStatic<EditModeManager> editModeManagerMock;

        @BeforeEach
        void setUp() {
            messageCenterMock = mockStatic(MessageCenter.class);
            previewMock = mockStatic(PreviewElementManager.class);
            editModeManagerMock = mockStatic(EditModeManager.class);
            var mockFrame = mock(MainFrame.class);
            var mockScore = mock(ScoreView.class);
            var mockRootPane = mock(JRootPane.class);
            when(mockRootPane.getInputMap(anyInt())).thenReturn(new InputMap());
            when(mockRootPane.getActionMap()).thenReturn(new ActionMap());
            when(mockFrame.getRootPane()).thenReturn(mockRootPane);
            when(mockFrame.requireScoreView()).thenReturn(mockScore);
            when(mockFrame.getScoreView()).thenReturn(mockScore);
            when(mockScore.getSelectionCoordinator()).thenReturn(mock(SelectionCoordinator.class));
            Actions.initialize(mockFrame);
        }

        @AfterEach
        void tearDown() throws Exception {
            // Drain pending invokeLater tasks (finish() posts invokeLater to re-enable
            // GRACE_EIGHTH_NOTE_ACTION) before resetting Actions so the lambda sees a
            // live action object rather than null. resetForTest() also prevents action-group
            // selection state from bleeding into subsequent tests.
            javax.swing.SwingUtilities.invokeAndWait(() -> {});
            Actions.resetForTest();
            editModeManagerMock.close();
            previewMock.close();
            messageCenterMock.close();
        }

        @Test
        void testEnterGraceNoteSelectsQuarterNoteAndClearsEmbellishments() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            editModeManagerMock.when(EditModeManager::getPreviewElement)
                .thenReturn(ElementType.GRACE_QUAVER.newInstance());
            previewMock.when(PreviewElementManager::getCurrentXIndex).thenReturn(0);

            // Pre-select a non-quarter duration and embellishments.
            // Actions.initialize(mockFrame) was called in setUp, so all actions reference
            // mockFrame — QUARTER_NOTE_ACTION.perform() calls mockFrame.requireScoreView()
            // which is already stubbed. No save/restore dance needed.
            Actions.DOT_ACTION_GROUP.setSelected(Actions.DOT_ACTION, true);
            Actions.ACCIDENTAL_ACTION_GROUP.setSelected(Actions.SHARP_ACTION, true);
            Actions.STACCATO_ACTION.setSelected(true);
            Actions.ACCIDENTAL_IN_PARENS_ACTION.setSelected(true);
            Actions.ACCENT_ACTION.setSelected(true);

            try (var calcMock = mockStatic(InsertionSpacingCalculator.class)) {
                calcMock.when(
                    () -> InsertionSpacingCalculator.hasRoomForGraceNote(any(), anyInt(), any(), any())
                ).thenReturn(true);

                var line = lineWith(ElementType.GRACE_QUAVER);
                var lineComponent = mock(LineComponent.class);
                when(lineComponent.getLine()).thenReturn(line);
                when(lineComponent.getLayoutResult()).thenReturn(null);
                var e = mouseEvent(lineComponent, MouseEvent.MOUSE_PRESSED, 50, 60, MouseEvent.BUTTON1);

                manager.mousePressed(lineComponent, e);
            }

            // enterGraceNote must have selected QUARTER_NOTE_ACTION
            assertThat(Actions.DURATION_ACTION_GROUP.getSelected())
                .as("DURATION_ACTION_GROUP should be QUARTER_NOTE_ACTION after entering grace note mode")
                .isSameAs(Actions.QUARTER_NOTE_ACTION);

            // All embellishment action groups must be cleared
            assertThat(Actions.DOT_ACTION_GROUP.getSelected())
                .as("DOT_ACTION_GROUP should be cleared after entering grace note mode")
                .isNull();
            assertThat(Actions.ACCIDENTAL_ACTION_GROUP.getSelected())
                .as("ACCIDENTAL_ACTION_GROUP should be cleared after entering grace note mode")
                .isNull();
            assertThat(Actions.STACCATO_ACTION.isSelected())
                .as("STACCATO_ACTION should be cleared after entering grace note mode")
                .isFalse();
            assertThat(Actions.ACCIDENTAL_IN_PARENS_ACTION.isSelected())
                .as("ACCIDENTAL_IN_PARENS_ACTION should be cleared after entering grace note mode")
                .isFalse();
            assertThat(Actions.ACCENT_ACTION.isSelected())
                .as("ACCENT_ACTION should be cleared after entering grace note mode")
                .isFalse();
        }

        /**
         * The decorations must be cleared <em>before</em> the duration is selected, not merely
         * by the time enterGraceNote returns. Selecting a duration synchronously posts
         * {@link DurationWasSelectedNotification}, whose handler rebuilds the preview element
         * and decorates it from whatever toggles are selected at that instant. That preview is
         * the very object inserted as the host note, and nothing re-decorates it afterwards, so
         * clearing the toggles after the selection leaves a clean toolbar above a host note that
         * still carries the grace note's accidental, dots and articulations.
         */
        @Test
        void testClearsEmbellishmentsBeforeSelectingHostDuration() {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            editModeManagerMock.when(EditModeManager::getPreviewElement)
                .thenReturn(ElementType.GRACE_QUAVER.newInstance());
            previewMock.when(PreviewElementManager::getCurrentXIndex).thenReturn(0);

            Actions.DOT_ACTION_GROUP.setSelected(Actions.DOT_ACTION, true);
            Actions.ACCIDENTAL_ACTION_GROUP.setSelected(Actions.SHARP_ACTION, true);
            Actions.STACCATO_ACTION.setSelected(true);
            Actions.ACCIDENTAL_IN_PARENS_ACTION.setSelected(true);
            Actions.ACCENT_ACTION.setSelected(true);

            // Sample the toggles at the moment the duration notification is posted — the
            // instant the real handler would build and decorate the host preview.
            var decorationsWereSet = new boolean[1];
            var durationWasPosted = new boolean[1];

            messageCenterMock.when(() -> MessageCenter.post(any())).thenAnswer(invocation -> {
                if (invocation.getArgument(0) instanceof DurationWasSelectedNotification) {
                    durationWasPosted[0] = true;
                    decorationsWereSet[0] =
                        Actions.DOT_ACTION_GROUP.getSelected() != null
                            || Actions.ACCIDENTAL_ACTION_GROUP.getSelected() != null
                            || Actions.STACCATO_ACTION.isSelected()
                            || Actions.ACCIDENTAL_IN_PARENS_ACTION.isSelected()
                            || Actions.ACCENT_ACTION.isSelected();
                }

                return null;
            });

            try (var calcMock = mockStatic(InsertionSpacingCalculator.class)) {
                calcMock.when(
                    () -> InsertionSpacingCalculator.hasRoomForGraceNote(any(), anyInt(), any(), any())
                ).thenReturn(true);

                var line = lineWith(ElementType.GRACE_QUAVER);
                var lineComponent = mock(LineComponent.class);
                when(lineComponent.getLine()).thenReturn(line);
                when(lineComponent.getLayoutResult()).thenReturn(null);
                var e = mouseEvent(lineComponent, MouseEvent.MOUSE_PRESSED, 50, 60, MouseEvent.BUTTON1);

                manager.mousePressed(lineComponent, e);
            }

            // Guards the assertion below: if the notification stopped being posted, the
            // sampling never runs and decorationsWereSet stays trivially false.
            assertThat(durationWasPosted[0])
                .as("selecting the host duration should post DurationWasSelectedNotification")
                .isTrue();

            assertThat(decorationsWereSet[0])
                .as("embellishments must already be cleared when the host preview is rebuilt")
                .isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // enterGraceNotePaired — the automatic grace-host melisma (#599/#600)
    //
    // Driven through mouseReleased with pendingConnect set: that is the drag-right
    // commit path, the only one that reaches the connectNext == true branch where an
    // existing host's syllable is handed to the newly paired grace note.
    // -------------------------------------------------------------------------

    @Nested
    class EnterGraceNotePairedMelisma {

        private static final int VERSE = 1;
        private static final int SECOND_VERSE = 2;
        private static final int GRACE_INDEX = 0;
        private static final int HOST_INDEX = 1;
        private static final String SYLLABLE = "glo";
        private static final String SECOND_VERSE_SYLLABLE = "ry";
        /** Grace and host must differ in pitch, or the pairing is rejected before it commits. */
        private static final int GRACE_STAFF_POSITION = 2;
        private static final int HOST_STAFF_POSITION = 4;
        private static final int MOUSE_DOWN_X = 100;
        private static final int MOUSE_DOWN_Y = 100;
        /** Far enough right of MOUSE_DOWN_X to be classified as a drag, not a click. */
        private static final int RELEASE_X = 200;

        private MockedStatic<MessageCenter> messageCenterMock;

        @BeforeEach
        void setUp() {
            messageCenterMock = mockStatic(MessageCenter.class);
            var mockFrame = mock(MainFrame.class);
            var mockScore = mock(ScoreView.class);
            var mockRootPane = mock(JRootPane.class);
            when(mockRootPane.getInputMap(anyInt())).thenReturn(new InputMap());
            when(mockRootPane.getActionMap()).thenReturn(new ActionMap());
            when(mockFrame.getRootPane()).thenReturn(mockRootPane);
            when(mockFrame.requireScoreView()).thenReturn(mockScore);
            when(mockFrame.getScoreView()).thenReturn(mockScore);
            when(mockScore.getSelectionCoordinator()).thenReturn(mock(SelectionCoordinator.class));
            Actions.initialize(mockFrame);
        }

        @AfterEach
        void tearDown() throws Exception {
            // Drain pending invokeLater tasks (commit() posts invokeLater to re-enable
            // GRACE_EIGHTH_NOTE_ACTION) before resetting Actions.
            javax.swing.SwingUtilities.invokeAndWait(() -> {});
            Actions.resetForTest();
            messageCenterMock.close();
        }

        @Test
        void testHostSyllableMovesToGraceAndBecomesAMelismaAcrossTheHost() {
            var line = graceAndHostLine();
            setSyllable(line, HOST_INDEX, VERSE, SYLLABLE);

            connectByDragRight(line);

            // The pairing itself must have committed, or the lyric assertions below
            // would be asserting against an untouched line.
            assertThat(line.isPairedGraceNote(GRACE_INDEX))
                .as("the drag-right commit must connect the grace note to its host")
                .isTrue();

            var graceLyric = requireLyric(line, GRACE_INDEX, VERSE);
            assertThat(graceLyric.text())
                .as("the syllable of a grace-host pair belongs to the grace note")
                .isEqualTo(SYLLABLE);
            assertThat(graceLyric.syllabic()).isEqualTo(Lyric.Syllabic.SINGLE);
            assertThat(graceLyric.extend()).isEqualTo(Lyric.Extend.START);

            var hostLyric = requireLyric(line, HOST_INDEX, VERSE);
            assertThat(hostLyric.text())
                .as("the host may not carry a syllable of its own")
                .isEmpty();
            assertThat(hostLyric.syllabic()).isNull();
            assertThat(hostLyric.extend()).isEqualTo(Lyric.Extend.STOP);
            assertThat(hostLyric.isCarrier()).isTrue();
        }

        @Test
        void testNoLyricIsCreatedWhenTheHostCarriesNone() {
            var line = graceAndHostLine();

            connectByDragRight(line);

            assertThat(line.isPairedGraceNote(GRACE_INDEX))
                .as("the drag-right commit must connect the grace note to its host")
                .isTrue();

            // Nothing to transfer and nothing to extend: neither element may end up with a
            // phantom empty lyric or a stray melisma carrier.
            assertThat(lyricAt(line, GRACE_INDEX, VERSE)).isNull();
            assertThat(lyricAt(line, HOST_INDEX, VERSE)).isNull();
        }

        @Test
        void testEveryVerseTransfersAndGetsItsOwnMelisma() {
            var line = graceAndHostLine();
            setSyllable(line, HOST_INDEX, VERSE, SYLLABLE);
            setSyllable(line, HOST_INDEX, SECOND_VERSE, SECOND_VERSE_SYLLABLE);

            connectByDragRight(line);

            var firstVerseGraceLyric = requireLyric(line, GRACE_INDEX, VERSE);
            assertThat(firstVerseGraceLyric.text()).isEqualTo(SYLLABLE);
            assertThat(firstVerseGraceLyric.extend()).isEqualTo(Lyric.Extend.START);
            assertThat(requireLyric(line, HOST_INDEX, VERSE).extend()).isEqualTo(Lyric.Extend.STOP);

            var secondVerseGraceLyric = requireLyric(line, GRACE_INDEX, SECOND_VERSE);
            assertThat(secondVerseGraceLyric.text()).isEqualTo(SECOND_VERSE_SYLLABLE);
            assertThat(secondVerseGraceLyric.extend()).isEqualTo(Lyric.Extend.START);

            var secondVerseHostLyric = requireLyric(line, HOST_INDEX, SECOND_VERSE);
            assertThat(secondVerseHostLyric.extend()).isEqualTo(Lyric.Extend.STOP);
            assertThat(secondVerseHostLyric.text()).isEmpty();
        }

        /** A line of [grace quaver, crotchet host] with distinct pitches and no glissando yet. */
        private Line graceAndHostLine() {
            var line = detachedLine();
            var grace = ElementType.GRACE_QUAVER.newInstance();
            grace.setStaffPosition(GRACE_STAFF_POSITION);
            line.addElement(grace);
            var host = ElementType.CROTCHET.newInstance();
            host.setStaffPosition(HOST_STAFF_POSITION);
            line.addElement(host);
            return line;
        }

        /**
         * Puts the manager in the mid-drag GRACE_NOTE state with pendingConnect set, then
         * releases the mouse well to the right of the mouse-down point — the sequence
         * mouseReleased routes to enterGraceNotePaired(connectNext = true).
         */
        private void connectByDragRight(Line line) {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var lineComponent = mock(LineComponent.class);

            manager.setState(GraceModeManager.State.GRACE_NOTE);
            manager.setGraceNote(line.getElement(GRACE_INDEX));
            setField(manager, "graceNoteIndex", GRACE_INDEX);
            manager.setGraceLine(line);
            manager.setGraceLineComponent(lineComponent);
            setField(manager, "mouseDownPoint", new Point(MOUSE_DOWN_X, MOUSE_DOWN_Y));
            setField(manager, "mouseDownTime", System.currentTimeMillis() - GraceModeManager.MIN_DRAG_MILLIS);
            setField(manager, "pendingConnect", true);

            var e = mouseEvent(lineComponent, MouseEvent.MOUSE_RELEASED, RELEASE_X, MOUSE_DOWN_Y, MouseEvent.BUTTON1);
            assertThat(manager.mouseReleased(lineComponent, e))
                .as("the release must be consumed by grace mode")
                .isTrue();
        }

        /** Writes a plain (no-melisma) syllable directly — detachedLine suspends tracking. */
        private static void setSyllable(Line line, int index, int verse, String text) {
            line.getElement(index).setLyricForVerse(verse, Lyric.Syllabic.SINGLE, false, text, Lyric.Extend.NONE);
        }

        private static @Nullable Lyric lyricAt(Line line, int index, int verse) {
            return line.getElement(index).getLyricForVerse(verse);
        }

        /** {@link #lyricAt} for assertions that dereference the lyric — fails when there is none. */
        private static Lyric requireLyric(Line line, int index, int verse) {
            var lyric = lyricAt(line, index, verse);

            if (lyric == null) {
                throw new AssertionError("expected a verse " + verse + " lyric at index " + index);
            }

            return lyric;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void resetStaticInstance(@Nullable GraceModeManager value) {
        GraceModeManager.setInstance(value);
    }

    private static void setField(Object target, String name, @Nullable Object value) {
        try {
            var field = findField(target.getClass(), name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /** Walks the class hierarchy to find a declared field by name. */
    private static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        var current = clazz;

        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }

        throw new NoSuchFieldException("Field '" + name + "' not found in " + clazz);
    }

    private static MouseEvent mouseEvent(Object source, int id, int x, int y, int button) {
        return new MouseEvent(
            (java.awt.Component) source,
            id,
            System.currentTimeMillis(),
            0,
            x, y, x, y,
            1,
            false,
            button
        );
    }

    /**
     * Returns a mock ScoreView with an identity ViewScale, so the grace-mode threshold
     * checks (which convert the view-pixel event x to document pixels via the line
     * component's ScoreView) resolve to a 1:1 mapping under test.
     */
    private static ScoreView graceScoreViewStub() {
        var scoreView = mock(ScoreView.class);
        when(scoreView.getViewScale()).thenReturn(ViewScale.IDENTITY);
        return scoreView;
    }
}
