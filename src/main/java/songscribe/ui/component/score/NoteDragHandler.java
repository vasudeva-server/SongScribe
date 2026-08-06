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
import java.util.LinkedHashSet;
import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.layout.AccidentalReconciliation;
import songscribe.ui.Mode;
import songscribe.ui.edit.AccidentalRestatements;
import songscribe.ui.edit.EditModeManager;
import songscribe.dom.ViewPx;
import songscribe.engraving.Staff;
import songscribe.hit.HitTarget;
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

    /**
     * The components of the lines other than this one that the drag group reaches, resolved
     * once at press. Non-empty only for a group formed across a cross-line tie.
     * <p>
     * Resolved at press rather than per drag event because the group is fixed for the whole
     * gesture, while {@code handleDrag} runs on every mouse movement that changes the pitch.
     * Each entry appears once however many of the group's notes sit on it, so one line is not
     * invalidated repeatedly.
     */
    private final List<LineComponent> foreignLineComponents = new ArrayList<>();

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
     * <p>
     * Takes the hit target the caller already resolved for this press rather than
     * hit-testing the point again. Reading the resolved target is also what keeps a lyric out
     * of this path: a lyric outranks an element, so a press on lyric text arrives here as a
     * lyric target and is declined, and text drawn over an element's rectangle cannot start a
     * pitch drag.
     */
    boolean handlePress(MouseEvent e, @Nullable HitTarget hitTarget) {
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

        if (!(hitTarget instanceof HitTarget.Element(var hitElement))) {
            return false;
        }

        var line = lc.getLine();

        if (line == null) {
            return false;
        }

        var hitIndex = line.getElementIndex(hitElement);

        if (hitIndex < 0) {
            return false;
        }

        var note = line.getElement(hitIndex);

        if (!note.getType().isNote()) {
            return false;
        }

        var coordinator = lc.getScoreView().getSelectionCoordinator();
        var range = coordinator.getRange();

        int dragBegin;
        int dragEnd;

        if (range != null && coordinator.isElementSelected(hitIndex, lc.getLineIndex())) {
            // Note is already part of the selection — preserve it for a potential drag.
            // Capture the existing selection range so the drag group spans all
            // originally-selected notes even after a collapse on release.
            dragBegin = range.begin();
            dragEnd = range.end();
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
        resolveForeignLineComponents();

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
        // The screen-Y delta is in view pixels; the view scale folds the zoom factor and the
        // fixed document scale into a single conversion, so a drag tracks on-screen motion 1:1.
        var deltaYSs = lc.getViewScale().toSs(new ViewPx(deltaYPx)).value();
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

        invalidateDraggedLines();
        dragMoved = true;
    }

    /**
     * Marks every line this drag moved a note on as needing a fresh layout.
     * <p>
     * A group formed across a cross-line tie moves the partner note in the adjacent line too,
     * and that line's tie geometry lives in its own cached {@code LayoutResult}. Repainting it
     * is not enough: the note head is redrawn at its new staff position while the tie is drawn
     * from the stale cached curve, leaving the tie pointing at where the note used to be. Only
     * the layout being recomputed puts the two back together.
     */
    private void invalidateDraggedLines() {
        lc.invalidateLayout();

        for (var lineComponent : foreignLineComponents) {
            lineComponent.invalidateLayout();
        }
    }

    /**
     * Resolves {@link #foreignLineComponents} for the group this press just built. Called once
     * per press; see that field for why it is not done per drag event.
     */
    private void resolveForeignLineComponents() {
        foreignLineComponents.clear();

        var ownLine = lc.getLine();
        var scoreView = lc.getScoreView();
        var seen = new LinkedHashSet<Line>();

        for (var entry : dragGroup) {
            var entryLine = entry.line();

            //noinspection ObjectEquality
            if (entryLine == ownLine || !seen.add(entryLine)) {
                continue;
            }

            var lineComponent = scoreView.getLineComponent(entryLine.getSong().indexOfLine(entryLine));

            if (lineComponent != null) {
                foreignLineComponents.add(lineComponent);
            }
        }
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
            // duration, then stop it. Shared with arrow-key shifting. Ahead of the revert below
            // because it reads the anchor's current pitch, which is the one that is sounding.
            PitchShifter.scheduleAnchorNoteOff(dragLine, dragElementIndex);

            // Asked before any modification bracket opens, like every other removal path. Unlike
            // them, this one has already mutated the line: the drag moved the notes live, so
            // Cancel has to put them back rather than merely decline to go forward.
            var decision = PitchShifter.confirmRestatements(lc, dragLine, dragGroup);

            if (decision.isCancelled()) {
                revertDrag();
            } else {
                // The pitch mutations were already applied during handleDrag, so
                // commitPitchShift records each PITCH/ACCIDENTAL ElementModification with the
                // press-time beforeClone, then the accidental changes, then the grace-note
                // cleanup — one bracket, one undo step.
                PitchShifter.commitPitchShift(
                    dragLine,
                    dragGroup,
                    reconcileVacatedPositions(
                        dragLine, lastPlayedStaffPositionSp - originalDragStaffPositionSp,
                        decision),
                    decision);
            }
        }

        dragActive = false;
        dragElementIndex = -1;
        dragLine = null;
        dragGroup.clear();
        foreignLineComponents.clear();

        PreviewElementManager.restorePreviewElement(lc);
    }

    /**
     * Puts every dragged note back the way it was at press time, for the one answer that abandons
     * the drag. Nothing was recorded — {@link PitchShifter#commitPitchShift} is what opens the
     * bracket, and it has not run — so this is a plain restore from each entry's press-time clone,
     * leaving no undo step behind and no modified flag set.
     *
     * <p>This is the only path on which a release does not commit.
     *
     * <p>Each entry is restored through its own line: a group formed across a cross-line tie
     * holds an entry whose index only means something in the adjacent line.
     */
    private void revertDrag() {
        for (var entry : dragGroup) {
            entry.line().getElement(entry.index()).copyStateFrom(entry.beforeClone());
        }

        invalidateDraggedLines();
        lc.repaint();
    }

    /**
     * The accidentals this drag must reconcile so no pitch the user did not touch changes: each
     * staff position the drag group vacates may have been lending its explicit accidental to a
     * later note sitting at that position, and a note that was given one by an earlier edit may no
     * longer need it.
     * <p>
     * The lifecycle differs from every other reconciliation call site. Everywhere else the
     * reconciliation runs once against a line that has not been mutated yet; a drag mutates the
     * line live on every mouse step, so by the time the move is finalized the "before" state is
     * gone from the line. It is not gone from {@code dragGroup}, though — each entry's
     * {@code beforeClone} was captured at press, before the first move. So the group is rolled
     * back onto the line, the reconciliation reads the pre-drag line it needs, and the final
     * state is put back. Running this per mouse step instead would be both wrong (the second step
     * would read the first step's result as its "before") and pointless work.
     * <p>
     * Scoped to the entries that sit on {@code line}, because the reconciliation reads and
     * indexes into that one line: a group formed across a cross-line tie also holds an entry
     * whose index is an index into the adjacent line, and means nothing here. The partner note
     * still moves and still gives up its own accidental — it is only the "which later notes were
     * leaning on the accidental this vacates" search that stops at the line boundary, exactly as
     * {@link PitchShifter#confirmRestatements} does.
     *
     * @param line       The dragged line, in its post-drag state
     * @param finalDelta The staff positions the group moved by, in total
     * @param decision   The restatements the user accepted for this drag, folded into the same
     *                   reconciliation so they clear on this line and suppress its materialization
     * @return The accidental changes to apply, keyed to live elements of {@code line}
     */
    private List<AccidentalReconciliation.AccidentalChange> reconcileVacatedPositions(
        Line line, int finalDelta, AccidentalRestatements.Decision decision) {

        var entriesOnLine = PitchShifter.entriesOnLine(dragGroup, line);
        var changes = PitchShifter.intendedChanges(dragGroup, line, finalDelta);
        var afterClones = new ArrayList<StaffElement>(entriesOnLine.size());

        for (var entry : entriesOnLine) {
            var note = line.getElement(entry.index());
            afterClones.add(note.clone());
            note.copyStateFrom(entry.beforeClone());
        }

        // The roll-forward is in a finally because the line is live between the two loops: leaving
        // it rolled back would silently strand the user's notes at their pre-drag pitches, with no
        // error and nothing to suggest the drag had not taken.
        try {
            return AccidentalReconciliation.reconcileModification(line, changes, decision.removal());
        } finally {
            for (var i = 0; i < entriesOnLine.size(); i++) {
                line.getElement(entriesOnLine.get(i).index()).copyStateFrom(afterClones.get(i));
            }
        }
    }
}
