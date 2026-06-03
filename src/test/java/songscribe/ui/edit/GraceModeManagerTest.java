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
import org.mockito.MockedStatic;

import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JRootPane;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.ScaleContext;
import songscribe.layout.ElementColumn;
import songscribe.layout.HorizontalSpacingCalculator;
import songscribe.layout.InsertionSpacingCalculator;
import songscribe.layout.LayoutResult;
import songscribe.message.MessageCenter;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.component.score.PreviewElementManager;
import songscribe.ui.selection.SelectionCoordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
}
