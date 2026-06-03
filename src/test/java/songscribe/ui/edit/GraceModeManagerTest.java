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
import songscribe.dom.ScaleContext;
import songscribe.dom.StaffElement;
import songscribe.layout.ElementColumn;
import songscribe.layout.HorizontalSpacingCalculator;
import songscribe.layout.InsertionSpacingCalculator;
import songscribe.layout.LayoutResult;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.notification.GraceModeStateDidChangeNotification;
import songscribe.ui.action.Actions;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.component.score.PreviewElementManager;
import songscribe.ui.selection.SelectionCoordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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
    void tearDown() throws Exception {
        // Reset the static singleton so tests don't bleed into each other.
        resetStaticInstance(null);
    }

    // -------------------------------------------------------------------------
    // isActive / isInProgress — rows 1-2
    // -------------------------------------------------------------------------

    @Nested
    class IsActiveAndIsInProgress {

        @Test
        void testIsActiveReturnsFalseWhenNoInstance() throws Exception {
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
        void testIsActiveReturnsTrueWhenStateIsNonInactive() throws Exception {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            setField(manager, "state", GraceModeManager.State.GRACE_NOTE);
            assertThat(GraceModeManager.isActive()).isTrue();
            assertThat(manager.isInProgress()).isTrue();
        }

        @Test
        void testIsInProgressIsFalseOnlyForInactiveState() throws Exception {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);

            for (var state : GraceModeManager.State.values()) {
                setField(manager, "state", state);
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
        void testIsPendingCancelReturnsFalseWhenNoInstance() throws Exception {
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
        void testIsPendingCancelReturnsFalseForDifferentElement() throws Exception {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            var otherElement = ElementType.CROTCHET.newInstance();

            setField(manager, "pendingCancel", true);
            setField(manager, "graceNote", graceNote);

            // Different instance — identity check must fail
            assertThat(GraceModeManager.isPendingCancel(otherElement)).isFalse();
        }

        @Test
        void testIsPendingCancelReturnsTrueForExactGraceNoteInstance() throws Exception {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var graceNote = ElementType.GRACE_QUAVER.newInstance();

            setField(manager, "pendingCancel", true);
            setField(manager, "graceNote", graceNote);

            assertThat(GraceModeManager.isPendingCancel(graceNote)).isTrue();
        }
    }

    // -------------------------------------------------------------------------
    // getCancelThresholdPx / getConnectThresholdPx — row 4
    // -------------------------------------------------------------------------

    @Nested
    class ThresholdPx {

        @Test
        void testCancelThresholdReturnsMinus1WhenNoInstance() throws Exception {
            resetStaticInstance(null);
            assertThat(GraceModeManager.getCancelThresholdPx()).isEqualTo(-1);
        }

        @Test
        void testConnectThresholdReturnsMinus1WhenNoInstance() throws Exception {
            resetStaticInstance(null);
            assertThat(GraceModeManager.getConnectThresholdPx()).isEqualTo(-1);
        }

        @Test
        void testCancelThresholdReturnsMinus1WhenGraceNoteIsNull() throws Exception {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var lineComponent = mock(LineComponent.class);
            setField(manager, "graceNote", null);
            setField(manager, "graceLineComponent", lineComponent);
            assertThat(GraceModeManager.getCancelThresholdPx()).isEqualTo(-1);
        }

        @Test
        void testCancelThresholdReturnsMinus1WhenLineComponentIsNull() throws Exception {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            setField(manager, "graceNote", ElementType.GRACE_QUAVER.newInstance());
            setField(manager, "graceLineComponent", null);
            assertThat(GraceModeManager.getCancelThresholdPx()).isEqualTo(-1);
        }

        @Test
        void testCancelThresholdReturnsMinus1WhenLayoutIsNull() throws Exception {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var lineComponent = mock(LineComponent.class);
            when(lineComponent.getLayoutResult()).thenReturn(null);
            setField(manager, "graceNote", ElementType.GRACE_QUAVER.newInstance());
            setField(manager, "graceLineComponent", lineComponent);
            assertThat(GraceModeManager.getCancelThresholdPx()).isEqualTo(-1);
        }

        @Test
        void testCancelThresholdIsGraceXPxMinusSlopPx() throws Exception {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            var lineComponent = mock(LineComponent.class);
            var layout = mock(LayoutResult.class);

            // Place grace note at xSs = 10.0 → with 8 px/ss → 80 px
            when(layout.getElementXSs(graceNote)).thenReturn(10.0);
            when(lineComponent.getLayoutResult()).thenReturn(layout);
            setField(manager, "graceNote", graceNote);
            setField(manager, "graceLineComponent", lineComponent);

            var expectedPx = (int) Math.round(10.0 * ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE) - GraceModeManager.GRACE_SLOP_PX;
            assertThat(GraceModeManager.getCancelThresholdPx()).isEqualTo(expectedPx);
        }

        @Test
        void testConnectThresholdReturnsMinus1WhenColumnIsNull() throws Exception {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            var lineComponent = mock(LineComponent.class);
            var layout = mock(LayoutResult.class);

            when(layout.getElementColumn(graceNote)).thenReturn(null);
            when(lineComponent.getLayoutResult()).thenReturn(layout);
            setField(manager, "graceNote", graceNote);
            setField(manager, "graceLineComponent", lineComponent);

            assertThat(GraceModeManager.getConnectThresholdPx()).isEqualTo(-1);
        }

        @Test
        void testConnectThresholdIsRightEdgePxPlusSlopPx() throws Exception {
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
            setField(manager, "graceNote", graceNote);
            setField(manager, "graceLineComponent", lineComponent);

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
        void testReturnsZeroWhenGraceNoteIsNull() throws Exception {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            setField(manager, "graceNote", null);
            setField(manager, "graceLine", detachedLine());
            setField(manager, "graceLineComponent", mock(LineComponent.class));
            assertThat(manager.getLockedInsertionXSs()).isEqualTo(0.0);
        }

        @Test
        void testReturnsZeroWhenGraceLineIsNull() throws Exception {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            setField(manager, "graceNote", ElementType.GRACE_QUAVER.newInstance());
            setField(manager, "graceLine", null);
            setField(manager, "graceLineComponent", mock(LineComponent.class));
            assertThat(manager.getLockedInsertionXSs()).isEqualTo(0.0);
        }

        @Test
        void testReturnsZeroWhenGraceLineComponentIsNull() throws Exception {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            setField(manager, "graceNote", ElementType.GRACE_QUAVER.newInstance());
            setField(manager, "graceLine", detachedLine());
            setField(manager, "graceLineComponent", null);
            assertThat(manager.getLockedInsertionXSs()).isEqualTo(0.0);
        }

        @Test
        void testReturnsZeroWhenLayoutIsNull() throws Exception {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var lineComponent = mock(LineComponent.class);
            when(lineComponent.getLayoutResult()).thenReturn(null);
            setField(manager, "graceNote", ElementType.GRACE_QUAVER.newInstance());
            setField(manager, "graceLine", detachedLine());
            setField(manager, "graceLineComponent", lineComponent);
            assertThat(manager.getLockedInsertionXSs()).isEqualTo(0.0);
        }

        @Test
        void testReturnsZeroWhenColumnIsNull() throws Exception {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            var lineComponent = mock(LineComponent.class);
            var layout = mock(LayoutResult.class);
            when(layout.getElementColumn(graceNote)).thenReturn(null);
            when(lineComponent.getLayoutResult()).thenReturn(layout);
            setField(manager, "graceNote", graceNote);
            setField(manager, "graceLine", detachedLine());
            setField(manager, "graceLineComponent", lineComponent);
            assertThat(manager.getLockedInsertionXSs()).isEqualTo(0.0);
        }

        @Test
        void testComputedValueIsColumnXPlusRightExtentPlusGapPlusHostLeftExtent() throws Exception {
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

            setField(manager, "graceNote", graceNote);
            setField(manager, "graceLine", detachedLine());
            setField(manager, "graceLineComponent", lineComponent);

            // EditModeManager.getPreviewElement() is static — must mock statically.
            // No accidental on hostPreview → hostLeftExtentSs = 0.
            // Expected = xSs + rightExtentSs + GRACE_NOTE_GAP_SS + |hostLeftExtentSs|
            try (var emMock = mockStatic(EditModeManager.class)) {
                emMock.when(EditModeManager::getPreviewElement).thenReturn(hostPreview);
                var expected = 3.0 + 1.5 + HorizontalSpacingCalculator.GRACE_NOTE_GAP_SS + 0.0;
                assertThat(manager.getLockedInsertionXSs()).isEqualTo(expected);
            }
        }

        @Test
        void testUsesZeroHostLeftExtentWhenPreviewElementIsNull() throws Exception {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            var lineComponent = mock(LineComponent.class);
            var layout = mock(LayoutResult.class);
            var column = mock(ElementColumn.class);

            when(column.getXSs()).thenReturn(4.0);
            when(column.getRightExtentSs()).thenReturn(2.0);
            when(layout.getElementColumn(graceNote)).thenReturn(column);
            when(lineComponent.getLayoutResult()).thenReturn(layout);

            setField(manager, "graceNote", graceNote);
            setField(manager, "graceLine", detachedLine());
            setField(manager, "graceLineComponent", lineComponent);

            // EditModeManager.getPreviewElement() is static — must mock statically.
            try (var emMock = mockStatic(EditModeManager.class)) {
                emMock.when(EditModeManager::getPreviewElement).thenReturn(null);
                var expected = 4.0 + 2.0 + HorizontalSpacingCalculator.GRACE_NOTE_GAP_SS;
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
        // enterGraceNote triggers Actions.DURATION_ACTION_GROUP.select() → applyToSelectionIfActive()
        // which calls MainFrame.getInstance().requireScoreView(). Mock the singleton chain.
        private MockedStatic<MainFrame> mainFrameMock;

        @BeforeEach
        void setUp() {
            messageCenterMock = mockStatic(MessageCenter.class);
            editModeManagerMock = mockStatic(EditModeManager.class);
            mainFrameMock = mockStatic(MainFrame.class);
            var mockFrame = mock(MainFrame.class);
            var mockScore = mock(ScoreView.class);
            var mockRootPane = mock(JRootPane.class);
            when(mockRootPane.getInputMap(anyInt())).thenReturn(new InputMap());
            when(mockRootPane.getActionMap()).thenReturn(new ActionMap());
            mainFrameMock.when(MainFrame::getInstance).thenReturn(mockFrame);
            when(mockFrame.getRootPane()).thenReturn(mockRootPane);
            when(mockFrame.requireScoreView()).thenReturn(mockScore);
            when(mockFrame.getScoreView()).thenReturn(mockScore);
            when(mockScore.getSelectionCoordinator()).thenReturn(mock(SelectionCoordinator.class));
        }

        @AfterEach
        void tearDown() {
            mainFrameMock.close();
            editModeManagerMock.close();
            messageCenterMock.close();
        }

        @Test
        void testReturnsTrueAndConsumesWhenStateIsGraceNoteInsert() throws Exception {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            setField(manager, "state", GraceModeManager.State.GRACE_NOTE_INSERT);
            var lineComponent = mock(LineComponent.class);
            var e = mouseEvent(lineComponent, MouseEvent.MOUSE_PRESSED, 0, 0, MouseEvent.BUTTON1);

            assertThat(manager.mousePressed(lineComponent, e)).isTrue();
        }

        @Test
        void testReturnsFalseWhenStateIsGraceNote() throws Exception {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            setField(manager, "state", GraceModeManager.State.GRACE_NOTE);
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
                    () -> InsertionSpacingCalculator.hasRoomForGraceNote(any(), anyInt(), any())
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
                    () -> InsertionSpacingCalculator.hasRoomForGraceNote(any(), anyInt(), any())
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
        void testReturnsTrueAndConsumesWhenStateIsGraceNoteInsert() throws Exception {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            setField(manager, "state", GraceModeManager.State.GRACE_NOTE_INSERT);
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
        void testFinishesWithCancelWhenMouseDownPointIsNull() throws Exception {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var line = detachedLine();
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            line.addElement(graceNote);

            setField(manager, "state", GraceModeManager.State.GRACE_NOTE);
            setField(manager, "mouseDownPoint", null);
            setField(manager, "graceNote", graceNote);
            setField(manager, "graceLine", line);
            setField(manager, "graceNoteIndex", 0);

            var lineComponent = mock(LineComponent.class);
            var e = mouseEvent(lineComponent, MouseEvent.MOUSE_RELEASED, 0, 0, MouseEvent.BUTTON1);

            assertThat(manager.mouseReleased(lineComponent, e)).isTrue();
            // finish(cancel=true) removes the grace note and resets to INACTIVE
            assertThat(manager.isInProgress()).isFalse();
        }

        @Test
        void testDragLeftWithPendingCancelFinishesWithCancel() throws Exception {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var line = detachedLine();
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            line.addElement(graceNote);

            // Simulate: state=GRACE_NOTE, pendingCancel=true, drag time >= MIN_DRAG_MILLIS
            var startPoint = new Point(100, 100);
            setField(manager, "state", GraceModeManager.State.GRACE_NOTE);
            setField(manager, "pendingCancel", true);
            setField(manager, "mouseDownPoint", startPoint);
            // Make the down time old enough to count as a drag
            setField(manager, "mouseDownTime", System.currentTimeMillis() - GraceModeManager.MIN_DRAG_MILLIS);
            setField(manager, "graceNote", graceNote);
            setField(manager, "graceLine", line);
            setField(manager, "graceNoteIndex", 0);

            var lineComponent = mock(LineComponent.class);
            // Move far left to clearly exceed GRACE_SLOP_PX
            var e = mouseEvent(lineComponent, MouseEvent.MOUSE_RELEASED, 50, 100, MouseEvent.BUTTON1);

            assertThat(manager.mouseReleased(lineComponent, e)).isTrue();
            // finish(cancel=true) resets to INACTIVE
            assertThat(manager.isInProgress()).isFalse();
        }

        @Test
        void testClickTransitionsToGraceNoteInsertState() throws Exception {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var line = detachedLine();
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            line.addElement(graceNote);
            var lineComponent = mock(LineComponent.class);
            when(lineComponent.getLayoutResult()).thenReturn(null);

            // Short time since mouse-down → classified as click
            var startPoint = new Point(100, 100);
            setField(manager, "state", GraceModeManager.State.GRACE_NOTE);
            setField(manager, "mouseDownPoint", startPoint);
            setField(manager, "mouseDownTime", System.currentTimeMillis());
            setField(manager, "graceNote", graceNote);
            setField(manager, "graceLine", line);
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
        void testReturnsTrueAndConsumesWhenStateIsGraceNoteInsert() throws Exception {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            setField(manager, "state", GraceModeManager.State.GRACE_NOTE_INSERT);
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
        void testPendingCancelSetWhenDragLeftOfGraceNote() throws Exception {
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

            setField(manager, "state", GraceModeManager.State.GRACE_NOTE);
            setField(manager, "graceNote", graceNote);
            setField(manager, "graceLine", detachedLine());
            setField(manager, "graceLineComponent", lineComponent);
            // Simulate an old mouse-down time to make isDrag = true
            setField(manager, "mouseDownTime", System.currentTimeMillis() - GraceModeManager.MIN_DRAG_MILLIS);

            // Mouse x <= cancelThreshold (76) → pendingCancel should become true
            var e = mouseEvent(lineComponent, MouseEvent.MOUSE_DRAGGED, 70, 0, MouseEvent.BUTTON1);
            var result = manager.mouseDragged(lineComponent, e);

            assertThat(result).isTrue();
            assertThat((Boolean) getField(manager, "pendingCancel")).isTrue();
        }

        @Test
        void testPendingConnectSetAndGlissandoAddedWhenDragRightWithEligibleHost() throws Exception {
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

            setField(manager, "state", GraceModeManager.State.GRACE_NOTE);
            setField(manager, "graceNote", graceNote);
            setField(manager, "graceNoteIndex", 0);
            setField(manager, "graceLine", line);
            setField(manager, "graceLineComponent", lineComponent);
            setField(manager, "mouseDownTime", System.currentTimeMillis() - GraceModeManager.MIN_DRAG_MILLIS);

            // Mouse x >= connectThreshold (100) → pendingConnect should become true
            var e = mouseEvent(lineComponent, MouseEvent.MOUSE_DRAGGED, 105, 0, MouseEvent.BUTTON1);
            manager.mouseDragged(lineComponent, e);

            assertThat((Boolean) getField(manager, "pendingConnect")).isTrue();
            assertThat(graceNote.getGlissando()).isNotNull();
        }

        @Test
        void testGlissandoRemovedWhenPendingConnectBecomesFlase() throws Exception {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            // Pre-attach a glissando (as if drag-right had fired before)
            graceNote.setGlissando(StaffElement.Glissando.Type.CONNECTED);

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

            setField(manager, "state", GraceModeManager.State.GRACE_NOTE);
            setField(manager, "graceNote", graceNote);
            setField(manager, "graceNoteIndex", 0);
            setField(manager, "graceLine", line);
            setField(manager, "graceLineComponent", lineComponent);
            setField(manager, "pendingConnect", true);
            setField(manager, "mouseDownTime", System.currentTimeMillis() - GraceModeManager.MIN_DRAG_MILLIS);

            // Mouse at x=50 → not right of grace note → pendingConnect = false
            var e = mouseEvent(lineComponent, MouseEvent.MOUSE_DRAGGED, 50, 0, MouseEvent.BUTTON1);
            manager.mouseDragged(lineComponent, e);

            assertThat((Boolean) getField(manager, "pendingConnect")).isFalse();
            assertThat(graceNote.getGlissando()).isNull();
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
        void testReturnsTrueWhenInProgressButNotGraceNoteInsertState() throws Exception {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            setField(manager, "state", GraceModeManager.State.GRACE_NOTE);
            var lineComponent = mock(LineComponent.class);
            var e = mouseEvent(lineComponent, MouseEvent.MOUSE_CLICKED, 0, 0, MouseEvent.BUTTON1);

            // Active in non-INSERT state — consume to prevent normal insertion
            assertThat(manager.mouseClicked(lineComponent, e)).isTrue();
        }

        @Test
        void testJustEnteredInsertFlagSuppressesFirstClick() throws Exception {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            setField(manager, "state", GraceModeManager.State.GRACE_NOTE_INSERT);
            setField(manager, "justEnteredInsert", true);
            var lineComponent = mock(LineComponent.class);
            var e = mouseEvent(lineComponent, MouseEvent.MOUSE_CLICKED, 0, 0, MouseEvent.BUTTON1);

            assertThat(manager.mouseClicked(lineComponent, e)).isTrue();
            // Flag must be cleared after suppression
            assertThat((Boolean) getField(manager, "justEnteredInsert")).isFalse();
        }

        @Test
        void testCancelWhenClickOnDifferentLine() throws Exception {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            var line = detachedLine();
            line.addElement(graceNote);

            var graceLineComponent = mock(LineComponent.class);
            var otherLineComponent = mock(LineComponent.class);

            setField(manager, "state", GraceModeManager.State.GRACE_NOTE_INSERT);
            setField(manager, "graceNote", graceNote);
            setField(manager, "graceNoteIndex", 0);
            setField(manager, "graceLine", line);
            setField(manager, "graceLineComponent", graceLineComponent);

            var e = mouseEvent(otherLineComponent, MouseEvent.MOUSE_CLICKED, 0, 0, MouseEvent.BUTTON1);
            assertThat(manager.mouseClicked(otherLineComponent, e)).isTrue();

            // finish(cancel=true) → state INACTIVE
            assertThat(manager.isInProgress()).isFalse();
        }

        @Test
        void testCancelWhenClickLeftOfGraceNote() throws Exception {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            var line = detachedLine();
            line.addElement(graceNote);

            var lineComponent = mock(LineComponent.class);
            var layout = mock(LayoutResult.class);
            // Grace note at xSs=10.0 → cancelThreshold = round(10*8) - 4 = 76
            when(layout.getElementXSs(graceNote)).thenReturn(10.0);
            when(lineComponent.getLayoutResult()).thenReturn(layout);

            setField(manager, "state", GraceModeManager.State.GRACE_NOTE_INSERT);
            setField(manager, "graceNote", graceNote);
            setField(manager, "graceNoteIndex", 0);
            setField(manager, "graceLine", line);
            setField(manager, "graceLineComponent", lineComponent);

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
        private MockedStatic<MainFrame> mainFrameMock;

        @BeforeEach
        void setUp() {
            messageCenterMock = mockStatic(MessageCenter.class);
            editModeManagerMock = mockStatic(EditModeManager.class);
            calcMock = mockStatic(InsertionSpacingCalculator.class);
            mainFrameMock = mockStatic(MainFrame.class);
            var mockFrame = mock(MainFrame.class);
            var mockScore = mock(ScoreView.class);
            var mockRootPane = mock(JRootPane.class);
            when(mockRootPane.getInputMap(anyInt())).thenReturn(new InputMap());
            when(mockRootPane.getActionMap()).thenReturn(new ActionMap());
            mainFrameMock.when(MainFrame::getInstance).thenReturn(mockFrame);
            when(mockFrame.getRootPane()).thenReturn(mockRootPane);
            when(mockFrame.requireScoreView()).thenReturn(mockScore);
            when(mockFrame.getScoreView()).thenReturn(mockScore);
            when(mockScore.getSelectionCoordinator()).thenReturn(mock(SelectionCoordinator.class));
        }

        @AfterEach
        void tearDown() throws Exception {
            // Drain pending invokeLater tasks (e.g. Actions init) while mock is active,
            // preventing Actions from initializing with the real MainFrame.
            javax.swing.SwingUtilities.invokeAndWait(() -> {});
            mainFrameMock.close();
            calcMock.close();
            editModeManagerMock.close();
            messageCenterMock.close();
        }

        @Test
        void testFinishesWithCancelWhenHostNoteDoesNotFit() throws Exception {
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

            editModeManagerMock.when(EditModeManager::getPreviewElement).thenReturn(hostPreview);

            // calculateInsertion returns a result that doesn't fit
            var mockResult = mock(InsertionSpacingCalculator.InsertionResult.class);
            when(mockResult.fitsWithinLine(anyDouble())).thenReturn(false);
            calcMock.when(() -> InsertionSpacingCalculator.calculateInsertion(
                any(), any(), anyInt(), any()
            )).thenReturn(mockResult);

            // Set state and fields to trigger enterGraceNoteInsert via click (mouseReleased)
            var startPoint = new Point(100, 100);
            setField(manager, "state", GraceModeManager.State.GRACE_NOTE);
            setField(manager, "mouseDownPoint", startPoint);
            setField(manager, "mouseDownTime", System.currentTimeMillis());
            setField(manager, "graceNote", graceNote);
            setField(manager, "graceNoteIndex", 0);
            setField(manager, "graceLine", line);
            setField(manager, "graceLineComponent", lineComponent);

            // click event (x,y same as startPoint → no drag)
            var e = mouseEvent(lineComponent, MouseEvent.MOUSE_RELEASED, 100, 100, MouseEvent.BUTTON1);
            assertThat(manager.mouseReleased(lineComponent, e)).isTrue();

            // enterGraceNoteInsert → computeHostInsertion returns null → finish(cancel=true) → INACTIVE
            assertThat(manager.isInProgress()).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // finish(cancel=true) wraps in withModification — row 23
    // -------------------------------------------------------------------------

    @Nested
    class FinishCancel {

        private MockedStatic<MessageCenter> messageCenterMock;
        private MockedStatic<MainFrame> mainFrameMock;

        @BeforeEach
        void setUp() {
            messageCenterMock = mockStatic(MessageCenter.class);
            mainFrameMock = mockStatic(MainFrame.class);
            var mockFrame = mock(MainFrame.class);
            var mockScore = mock(ScoreView.class);
            var mockRootPane = mock(JRootPane.class);
            when(mockRootPane.getInputMap(anyInt())).thenReturn(new InputMap());
            when(mockRootPane.getActionMap()).thenReturn(new ActionMap());
            mainFrameMock.when(MainFrame::getInstance).thenReturn(mockFrame);
            when(mockFrame.getRootPane()).thenReturn(mockRootPane);
            when(mockFrame.requireScoreView()).thenReturn(mockScore);
            when(mockFrame.getScoreView()).thenReturn(mockScore);
            when(mockScore.getSelectionCoordinator()).thenReturn(mock(SelectionCoordinator.class));
        }

        @AfterEach
        void tearDown() throws Exception {
            // Drain pending invokeLater tasks (e.g. Actions init) while mock is active,
            // preventing Actions from initializing with the real MainFrame.
            javax.swing.SwingUtilities.invokeAndWait(() -> {});
            mainFrameMock.close();
            messageCenterMock.close();
        }

        @Test
        void testCancelRemovesGraceNoteViaModificationBracket() throws Exception {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            var line = detachedLine();
            line.addElement(graceNote);

            setField(manager, "state", GraceModeManager.State.GRACE_NOTE);
            setField(manager, "mouseDownPoint", null);  // triggers finish(cancel=true)
            setField(manager, "graceNote", graceNote);
            setField(manager, "graceNoteIndex", 0);
            setField(manager, "graceLine", line);
            setField(manager, "graceLineComponent", mock(LineComponent.class));

            var e = mouseEvent(mock(LineComponent.class), MouseEvent.MOUSE_RELEASED, 0, 0, MouseEvent.BUTTON1);
            manager.mouseReleased(mock(LineComponent.class), e);

            // Grace note must have been removed inside withModification
            assertThat(line.elementCount()).isEqualTo(0);
        }

        @Test
        void testCancelDoesNotRemoveWhenGraceNoteIsNull() throws Exception {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var line = detachedLine();
            var note = ElementType.CROTCHET.newInstance();
            line.addElement(note);

            setField(manager, "state", GraceModeManager.State.GRACE_NOTE);
            setField(manager, "mouseDownPoint", null);
            setField(manager, "graceNote", null);  // no grace note to remove
            setField(manager, "graceNoteIndex", -1);
            setField(manager, "graceLine", line);
            setField(manager, "graceLineComponent", mock(LineComponent.class));

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
        private MockedStatic<MainFrame> mainFrameMock;

        @BeforeEach
        void setUp() {
            messageCenterMock = mockStatic(MessageCenter.class);
            mainFrameMock = mockStatic(MainFrame.class);
            var mockFrame = mock(MainFrame.class);
            var mockScore = mock(ScoreView.class);
            var mockRootPane = mock(JRootPane.class);
            when(mockRootPane.getInputMap(anyInt())).thenReturn(new InputMap());
            when(mockRootPane.getActionMap()).thenReturn(new ActionMap());
            mainFrameMock.when(MainFrame::getInstance).thenReturn(mockFrame);
            when(mockFrame.getRootPane()).thenReturn(mockRootPane);
            when(mockFrame.requireScoreView()).thenReturn(mockScore);
            when(mockFrame.getScoreView()).thenReturn(mockScore);
            when(mockScore.getSelectionCoordinator()).thenReturn(mock(SelectionCoordinator.class));
        }

        @AfterEach
        void tearDown() throws Exception {
            // Drain pending invokeLater tasks (e.g. Actions init) while mock is active,
            // preventing Actions from initializing with the real MainFrame.
            javax.swing.SwingUtilities.invokeAndWait(() -> {});
            mainFrameMock.close();
            messageCenterMock.close();
        }

        @Test
        void testFinishResetsAllStateFieldsToDefaults() throws Exception {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            var line = detachedLine();
            line.addElement(graceNote);

            setField(manager, "state", GraceModeManager.State.GRACE_NOTE);
            setField(manager, "mouseDownPoint", null);  // triggers finish(cancel=true)
            setField(manager, "graceNote", graceNote);
            setField(manager, "graceNoteIndex", 0);
            setField(manager, "graceLine", line);
            setField(manager, "graceLineComponent", mock(LineComponent.class));
            setField(manager, "pendingCancel", true);
            setField(manager, "pendingConnect", true);
            setField(manager, "justEnteredInsert", true);

            var e = mouseEvent(mock(LineComponent.class), MouseEvent.MOUSE_RELEASED, 0, 0, MouseEvent.BUTTON1);
            manager.mouseReleased(mock(LineComponent.class), e);

            // finish() must reset all state
            assertThat(manager.isInProgress()).isFalse();
            assertThat((Boolean) getField(manager, "pendingCancel")).isFalse();
            assertThat((Boolean) getField(manager, "pendingConnect")).isFalse();
            assertThat((Boolean) getField(manager, "justEnteredInsert")).isFalse();
            assertThat(getField(manager, "graceNote")).isNull();
            assertThat(getField(manager, "graceLine")).isNull();
            assertThat(getField(manager, "graceLineComponent")).isNull();
            assertThat((Integer) getField(manager, "graceNoteIndex")).isEqualTo(-1);
        }

        @Test
        void testFinishPostsGraceModeStateDidChangeNotificationWithFalse() throws Exception {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            var line = detachedLine();
            line.addElement(graceNote);

            setField(manager, "state", GraceModeManager.State.GRACE_NOTE);
            setField(manager, "mouseDownPoint", null);
            setField(manager, "graceNote", graceNote);
            setField(manager, "graceNoteIndex", 0);
            setField(manager, "graceLine", line);
            setField(manager, "graceLineComponent", mock(LineComponent.class));

            var e = mouseEvent(mock(LineComponent.class), MouseEvent.MOUSE_RELEASED, 0, 0, MouseEvent.BUTTON1);
            manager.mouseReleased(mock(LineComponent.class), e);

            var captor = ArgumentCaptor.forClass(Message.class);
            messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
            var notification = captor.getValue();
            assertThat(notification).isInstanceOf(GraceModeStateDidChangeNotification.class);
            assertThat(((GraceModeStateDidChangeNotification) notification).isActive()).isFalse();
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
        void testPendingConnectIsFalseWhenNoNextElement() throws Exception {
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

            setField(manager, "state", GraceModeManager.State.GRACE_NOTE);
            setField(manager, "graceNote", graceNote);
            setField(manager, "graceNoteIndex", 0);
            setField(manager, "graceLine", line);
            setField(manager, "graceLineComponent", lineComponent);
            setField(manager, "mouseDownTime", System.currentTimeMillis() - GraceModeManager.MIN_DRAG_MILLIS);

            // Drag right past connectThreshold, but no eligible host
            var e = mouseEvent(lineComponent, MouseEvent.MOUSE_DRAGGED, 105, 0, MouseEvent.BUTTON1);
            manager.mouseDragged(lineComponent, e);

            assertThat((Boolean) getField(manager, "pendingConnect")).isFalse();
        }

        @Test
        void testPendingConnectIsFalseWhenNextElementIsNotPitchedNote() throws Exception {
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

            setField(manager, "state", GraceModeManager.State.GRACE_NOTE);
            setField(manager, "graceNote", graceNote);
            setField(manager, "graceNoteIndex", 0);
            setField(manager, "graceLine", line);
            setField(manager, "graceLineComponent", lineComponent);
            setField(manager, "mouseDownTime", System.currentTimeMillis() - GraceModeManager.MIN_DRAG_MILLIS);

            var e = mouseEvent(lineComponent, MouseEvent.MOUSE_DRAGGED, 105, 0, MouseEvent.BUTTON1);
            manager.mouseDragged(lineComponent, e);

            assertThat((Boolean) getField(manager, "pendingConnect")).isFalse();
        }

        @Test
        void testPendingConnectIsTrueWhenNextElementIsPitchedNote() throws Exception {
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

            setField(manager, "state", GraceModeManager.State.GRACE_NOTE);
            setField(manager, "graceNote", graceNote);
            setField(manager, "graceNoteIndex", 0);
            setField(manager, "graceLine", line);
            setField(manager, "graceLineComponent", lineComponent);
            setField(manager, "mouseDownTime", System.currentTimeMillis() - GraceModeManager.MIN_DRAG_MILLIS);

            var e = mouseEvent(lineComponent, MouseEvent.MOUSE_DRAGGED, 105, 0, MouseEvent.BUTTON1);
            manager.mouseDragged(lineComponent, e);

            assertThat((Boolean) getField(manager, "pendingConnect")).isTrue();
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
        private MockedStatic<MainFrame> mainFrameMock;

        @BeforeEach
        void setUp() {
            messageCenterMock = mockStatic(MessageCenter.class);
            previewMock = mockStatic(PreviewElementManager.class);
            editModeManagerMock = mockStatic(EditModeManager.class);
            mainFrameMock = mockStatic(MainFrame.class);
            var mockFrame = mock(MainFrame.class);
            var mockScore = mock(ScoreView.class);
            var mockRootPane = mock(JRootPane.class);
            when(mockRootPane.getInputMap(anyInt())).thenReturn(new InputMap());
            when(mockRootPane.getActionMap()).thenReturn(new ActionMap());
            mainFrameMock.when(MainFrame::getInstance).thenReturn(mockFrame);
            when(mockFrame.getRootPane()).thenReturn(mockRootPane);
            when(mockFrame.requireScoreView()).thenReturn(mockScore);
            when(mockFrame.getScoreView()).thenReturn(mockScore);
            when(mockScore.getSelectionCoordinator()).thenReturn(mock(SelectionCoordinator.class));
        }

        @AfterEach
        void tearDown() throws Exception {
            // Drain any pending invokeLater tasks (e.g., Actions.GRACE_EIGHTH_NOTE_ACTION
            // initialization triggered by finish()) while the MainFrame mock is still
            // active. Without this, Actions may initialize with the real MainFrame after
            // teardown, causing requireScoreView() to throw RuntimeError in later tests.
            javax.swing.SwingUtilities.invokeAndWait(() -> {});
            mainFrameMock.close();
            editModeManagerMock.close();
            previewMock.close();
            messageCenterMock.close();
        }

        @Test
        void testNotificationWithTruePostedOnEnterGraceNote() throws Exception {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            editModeManagerMock.when(EditModeManager::getPreviewElement)
                .thenReturn(ElementType.GRACE_QUAVER.newInstance());
            previewMock.when(PreviewElementManager::getCurrentXIndex).thenReturn(0);

            // Actions.QUARTER_NOTE_ACTION was constructed during Actions class-load with the
            // real MainFrame singleton, so its private mainFrame field bypasses the static
            // mock installed in setUp(). enterGraceNote() selects the crotchet duration via
            // DURATION_ACTION_GROUP.select(), whose perform() calls requireScoreView() — on the
            // real frame that resolves to a null scoreView and System.exit()s the test JVM.
            // Pin the action's frame to the mock for the duration of the call, then restore it
            // so the shared static action is not left pointing at a closed mock.
            var originalActionFrame = getField(Actions.QUARTER_NOTE_ACTION, "mainFrame");
            setField(Actions.QUARTER_NOTE_ACTION, "mainFrame", MainFrame.getInstance());

            try (var calcMock = mockStatic(InsertionSpacingCalculator.class)) {
                calcMock.when(
                    () -> InsertionSpacingCalculator.hasRoomForGraceNote(any(), anyInt(), any())
                ).thenReturn(true);

                var line = lineWith(ElementType.GRACE_QUAVER);
                var lineComponent = mock(LineComponent.class);
                when(lineComponent.getLine()).thenReturn(line);
                when(lineComponent.getLayoutResult()).thenReturn(null);
                var e = mouseEvent(lineComponent, MouseEvent.MOUSE_PRESSED, 50, 60, MouseEvent.BUTTON1);

                manager.mousePressed(lineComponent, e);

                // Capture ALL MessageCenter.post calls; enterGraceNote may trigger
                // secondary posts (e.g. DurationWasSelectedNotification from Actions init).
                // Filter for the GraceModeStateDidChangeNotification.
                var captor = ArgumentCaptor.forClass(Message.class);
                messageCenterMock.verify(() -> MessageCenter.post(captor.capture()),
                    org.mockito.Mockito.atLeastOnce());
                var graceModeNotification = captor.getAllValues().stream()
                    .filter(m -> m instanceof GraceModeStateDidChangeNotification)
                    .map(m -> (GraceModeStateDidChangeNotification) m)
                    .findFirst();
                assertThat(graceModeNotification).isPresent();
                assertThat(graceModeNotification.get().isActive()).isTrue();
            } finally {
                setField(Actions.QUARTER_NOTE_ACTION, "mainFrame", originalActionFrame);
            }
        }

        @Test
        void testNotificationWithFalsePostedOnFinish() throws Exception {
            var manager = new GraceModeManager(editModeManager, selectionCoordinator);
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            var line = detachedLine();
            line.addElement(graceNote);

            setField(manager, "state", GraceModeManager.State.GRACE_NOTE);
            setField(manager, "mouseDownPoint", null);
            setField(manager, "graceNote", graceNote);
            setField(manager, "graceNoteIndex", 0);
            setField(manager, "graceLine", line);
            setField(manager, "graceLineComponent", mock(LineComponent.class));

            var e = mouseEvent(mock(LineComponent.class), MouseEvent.MOUSE_RELEASED, 0, 0, MouseEvent.BUTTON1);
            manager.mouseReleased(mock(LineComponent.class), e);

            var captor = ArgumentCaptor.forClass(Message.class);
            messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
            assertThat(captor.getValue()).isInstanceOf(GraceModeStateDidChangeNotification.class);
            assertThat(((GraceModeStateDidChangeNotification) captor.getValue()).isActive()).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void resetStaticInstance(@Nullable GraceModeManager value) throws Exception {
        var field = GraceModeManager.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, value);
    }

    private static void setField(Object target, String name, @Nullable Object value) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        field.set(target, value);
    }

    /** Walks the class hierarchy to find a declared field by name. */
    private static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        Class<?> current = clazz;

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

    @Nullable
    private static Object getField(Object target, String name) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        return field.get(target);
    }
}
