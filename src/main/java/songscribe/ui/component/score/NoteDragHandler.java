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

package songscribe.ui.component.score;

import module java.desktop;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.layout.AccidentalReconciliation;
import songscribe.ui.Mode;
import songscribe.ui.edit.EditModeManager;
import songscribe.dom.ScaleContext;
import songscribe.engraving.Staff;
import songscribe.ui.playback.MidiController;

/**
 * Handles press/drag/release for pitch-dragging a note head in SELECT mode.
 * <p>
 * One instance per {@link LineComponent}. The owning component delegates its
 * mouse events here before passing them on to other handlers.
 */
class NoteDragHandler {

    private final LineComponent lc;

    private boolean dragActive = false;
    private boolean dragMoved = false;
    private boolean pressPreservedMultiSelection = false;
    private int dragElementIndex = -1;

    /** Original staff position of the directly dragged note — used to compute deltaSp. */
    private int originalDragStaffPositionSp;

    /**
     * Screen-absolute mouse Y (pixels) at drag start. The drag tracks mouse movement as a
     * delta from this anchor in screen coordinates, so an in-drag layout reflow — which can
     * shift both the staff within the line component and the line component within its
     * parent — cannot perturb the mouse→staff-position mapping.
     */
    private int dragPressScreenYPx;

    private int lastPlayedStaffPositionSp;
    @Nullable
    private Line dragLine;

    /** All notes (plus tie-chain expansions) that move together during drag. */
    private final List<PitchShifter.PitchShiftEntry> dragGroup = new ArrayList<>();

    NoteDragHandler(LineComponent lc) {
        this.lc = lc;
    }

    // ======================================================================
    // Accessors
    // ======================================================================

    boolean isDragActive() {
        return dragActive;
    }

    int getDragElementIndex() {
        return dragElementIndex;
    }

    // ======================================================================
    // Mouse event handlers
    // ======================================================================

    /**
     * Handles a mouse press. Returns {@code true} if a pitch-drag was initiated
     * and the event should not be processed further.
     */
    boolean handlePress(MouseEvent e) {
        var scoreView = lc.getScoreView();

        if (scoreView.getMode() != Mode.SELECT) {
            return false;
        }

        if (MidiController.isPlaying()) {
            return false;
        }

        // Shift+click extends selection — let the selection handler manage it
        if (e.isShiftDown()) {
            return false;
        }

        dragMoved = false;

        var hitIndex = ElementHitTest.hitTestElement(lc, lc.getViewScale().toDocumentPoint(e.getPoint()));

        if (hitIndex == -1) {
            return false;
        }

        var line = lc.getLine();

        if (line == null) {
            return false;
        }

        var note = line.getElement(hitIndex);

        if (!note.getType().isNote()) {
            return false;
        }

        var selectionState = lc.getLineSelectionState();

        int dragBegin;
        int dragEnd;

        if (selectionState != null && selectionState.isElementSelected(hitIndex)) {
            // Note is already part of the selection — preserve it for a potential drag.
            // Capture the existing selection range so the drag group spans all
            // originally-selected notes even after a collapse on release.
            dragBegin = selectionState.getSelectionBegin();
            dragEnd = selectionState.getSelectionEnd();
            lc.getSelectionHandler().playNoteIfPitched(hitIndex);
            pressPreservedMultiSelection = true;
        } else {
            // Note is not in the current selection — replace selection with just this note.
            // Drag group is only the clicked note.
            dragBegin = hitIndex;
            dragEnd = hitIndex;
            lc.getSelectionHandler().selectAndPlayElement(hitIndex);
            pressPreservedMultiSelection = false;
        }

        // Build the drag group from the captured selection range (which may be multi-note).
        // The press gate above admits any note (isNote), and buildPitchShiftGroup includes
        // every note in the range, so the group is never empty for an admitted hit.
        //
        // Each entry's beforeClone is also the accidental reconciliation's input: it is the only
        // record of the pre-drag line once handleDrag starts mutating it. See
        // reconcileVacatedPositions.
        dragGroup.clear();
        dragGroup.addAll(PitchShifter.buildPitchShiftGroup(line, dragBegin, dragEnd));

        // Save state for possible revert on a press+release without drag
        dragElementIndex = hitIndex;
        originalDragStaffPositionSp = note.getStaffPosition();
        lastPlayedStaffPositionSp = originalDragStaffPositionSp;
        dragLine = line;
        dragPressScreenYPx = e.getYOnScreen();

        PreviewElementManager.clearPreviewElement();
        EditModeManager.setPreviewElementVisible(false);

        dragActive = true;
        lc.repaint();
        return true;
    }

    /**
     * Handles mouse drag while a pitch-drag is active.
     */
    void handleDrag(MouseEvent e) {
        // Track mouse movement in screen-absolute pixels. Using a screen-Y delta instead of
        // a component-local coordinate decouples the drag from any layout reflow — neither
        // a change to aboveStaffSs (which shifts the staff within the line component) nor
        // a reposition of the line component within its parent can perturb the mapping.
        var deltaYPx = e.getYOnScreen() - dragPressScreenYPx;
        // The screen-Y delta is in view pixels; divide by the zoom factor to reach document
        // pixels before the fixed-scale ss conversion, so a drag tracks on-screen motion 1:1.
        var deltaYSs = ScaleContext.pxToSs(deltaYPx / lc.getViewScale().factor());
        var deltaSp = Staff.ssToSp(deltaYSs);
        var newPositionSp = originalDragStaffPositionSp + deltaSp;

        if (newPositionSp == lastPlayedStaffPositionSp) {
            return;
        }

        if (dragLine == null) {
            return;
        }

        // Clamp delta so no note in the group exits the valid staff range
        var clampedDelta = PitchShifter.clampDelta(dragGroup, deltaSp);

        // If the clamped delta lands on the last-played position, skip so a stationary
        // mouse does not retrigger the note.
        if (originalDragStaffPositionSp + clampedDelta == lastPlayedStaffPositionSp) {
            return;
        }

        // Shared with arrow-key shifting: move the whole group and play the dragged note.
        PitchShifter.moveGroupAndPlayAnchor(dragLine, dragGroup, dragElementIndex, clampedDelta);

        lastPlayedStaffPositionSp = originalDragStaffPositionSp + clampedDelta;

        lc.invalidateLayout();
        dragMoved = true;
    }

    /**
     * Handles mouse release, finalizing or reverting the pitch change.
     */
    void handleRelease() {
        if (dragLine == null) {
            return;
        }

        if (!dragMoved && pressPreservedMultiSelection) {
            // Click without drag on a preserved selection — collapse to the clicked note.
            // Use selectElementAtIndex (not selectAndPlayElement) so we don't re-play the note
            // that was already played on press. Explicit repaint ensures the visual updates
            // immediately — selectionChanged() posts a message but does not trigger a LineComponent repaint.
            lc.getSelectionHandler().selectElementAtIndex(dragElementIndex);
            lc.repaint();
        }

        if (dragMoved) {
            // The last drag noteOn is still sounding — let it ring for the standard
            // duration, then stop it. Shared with arrow-key shifting.
            PitchShifter.scheduleAnchorNoteOff(dragLine, dragElementIndex);

            // The pitch mutations were already applied during handleDrag, so
            // commitPitchShift records each PITCH/ACCIDENTAL ElementModification with the
            // press-time beforeClone, then the materializations, then the grace-note cleanup —
            // one bracket, one undo step.
            PitchShifter.commitPitchShift(
                dragLine,
                dragGroup,
                reconcileVacatedPositions(dragLine, lastPlayedStaffPositionSp - originalDragStaffPositionSp));
        }

        dragActive = false;
        dragElementIndex = -1;
        dragLine = null;
        dragGroup.clear();

        PreviewElementManager.restorePreviewElement(lc);
    }

    /**
     * The accidentals this drag must make explicit so no pitch the user did not touch changes:
     * each staff position the drag group vacates may have been lending its explicit accidental to
     * a later note sitting at that position.
     * <p>
     * The lifecycle differs from every other reconciliation call site. Everywhere else the
     * reconciliation runs once against a line that has not been mutated yet; a drag mutates the
     * line live on every mouse step, so by the time the move is finalized the "before" state is
     * gone from the line. It is not gone from {@code dragGroup}, though — each entry's
     * {@code beforeClone} was captured at press, before the first move. So the group is rolled
     * back onto the line, the reconciliation reads the pre-drag line it needs, and the final
     * state is put back. Running this per mouse step instead would be both wrong (the second step
     * would read the first step's result as its "before") and pointless work.
     *
     * @param line       The dragged line, in its post-drag state
     * @param finalDelta The staff positions the group moved by, in total
     * @return The materializations to apply, keyed to live elements of {@code line}
     */
    private List<AccidentalReconciliation.Materialization> reconcileVacatedPositions(Line line, int finalDelta) {
        var changes = PitchShifter.intendedChanges(dragGroup, finalDelta);
        var afterClones = new ArrayList<StaffElement>(dragGroup.size());

        for (var entry : dragGroup) {
            var note = line.getElement(entry.index());
            afterClones.add(note.clone());
            note.copyStateFrom(entry.beforeClone());
        }

        var materializations = AccidentalReconciliation.reconcileModification(line, changes);

        for (var i = 0; i < dragGroup.size(); i++) {
            line.getElement(dragGroup.get(i).index()).copyStateFrom(afterClones.get(i));
        }

        return materializations;
    }
}
