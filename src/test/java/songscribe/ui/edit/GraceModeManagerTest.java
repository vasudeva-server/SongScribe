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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.*;
import java.awt.event.*;

import javax.swing.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.ui.Dialogs;
import songscribe.ui.Mode;
import songscribe.ui.action.UIAction;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.Score;
import songscribe.ui.component.score.InsertionElementManager;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.layout2.InsertionSpacingCalculator;
import songscribe.ui.layout2.LayoutResult;
import songscribe.ui.selection.SelectionCoordinator;

class GraceModeManagerTest extends UnitTest {

    // Static mocks opened for all tests and closed in tearDown()
    private MockedStatic<MainFrame> mainFrameMock;
    private MockedStatic<InsertionElementManager> insertionManagerMock;
    private MockedStatic<InsertionSpacingCalculator> spacingCalcMock;

    // GraceModeManager and its direct dependencies
    private EditModeManager mockEditModeManager;
    private SelectionCoordinator mockSelectionCoordinator;
    private GraceModeManager graceModeManager;

    @BeforeEach
    void setUp() {
        mainFrameMock = mockStatic(MainFrame.class);
        insertionManagerMock = mockStatic(InsertionElementManager.class);
        spacingCalcMock = mockStatic(InsertionSpacingCalculator.class);

        setupMainFrameMock();

        mockEditModeManager = mock(EditModeManager.class);
        mockSelectionCoordinator = mock(SelectionCoordinator.class);
        graceModeManager = new GraceModeManager(mockEditModeManager, mockSelectionCoordinator);

        // Room check passes by default — individual tests can override
        spacingCalcMock.when(
            () -> InsertionSpacingCalculator.hasRoomForGraceNotePair(any(), anyInt())
        ).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        spacingCalcMock.close();
        insertionManagerMock.close();
        mainFrameMock.close();
    }

    // -------------------------------------------------------------------------
    // IsActive
    // -------------------------------------------------------------------------

    @Nested
    class IsActive {

        @Test
        void testReturnsFalseInitially() {
            assertThat(graceModeManager.isInProgress()).isFalse();
        }

        @Test
        void testReturnsTrueAfterEnteringGraceNoteState() {
            var line = lineWithGraceAndCrotchet();
            var lineComponent = lineComponentFor(line);

            pressGraceMouse(lineComponent, 100, 100);

            assertThat(graceModeManager.isInProgress()).isTrue();
        }

        @Test
        void testIsActiveStaticMatchesInstance() {
            assertThat(GraceModeManager.isActive()).isFalse();

            var line = lineWithGraceAndCrotchet();
            var lineComponent = lineComponentFor(line);

            pressGraceMouse(lineComponent, 100, 100);

            assertThat(GraceModeManager.isActive()).isTrue();
        }
    }

    // -------------------------------------------------------------------------
    // mousePressed routing
    // -------------------------------------------------------------------------

    @Nested
    class MousePressedRouting {

        @Test
        void testIgnoresNonButton1Press() {
            var line = lineWithGraceAndCrotchet();
            var lineComponent = lineComponentFor(line);
            when(mockEditModeManager.getInsertionElement())
                .thenReturn(ElementType.GRACE_QUAVER.newInstance());

            var event = mouseEvent(lineComponent, MouseEvent.MOUSE_PRESSED, 100, 100, MouseEvent.BUTTON2);
            var consumed = graceModeManager.mousePressed(lineComponent, event);

            assertThat(consumed).isFalse();
            assertThat(graceModeManager.isInProgress()).isFalse();
        }

        @Test
        void testIgnoresNonGraceInsertionElement() {
            var line = new Line();
            var lineComponent = lineComponentFor(line);
            when(mockEditModeManager.getInsertionElement())
                .thenReturn(ElementType.CROTCHET.newInstance());

            var event = mouseEvent(lineComponent, MouseEvent.MOUSE_PRESSED, 100, 100, MouseEvent.BUTTON1);
            var consumed = graceModeManager.mousePressed(lineComponent, event);

            assertThat(consumed).isFalse();
            assertThat(graceModeManager.isInProgress()).isFalse();
        }

        @Test
        void testIgnoresNullInsertionElement() {
            var line = new Line();
            var lineComponent = lineComponentFor(line);
            when(mockEditModeManager.getInsertionElement()).thenReturn(null);

            var event = mouseEvent(lineComponent, MouseEvent.MOUSE_PRESSED, 100, 100, MouseEvent.BUTTON1);
            var consumed = graceModeManager.mousePressed(lineComponent, event);

            assertThat(consumed).isFalse();
        }

        @Test
        void testRoomCheckFailureShowsDialogAndRemainsInactive() {
            try (var dialogsMock = mockStatic(Dialogs.class)) {
                spacingCalcMock.when(
                    () -> InsertionSpacingCalculator.hasRoomForGraceNotePair(any(), anyInt())
                ).thenReturn(false);

                var line = new Line();
                var lineComponent = lineComponentFor(line);
                when(mockEditModeManager.getInsertionElement())
                    .thenReturn(ElementType.GRACE_QUAVER.newInstance());
                insertionManagerMock.when(InsertionElementManager::getCurrentXIndex).thenReturn(0);

                var event = mouseEvent(lineComponent, MouseEvent.MOUSE_PRESSED, 100, 100, MouseEvent.BUTTON1);
                var consumed = graceModeManager.mousePressed(lineComponent, event);

                assertThat(consumed).isTrue();
                assertThat(graceModeManager.isInProgress()).isFalse();
                dialogsMock.verify(
                    () -> Dialogs.showErrorMessage(any(), any(), any())
                );
            }
        }

        @Test
        void testSuccessfulPressEntersGraceNoteState() {
            var line = lineWithGraceAndCrotchet();
            var lineComponent = lineComponentFor(line);

            var consumed = pressGraceMouse(lineComponent, 100, 100);

            assertThat(consumed).isTrue();
            assertThat(graceModeManager.isInProgress()).isTrue();
        }
    }

    // -------------------------------------------------------------------------
    // GRACE_NOTE state transitions
    // -------------------------------------------------------------------------

    @Nested
    class GraceNoteStateTransitions {

        @Test
        void testClickTransitionsToGraceNoteInsertState() {
            var line = lineWithGraceAndCrotchet();
            var lineComponent = lineComponentFor(line);
            setupLayoutForInsert(lineComponent, line.getElement(0));

            pressGraceMouse(lineComponent, 100, 100);

            // Small displacement (< GRACE_SLOP_PX in both axes) → GRACE_NOTE_INSERT
            var releaseEvent = mouseEventWithScreenCoords(
                lineComponent, MouseEvent.MOUSE_RELEASED, 100, 100, 101, 100, MouseEvent.BUTTON1
            );
            graceModeManager.mouseReleased(lineComponent, releaseEvent);

            // Still active (in GRACE_NOTE_INSERT now)
            assertThat(graceModeManager.isInProgress()).isTrue();
        }

        @Test
        void testDragLeftCancels() {
            var line = lineWithGraceAndCrotchet();
            var lineComponent = lineComponentFor(line);

            pressGraceMouse(lineComponent, 100, 100);

            // Drag left >= GRACE_SLOP_PX → cancel
            var releaseEvent = mouseEventWithScreenCoords(
                lineComponent, MouseEvent.MOUSE_RELEASED, 95, 100, 95, 100, MouseEvent.BUTTON1
            );
            graceModeManager.mouseReleased(lineComponent, releaseEvent);

            assertThat(graceModeManager.isInProgress()).isFalse();
        }

        @Test
        void testDragRightWithNextPitchedNoteTransitionsToFinishedPair() {
            var line = lineWithGraceAndCrotchet();
            var lineComponent = lineComponentFor(line);

            pressGraceMouse(lineComponent, 100, 100);

            // Drag right >= GRACE_SLOP_PX, next element is a pitched note
            var releaseEvent = mouseEventWithScreenCoords(
                lineComponent, MouseEvent.MOUSE_RELEASED, 105, 100, 105, 100, MouseEvent.BUTTON1
            );
            graceModeManager.mouseReleased(lineComponent, releaseEvent);

            // GRACE_NOTE_PAIRED → FINISH(success) → INACTIVE
            assertThat(graceModeManager.isInProgress()).isFalse();
        }

        @Test
        void testDragRightWithNoNextNoteTransitionsToInsertState() {
            // Line has only the grace note — no next pitched note after it
            var line = new Line();
            line.addElement(ElementType.GRACE_QUAVER.newInstance());
            var lineComponent = lineComponentFor(line);
            setupLayoutForInsert(lineComponent, line.getElement(0));

            pressGraceMouse(lineComponent, 100, 100);

            // Drag right — no next note → GRACE_NOTE_INSERT
            var releaseEvent = mouseEventWithScreenCoords(
                lineComponent, MouseEvent.MOUSE_RELEASED, 105, 100, 105, 100, MouseEvent.BUTTON1
            );
            graceModeManager.mouseReleased(lineComponent, releaseEvent);

            // Still active (in GRACE_NOTE_INSERT)
            assertThat(graceModeManager.isInProgress()).isTrue();
        }

        @Test
        void testEscapeCancels() {
            var line = lineWithGraceAndCrotchet();
            var lineComponent = lineComponentFor(line);

            pressGraceMouse(lineComponent, 100, 100);

            var keyEvent = escapeKey(lineComponent);
            var consumed = graceModeManager.keyPressed(keyEvent);

            assertThat(consumed).isTrue();
            assertThat(graceModeManager.isInProgress()).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // GRACE_NOTE_INSERT state transitions
    // -------------------------------------------------------------------------

    @Nested
    class GraceNoteInsertStateTransitions {

        private LineComponent lineComponent;

        @BeforeEach
        void enterInsertState() {
            var line = lineWithGraceAndCrotchet();
            lineComponent = lineComponentFor(line);
            setupLayoutForInsert(lineComponent, line.getElement(0));

            pressGraceMouse(lineComponent, 100, 100);

            // Small displacement → GRACE_NOTE_INSERT
            var releaseEvent = mouseEventWithScreenCoords(
                lineComponent, MouseEvent.MOUSE_RELEASED, 100, 100, 102, 101, MouseEvent.BUTTON1
            );
            graceModeManager.mouseReleased(lineComponent, releaseEvent);
        }

        @Test
        void testIsActiveInInsertState() {
            assertThat(graceModeManager.isInProgress()).isTrue();
        }

        @Test
        void testEscapeCancels() {
            graceModeManager.keyPressed(escapeKey(lineComponent));

            assertThat(graceModeManager.isInProgress()).isFalse();
        }

        @Test
        void testFirstMouseClickAfterEntryIsSuppressed() {
            // The mouseClicked that fires as part of the same click cycle that
            // triggered the transition must be suppressed (justEnteredInsert flag).
            var clickEvent = mouseEvent(lineComponent, MouseEvent.MOUSE_CLICKED, 200, 100, MouseEvent.BUTTON1);
            var consumed = graceModeManager.mouseClicked(lineComponent, clickEvent);

            assertThat(consumed).isTrue();
            // Still active — the click was consumed but not acted upon
            assertThat(graceModeManager.isInProgress()).isTrue();
        }

        @Test
        void testClickOnDifferentLineCancels() {
            var otherLineComponent = mock(LineComponent.class);

            // First click clears the justEnteredInsert flag (same line)
            var firstClick = mouseEvent(lineComponent, MouseEvent.MOUSE_CLICKED, 200, 100, MouseEvent.BUTTON1);
            graceModeManager.mouseClicked(lineComponent, firstClick);

            // Second click on a different LineComponent → cancel
            var secondClick = mouseEvent(otherLineComponent, MouseEvent.MOUSE_CLICKED, 200, 100, MouseEvent.BUTTON1);
            graceModeManager.mouseClicked(otherLineComponent, secondClick);

            assertThat(graceModeManager.isInProgress()).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // Side effects
    // -------------------------------------------------------------------------

    @Nested
    class SideEffects {

        @Test
        void testActionStatesSavedOnEntry() {
            var line = lineWithGraceAndCrotchet();
            var lineComponent = lineComponentFor(line);

            pressGraceMouse(lineComponent, 100, 100);

            verify(mockSelectionCoordinator).saveActionStates();
        }

        @Test
        void testSelectionModeDisabledOnEntry() {
            var line = lineWithGraceAndCrotchet();
            var lineComponent = lineComponentFor(line);

            pressGraceMouse(lineComponent, 100, 100);

            verify(mockSelectionCoordinator).setInSelectMode(false);
        }

        @Test
        void testActionStatesRestoredOnCancel() {
            var line = lineWithGraceAndCrotchet();
            var lineComponent = lineComponentFor(line);

            pressGraceMouse(lineComponent, 100, 100);
            graceModeManager.keyPressed(escapeKey(lineComponent));

            verify(mockSelectionCoordinator).restoreActionStatesWithFlag(UIAction.Flag.DISABLE_IN_GRACE_MODE);
        }

        @Test
        void testActionStatesClearedOnSuccess() {
            // Grace note and crotchet must have different pitches for the success path
            var line = lineWithGraceAndCrotchetAtDifferentPitch();
            var lineComponent = lineComponentFor(line);
            setupLayoutForInsert(lineComponent, line.getElement(0));

            pressGraceMouse(lineComponent, 100, 100);

            // Small displacement → GRACE_NOTE_INSERT
            var releaseEvent = mouseEventWithScreenCoords(
                lineComponent, MouseEvent.MOUSE_RELEASED, 100, 100, 101, 100, MouseEvent.BUTTON1
            );
            graceModeManager.mouseReleased(lineComponent, releaseEvent);

            // First click consumed (justEnteredInsert flag)
            var firstClick = mouseEvent(lineComponent, MouseEvent.MOUSE_CLICKED, 200, 100, MouseEvent.BUTTON1);
            graceModeManager.mouseClicked(lineComponent, firstClick);

            // Second click triggers host note insertion → GRACE_NOTE_PAIRED → FINISH(success)
            var secondClick = mouseEvent(lineComponent, MouseEvent.MOUSE_CLICKED, 200, 100, MouseEvent.BUTTON1);
            graceModeManager.mouseClicked(lineComponent, secondClick);

            verify(mockSelectionCoordinator).restoreActionStatesWithFlag(UIAction.Flag.DISABLE_IN_GRACE_MODE);
        }

        @Test
        void testSelectionModeNotChangedOnFinish() {
            var line = lineWithGraceAndCrotchet();
            var lineComponent = lineComponentFor(line);

            pressGraceMouse(lineComponent, 100, 100);
            graceModeManager.keyPressed(escapeKey(lineComponent));

            // setInSelectMode(false) is called on entry, but NOT set back to true on finish
            verify(mockSelectionCoordinator).setInSelectMode(false);
            verify(mockSelectionCoordinator, never()).setInSelectMode(true);
        }
    }

    // -------------------------------------------------------------------------
    // Key event routing
    // -------------------------------------------------------------------------

    @Nested
    class KeyEventRouting {

        @Test
        void testEscapeConsumedWhenActive() {
            var line = lineWithGraceAndCrotchet();
            var lineComponent = lineComponentFor(line);
            pressGraceMouse(lineComponent, 100, 100);

            assertThat(graceModeManager.keyPressed(escapeKey(lineComponent))).isTrue();
        }

        @Test
        void testEscapePassesThroughWhenInactive() {
            var keyEvent = new KeyEvent(
                new Panel(), KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED
            );

            assertThat(graceModeManager.keyPressed(keyEvent)).isFalse();
        }

        @Test
        void testNonEscapeKeyPassesThroughWhenActive() {
            var line = lineWithGraceAndCrotchet();
            var lineComponent = lineComponentFor(line);
            pressGraceMouse(lineComponent, 100, 100);

            var keyEvent = new KeyEvent(
                lineComponent, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_4, '4'
            );

            assertThat(graceModeManager.keyPressed(keyEvent)).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // mouseClicked behavior
    // -------------------------------------------------------------------------

    @Nested
    class MouseClickedBehavior {

        @Test
        void testReturnsFalseWhenInactive() {
            var lineComponent = mock(LineComponent.class);
            var event = mouseEvent(lineComponent, MouseEvent.MOUSE_CLICKED, 100, 100, MouseEvent.BUTTON1);

            assertThat(graceModeManager.mouseClicked(lineComponent, event)).isFalse();
        }

        @Test
        void testReturnsTrueWhenActiveButNotInInsertState() {
            var line = lineWithGraceAndCrotchet();
            var lineComponent = lineComponentFor(line);
            pressGraceMouse(lineComponent, 100, 100);

            // In GRACE_NOTE state — mouseClicked must be consumed
            var event = mouseEvent(lineComponent, MouseEvent.MOUSE_CLICKED, 100, 100, MouseEvent.BUTTON1);

            assertThat(graceModeManager.mouseClicked(lineComponent, event)).isTrue();
        }
    }

    // -------------------------------------------------------------------------
    // mouseMoved behavior
    // -------------------------------------------------------------------------

    @Nested
    class MouseMovedBehavior {

        @Test
        void testReturnsFalseWhenInactive() {
            var lineComponent = mock(LineComponent.class);
            var event = mouseEvent(lineComponent, MouseEvent.MOUSE_MOVED, 100, 100, MouseEvent.NOBUTTON);

            assertThat(graceModeManager.mouseMoved(lineComponent, event)).isFalse();
        }

        @Test
        void testConsumedInGraceNoteStateAndHidesInsertion() {
            var line = lineWithGraceAndCrotchet();
            var lineComponent = lineComponentFor(line);
            pressGraceMouse(lineComponent, 100, 100);

            // enterGraceNote also calls hideInsertionElement — clear to isolate mouseMoved
            insertionManagerMock.clearInvocations();

            var event = mouseEvent(lineComponent, MouseEvent.MOUSE_MOVED, 150, 100, MouseEvent.NOBUTTON);
            var result = graceModeManager.mouseMoved(lineComponent, event);

            assertThat(result).isTrue();
            insertionManagerMock.verify(() -> InsertionElementManager.hideInsertionElement(false));
        }

        @Test
        void testReturnsFalseInInsertState() {
            var line = lineWithGraceAndCrotchet();
            var lineComponent = lineComponentFor(line);
            setupLayoutForInsert(lineComponent, line.getElement(0));

            pressGraceMouse(lineComponent, 100, 100);

            // Transition to GRACE_NOTE_INSERT
            var releaseEvent = mouseEventWithScreenCoords(
                lineComponent, MouseEvent.MOUSE_RELEASED, 100, 100, 101, 100, MouseEvent.BUTTON1
            );
            graceModeManager.mouseReleased(lineComponent, releaseEvent);

            // In INSERT state, mouseMoved passes through (returns false) so
            // InsertionElementManager.trackMouse() can apply the x-lock
            var movedEvent = mouseEvent(lineComponent, MouseEvent.MOUSE_MOVED, 150, 100, MouseEvent.NOBUTTON);

            assertThat(graceModeManager.mouseMoved(lineComponent, movedEvent)).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // X-lock computation
    // -------------------------------------------------------------------------

    @Nested
    class XLockComputation {

        @Test
        void testReturnsZeroWhenInactive() {
            assertThat(graceModeManager.getLockedInsertionXSs()).isEqualTo(0.0);
        }

        @Test
        void testReturnsZeroWhenLayoutIsNull() {
            var line = lineWithGraceAndCrotchet();
            var lineComponent = lineComponentFor(line);
            when(lineComponent.getLayoutResult()).thenReturn(null);

            pressGraceMouse(lineComponent, 100, 100);

            assertThat(graceModeManager.getLockedInsertionXSs()).isEqualTo(0.0);
        }

        @Test
        void testReturnsZeroWhenElementColumnIsNull() {
            var line = lineWithGraceAndCrotchet();
            var lineComponent = lineComponentFor(line);

            var mockLayout = mock(LayoutResult.class);
            when(lineComponent.getLayoutResult()).thenReturn(mockLayout);
            when(mockLayout.getElementColumn(any())).thenReturn(null);

            pressGraceMouse(lineComponent, 100, 100);

            assertThat(graceModeManager.getLockedInsertionXSs()).isEqualTo(0.0);
        }

        @Test
        void testComputesLockedXFromGraceColumnPosition() {
            var line = lineWithGraceAndCrotchet();
            var graceNote = line.getElement(0);
            var lineComponent = lineComponentFor(line);
            setupLayoutForInsert(lineComponent, graceNote);

            pressGraceMouse(lineComponent, 100, 100);

            // graceColumn.getXSs() = 10.0, getRightExtentSs() = 2.0, GRACE_NOTE_GAP_SS = 2.0
            // Expected: 10.0 + 2.0 + 2.0 = 14.0 (no accidental, so no host left extent)
            assertThat(graceModeManager.getLockedInsertionXSs()).isEqualTo(14.0);
        }
    }

    // -------------------------------------------------------------------------
    // Normal edit mode unaffected
    // -------------------------------------------------------------------------

    @Nested
    class NormalEditModeUnaffected {

        @Test
        void testMouseEventsPassThroughWhenInactive() {
            var lineComponent = mock(LineComponent.class);

            var pressEvent = mouseEvent(lineComponent, MouseEvent.MOUSE_PRESSED, 100, 100, MouseEvent.BUTTON1);
            var releaseEvent = mouseEvent(lineComponent, MouseEvent.MOUSE_RELEASED, 100, 100, MouseEvent.BUTTON1);
            var movedEvent = mouseEvent(lineComponent, MouseEvent.MOUSE_MOVED, 100, 100, MouseEvent.NOBUTTON);
            var clickEvent = mouseEvent(lineComponent, MouseEvent.MOUSE_CLICKED, 100, 100, MouseEvent.BUTTON1);

            // Non-grace element set — mousePressed does not trigger entry
            when(mockEditModeManager.getInsertionElement())
                .thenReturn(ElementType.CROTCHET.newInstance());

            assertThat(graceModeManager.mousePressed(lineComponent, pressEvent)).isFalse();
            assertThat(graceModeManager.mouseReleased(lineComponent, releaseEvent)).isFalse();
            assertThat(graceModeManager.mouseMoved(lineComponent, movedEvent)).isFalse();
            assertThat(graceModeManager.mouseClicked(lineComponent, clickEvent)).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Mocks MainFrame.getInstance() to return a minimal stub that satisfies
     * the call chain in UIAction.applyToSelectionIfActive() → no active selection.
     */
    private void setupMainFrameMock() {
        var mockFrame = mock(MainFrame.class);
        var mockScore = mock(Score.class);
        var mockCoordinator = mock(SelectionCoordinator.class);

        mainFrameMock.when(MainFrame::getInstance).thenReturn(mockFrame);
        when(mockFrame.getScore()).thenReturn(mockScore);
        // Needed by UIUtils.registerActionKeystroke() when Actions class initializes
        var mockRootPane = mock(JRootPane.class);
        when(mockRootPane.getInputMap(anyInt())).thenReturn(new InputMap());
        when(mockRootPane.getActionMap()).thenReturn(new ActionMap());
        when(mockFrame.getRootPane()).thenReturn(mockRootPane);
        when(mockScore.getSelectionCoordinator()).thenReturn(mockCoordinator);
        when(mockScore.getMode()).thenReturn(Mode.EDIT);
        when(mockScore.getSelectionSize()).thenReturn(0);
        when(mockCoordinator.getSelection()).thenReturn(null);
        when(mockCoordinator.hasActiveSelection()).thenReturn(false);
    }

    /**
     * Creates a Line pre-populated with a grace note at index 0 and a crotchet
     * at index 1. The grace note was "already inserted" — since handleClick is
     * mocked (no-op), we pre-populate the line to simulate a successful insertion.
     */
    private Line lineWithGraceAndCrotchet() {
        var line = new Line();
        line.addElement(ElementType.GRACE_QUAVER.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());

        return line;
    }

    /**
     * Creates a Line with a grace note and a crotchet at different staff positions.
     * Needed for tests that exercise the success path (same pitch causes cancellation).
     */
    private Line lineWithGraceAndCrotchetAtDifferentPitch() {
        var line = new Line();
        line.addElement(ElementType.GRACE_QUAVER.newInstance());

        var crotchet = ElementType.CROTCHET.newInstance();
        crotchet.setStaffPosition(2);
        line.addElement(crotchet);

        return line;
    }

    /**
     * Creates a mock LineComponent backed by the given Line.
     */
    private LineComponent lineComponentFor(Line line) {
        var mockLineComponent = mock(LineComponent.class);
        when(mockLineComponent.getLine()).thenReturn(line);
        return mockLineComponent;
    }

    /**
     * Sets up the mocked layout so that getLockedInsertionXSs() returns a
     * non-zero value, enabling the GRACE_NOTE → GRACE_NOTE_INSERT transition.
     *
     * <p>Stubs:
     * <ul>
     *   <li>lineComponent.getLayoutResult() → mockLayout</li>
     *   <li>mockLayout.getElementColumn(graceNote) → column with xSs=10.0, rightExtent=2.0</li>
     * </ul>
     * Expected locked x: 10.0 + 2.0 + 2.0 (GRACE_NOTE_GAP_SS) = 14.0
     */
    private void setupLayoutForInsert(LineComponent lineComponent, StaffElement graceNote) {
        var mockLayout = mock(LayoutResult.class);
        var mockColumn = mock(songscribe.ui.layout2.ElementColumn.class);

        when(lineComponent.getLayoutResult()).thenReturn(mockLayout);
        when(mockLayout.getElementColumn(graceNote)).thenReturn(mockColumn);
        when(mockColumn.getXSs()).thenReturn(10.0);
        when(mockColumn.getRightExtentSs()).thenReturn(2.0);
    }

    /**
     * Stubs the insertion element to be a GRACE_QUAVER, stubs getCurrentXIndex()
     * to return 0, then calls mousePressed on the graceModeManager. Returns whether
     * the event was consumed.
     */
    private boolean pressGraceMouse(LineComponent lineComponent, int screenX, int screenY) {
        when(mockEditModeManager.getInsertionElement())
            .thenReturn(ElementType.GRACE_QUAVER.newInstance());
        insertionManagerMock.when(InsertionElementManager::getCurrentXIndex).thenReturn(0);

        var event = mouseEventWithScreenCoords(
            lineComponent, MouseEvent.MOUSE_PRESSED,
            screenX, screenY, screenX, screenY,
            MouseEvent.BUTTON1
        );
        return graceModeManager.mousePressed(lineComponent, event);
    }

    /**
     * Creates a MouseEvent using component-relative coordinates.
     * Uses the absolute-coordinate constructor (xAbs = x, yAbs = y) to
     * avoid a NullPointerException from screen-location lookup on mock components.
     */
    private MouseEvent mouseEvent(
        java.awt.Component source, int id, int x, int y, int button
    ) {
        return new MouseEvent(source, id, 0L, 0, x, y, x, y, 1, false, button);
    }

    /**
     * Creates a MouseEvent with explicit screen (absolute) coordinates.
     * Used when the code under test reads e.getXOnScreen() / e.getYOnScreen().
     */
    private MouseEvent mouseEventWithScreenCoords(
        java.awt.Component source, int id,
        int x, int y, int xAbs, int yAbs,
        int button
    ) {
        return new MouseEvent(source, id, 0L, 0, x, y, xAbs, yAbs, 1, false, button);
    }

    /**
     * Creates a VK_ESCAPE KeyEvent sourced from the given component.
     */
    private KeyEvent escapeKey(java.awt.Component source) {
        return new KeyEvent(source, KeyEvent.KEY_PRESSED, 0L, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED);
    }
}
