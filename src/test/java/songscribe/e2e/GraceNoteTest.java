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

package songscribe.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.swing.core.MouseButton.LEFT_BUTTON;

import java.awt.*;
import java.awt.event.*;

import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.finder.JOptionPaneFinder;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.music.Composition;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.ui.action.Actions;
import songscribe.ui.action.UIAction;
import songscribe.ui.component.MainFrame;
import songscribe.ui.edit.GraceModeManager;
import songscribe.util.Utils;

/**
 * E2E tests for grace note pairing state machine: click-click flow, drag-right flow,
 * cancel paths, toolbar state, key equivalents, and edge cases.
 */
class GraceNoteTest extends E2ETest {

    @Test
    void testDragLeftCancelsAndRemovesGraceNote() {
        buildEmptyLine();
        selectDuration(Actions.GRACE_EIGHTH_NOTE_ACTION);

        var insertPt = insertionPoint(0, 0);

        // Drag left: mouseDown, wait for drag threshold, move left past cancel threshold, mouseUp
        robot.pressMouse(insertPt, LEFT_BUTTON);
        Utils.sleep(GraceModeManager.MIN_DRAG_MILLIS);
        int cancelScreenX = GuiActionRunner.execute(() -> {
            var lc = score().getLineComponent(0);
            return lc.getLocationOnScreen().x + GraceModeManager.getCancelThresholdPx();
        });
        robot.moveMouse(new Point(cancelScreenX, insertPt.y));
        pause();
        robot.releaseMouseButtons();
        pause();
        performLayout(0);

        // Grace note should be removed
        var line = composition().getLine(0);
        assertThat(line.elementCount()).isEqualTo(0);

        assertGraceModeInactive();
    }

    @Test
    void testEscapeDuringGraceNoteCancels() {
        buildEmptyLine();
        selectDuration(Actions.GRACE_EIGHTH_NOTE_ACTION);

        // Click to insert grace note (enters GRACE_NOTE_INSERT)
        clickAt(insertionPoint(0, 0));
        performLayout(0);

        // Grace note should be present
        var line = composition().getLine(0);
        assertThat(line.elementCount()).isEqualTo(1);

        // Press Escape to cancel
        robot.pressAndReleaseKey(KeyEvent.VK_ESCAPE);
        pause();
        performLayout(0);

        // Grace note should be removed
        assertThat(line.elementCount()).isEqualTo(0);
        assertGraceModeInactive();
    }

    @Test
    void testClickClickInsertsGraceNoteAndHostWithGlissando() {
        buildEmptyLine();
        selectDuration(Actions.GRACE_EIGHTH_NOTE_ACTION);

        // Click to insert the grace note (enters GRACE_NOTE, then GRACE_NOTE_INSERT on release)
        clickAt(insertionPoint(0, 0));
        performLayout(0);

        // The grace note should be inserted
        var line = composition().getLine(0);
        assertThat(line.elementCount()).isEqualTo(1);
        assertThat(line.getElement(0).getType()).isEqualTo(ElementType.GRACE_QUAVER);

        // Click again to insert the host note
        clickAt(insertionPoint(0, -2));
        performLayout(0);

        // Now there should be two notes: grace + host
        assertThat(line.elementCount()).isEqualTo(2);
        assertThat(line.getElement(0).getType()).isEqualTo(ElementType.GRACE_QUAVER);
        assertThat(line.getElement(1).getType().isPitchedNote()).isTrue();

        // Grace note should have a CONNECTED glissando to the host
        var graceNote = line.getElement(0);
        //noinspection ObjectEquality
        assertThat(graceNote.getGlissando() != StaffElement.NO_GLISSANDO).isTrue();
        assertThat(graceNote.getGlissando().type).isEqualTo(StaffElement.Glissando.Type.CONNECTED);

        // Grace mode should be inactive after completion
        assertGraceModeInactive();
    }

    @Test
    void testDragRightConnectsToExistingNote() {
        buildLineWithOneNote();
        selectDuration(Actions.GRACE_EIGHTH_NOTE_ACTION);

        // The insertion point is before the existing note, at a different pitch
        var insertPt = insertionPointBefore(0, 0, -2);

        // Drag right: mouseDown, wait for drag threshold, move right past connect threshold, mouseUp
        robot.pressMouse(insertPt, LEFT_BUTTON);
        Utils.sleep(GraceModeManager.MIN_DRAG_MILLIS);
        int connectScreenX = GuiActionRunner.execute(() -> {
            var lc = score().getLineComponent(0);
            return lc.getLocationOnScreen().x + GraceModeManager.getConnectThresholdPx();
        });
        robot.moveMouse(new Point(connectScreenX, insertPt.y));
        pause();
        robot.releaseMouseButtons();
        pause();
        performLayout(0);

        var line = composition().getLine(0);

        // Grace note inserted before the existing note
        assertThat(line.elementCount()).isEqualTo(2);
        assertThat(line.getElement(0).getType()).isEqualTo(ElementType.GRACE_QUAVER);

        // Grace note should have CONNECTED glissando
        var graceNote = line.getElement(0);
        //noinspection ObjectEquality
        assertThat(graceNote.getGlissando() != StaffElement.NO_GLISSANDO).isTrue();
        assertThat(graceNote.getGlissando().type).isEqualTo(StaffElement.Glissando.Type.CONNECTED);

        assertGraceModeInactive();
    }

    @Nested
    class EdgeCases {

        @Test
        void testNoRoomShowsAlertAndNoInsertion() {
            buildFullLine();
            selectDuration(Actions.GRACE_EIGHTH_NOTE_ACTION);

            // Click 1px inside the right edge of the line — room check should fail
            // because there's no room for both a grace note and a host note.
            var pt = GuiActionRunner.execute(() -> {
                var lc = score().getLineComponent(0);
                var loc = lc.getLocationOnScreen();
                var yPx = lc.staffPositionToYPx(0);
                return new Point(loc.x + lc.getWidth() - 1, loc.y + yPx);
            });
            clickAt(pt);

            // Wait for the error dialog to appear and dismiss it
            // TODO: verify dialog message type once Dialogs.showErrorMessage is testable in E2E
            var optionPane = JOptionPaneFinder.findOptionPane().using(robot);
            optionPane.okButton().click();

            // No grace note should be inserted
            var line = composition().getLine(0);
            var originalCount = getFullLineNoteCount();
            assertThat(line.elementCount()).isEqualTo(originalCount);

            assertGraceModeInactive();
        }

        @Test
        void testSamePitchShowsAlertAndCancels() {
            buildEmptyLine();
            selectDuration(Actions.GRACE_EIGHTH_NOTE_ACTION);

            // Click to insert grace note at staff position 0
            clickAt(insertionPoint(0, 0));
            performLayout(0);

            // Click to insert host note at the same pitch (position 0)
            clickAt(insertionPoint(0, 0));

            // Wait for the same-pitch warning dialog to appear and dismiss it
            var optionPane = JOptionPaneFinder.findOptionPane().using(robot);
            optionPane.requireErrorMessage();
            optionPane.okButton().click();
            performLayout(0);

            // Grace note should be removed (cancel path)
            var line = composition().getLine(0);
            assertThat(line.elementCount()).isEqualTo(0);

            assertGraceModeInactive();
        }
    }

    @Test
    void testKeyChangeDurationDuringFlow() {
        buildEmptyLine();
        selectDuration(Actions.GRACE_EIGHTH_NOTE_ACTION);

        // Click to insert grace note (enters GRACE_NOTE_INSERT)
        clickAt(insertionPoint(0, 0));
        performLayout(0);

        // Press key to change to minim (half note)
        selectDuration(Actions.HALF_NOTE_ACTION);

        // Click to insert host note
        clickAt(insertionPoint(0, -2));
        performLayout(0);

        // Host note should be a minim
        var line = composition().getLine(0);
        assertThat(line.elementCount()).isEqualTo(2);
        assertThat(line.getElement(1).getType()).isEqualTo(ElementType.MINIM);

        // Grace note should still be GRACE_QUAVER
        assertThat(line.getElement(0).getType()).isEqualTo(ElementType.GRACE_QUAVER);

        // Glissando should be connected
        assertThat(line.getElement(0).getGlissando().type)
            .isEqualTo(StaffElement.Glissando.Type.CONNECTED);
    }

    @Nested
    class ToolbarState {

        @Test
        void testNonApplicableActionsDisabledDuringFlow() {
            buildEmptyLine();

            // Verify actions are enabled before grace mode
            assertActionEnabled(Actions.GLISSANDO_ACTION, true);
            assertActionEnabled(Actions.REST_ACTION, true);

            selectDuration(Actions.GRACE_EIGHTH_NOTE_ACTION);

            // Click to insert grace note — enters grace mode
            clickAt(insertionPoint(0, 0));
            pause();

            // Non-applicable actions should be disabled during grace mode
            assertActionEnabled(Actions.GLISSANDO_ACTION, false);
            assertActionEnabled(Actions.REST_ACTION, false);
            assertActionEnabled(Actions.GRACE_EIGHTH_NOTE_ACTION, false);

            // Duration actions should remain enabled
            assertActionEnabled(Actions.QUARTER_NOTE_ACTION, true);
            assertActionEnabled(Actions.HALF_NOTE_ACTION, true);

            // Cancel to exit grace mode
            robot.pressAndReleaseKey(KeyEvent.VK_ESCAPE);
            pause();
            performLayout(0);

            // Actions should be re-enabled after grace mode exits
            assertActionEnabled(Actions.GLISSANDO_ACTION, true);
            assertActionEnabled(Actions.REST_ACTION, true);
        }

        @Test
        void testToolbarReflectsHostNoteAfterPairing() {
            buildEmptyLine();
            selectDuration(Actions.GRACE_EIGHTH_NOTE_ACTION);

            // Click to insert grace note
            clickAt(insertionPoint(0, 0));
            performLayout(0);

            // Switch to half note for the host
            selectDuration(Actions.HALF_NOTE_ACTION);

            // Click to insert host note
            clickAt(insertionPoint(0, -2));
            performLayout(0);

            // After pairing, the toolbar should reflect the host note (half note)
            assertActionSelected(Actions.HALF_NOTE_ACTION, true);
            assertActionSelected(Actions.QUARTER_NOTE_ACTION, false);
        }
    }


    // -- Assertion helpers --

    private void assertGraceModeInactive() {
        var isActive = GuiActionRunner.execute(() -> GraceModeManager.isActive());
        assertThat(isActive).as("Grace mode should be inactive").isFalse();
    }

    private void assertActionEnabled(UIAction action, boolean expected) {
        var isEnabled = GuiActionRunner.execute(() -> action.isEnabled());
        assertThat(isEnabled)
            .as("Action '%s' enabled state", action.getActionCommand())
            .isEqualTo(expected);
    }

    private void assertActionSelected(UIAction action, boolean expected) {
        var selectable = (UIAction.Selectable) action;
        var isSelected = GuiActionRunner.execute(() -> selectable.isSelected());
        assertThat(isSelected)
            .as("Action '%s' selected state", action.getActionCommand())
            .isEqualTo(expected);
    }


    // -- Composition builders --

    private void buildEmptyLine() {
        GuiActionRunner.execute(() -> {
            var composition = new Composition(MainFrame.getInstance());
            composition.addLine(0, new Line());
            score().setComposition(composition);
        });

        performLayout(0);
    }

    private void buildLineWithOneNote() {
        buildEmptyLine();
        selectDuration(Actions.QUARTER_NOTE_ACTION);
        clickAt(insertionPoint(0, 0));
        performLayout(0);
    }

    private int fullLineNoteCount;

    private void buildFullLine() {
        buildEmptyLine();
        selectDuration(Actions.QUARTER_NOTE_ACTION);

        // Click to insert notes until the insertion point goes past the
        // line component's right edge.
        while (true) {
            var pt = insertionPoint(0, 0);

            var rightEdge = GuiActionRunner.execute(() -> {
                var lc = score().getLineComponent(0);
                var loc = lc.getLocationOnScreen();
                return loc.x + lc.getWidth();
            });

            if (pt.x >= rightEdge) {
                fullLineNoteCount = composition().getLine(0).elementCount();
                break;
            }

            clickAt(pt);
            performLayout(0);
        }
    }

    private int getFullLineNoteCount() {
        return fullLineNoteCount;
    }
}
